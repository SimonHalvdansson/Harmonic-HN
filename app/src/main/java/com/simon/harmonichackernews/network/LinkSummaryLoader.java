package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.data.WikipediaInfo;
import com.simon.harmonichackernews.linkpreview.WikipediaGetter;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class LinkSummaryLoader {
    private static final int MAX_DESCRIPTION_CHARS = 600;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final String YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed";
    private static final String REDDIT_OEMBED_ENDPOINT = "https://www.reddit.com/oembed";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Pattern YOUTUBE_VIDEO_URL_PATTERN = Pattern.compile(
            "^https?://(?:(?:www|m|music)\\.)?(?:youtube\\.com|youtube-nocookie\\.com)/"
                    + "(?:watch\\?(?:[^#]*&)?v=|embed/|v/|shorts/|live/)"
                    + "([A-Za-z0-9_-]{11})(?:[?&#/].*)?$"
                    + "|^https?://(?:www\\.)?youtu\\.be/([A-Za-z0-9_-]{11})(?:[?&#/].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final String[] IMAGE_SELECTORS = new String[]{
            "meta[property=og:image:secure_url]",
            "meta[property=og:image:url]",
            "meta[property=og:image]",
            "meta[name=twitter:image:src]",
            "meta[name=twitter:image]",
            "meta[itemprop=image]",
            "link[rel=image_src]"
    };

    public interface Callback {
        void onSuccess(@NonNull Result result);

        void onFailure(@NonNull String message);
    }

    public interface SummaryRequest {
        void cancel();
    }

    public static final class Result {
        public final String title;
        public final String siteName;
        public final String author;
        public final String publishedTime;
        public final String language;
        public final String contentType;
        public final String description;
        public final String imageUrl;
        public final String finalUrl;

        Result(String title,
               String siteName,
               String author,
               String publishedTime,
               String language,
               String contentType,
               String description,
               String imageUrl,
               String finalUrl) {
            this.title = title;
            this.siteName = siteName;
            this.author = author;
            this.publishedTime = publishedTime;
            this.language = language;
            this.contentType = contentType;
            this.description = description;
            this.imageUrl = imageUrl;
            this.finalUrl = finalUrl;
        }
    }

    private LinkSummaryLoader() {
    }

    public static SummaryRequest load(
            @Nullable Context context,
            String pageUrl,
            @Nullable String fallbackTitle,
            Callback callback) {
        HttpUrl parsedUrl = HttpUrl.parse(pageUrl);
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            postFailure(callback, "This link does not use HTTP or HTTPS");
            return () -> { };
        }

        String normalizedPageUrl = parsedUrl.toString();
        boolean wikipediaSummaryRequest = WikipediaGetter.isValidWikipediaUrl(normalizedPageUrl);
        String youtubeOEmbedUrl = buildYoutubeOEmbedUrl(normalizedPageUrl);
        boolean youtubeOEmbedRequest = !TextUtils.isEmpty(youtubeOEmbedUrl);
        String redditOEmbedUrl = buildRedditOEmbedUrl(normalizedPageUrl);
        boolean redditOEmbedRequest = !TextUtils.isEmpty(redditOEmbedUrl);
        boolean oEmbedRequest = youtubeOEmbedRequest || redditOEmbedRequest;
        Result cached = StoryPreviewImageLoader.getCachedLinkSummary(context, normalizedPageUrl);
        if (cached != null
                && (!oEmbedRequest || "application/json".equals(cached.contentType))
                && (!wikipediaSummaryRequest || "application/json".equals(cached.contentType))) {
            MAIN_HANDLER.post(() -> callback.onSuccess(cached));
            return () -> { };
        }

        if (wikipediaSummaryRequest) {
            return loadWikipediaSummary(
                    context,
                    normalizedPageUrl,
                    fallbackTitle,
                    callback);
        }

        Request request = new Request.Builder()
                .url(youtubeOEmbedRequest
                        ? youtubeOEmbedUrl
                        : redditOEmbedRequest ? redditOEmbedUrl : normalizedPageUrl)
                .header("Accept", oEmbedRequest
                        ? "application/json"
                        : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build();
        Call call = NetworkComponent.getOkHttpClientInstance().newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) {
                    postFailure(callback, getFailureMessage(e));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful()) {
                        postFailure(callback, "The page returned HTTP " + closeableResponse.code());
                        return;
                    }
                    if (closeableResponse.body() == null) {
                        postFailure(callback, "The page did not return any content");
                        return;
                    }

                    String contentType = normalizeContentType(closeableResponse.header("Content-Type", ""));
                    if (!oEmbedRequest
                            && !TextUtils.isEmpty(contentType)
                            && !contentType.toLowerCase(Locale.US).contains("html")
                            && !contentType.toLowerCase(Locale.US).contains("xml")) {
                        postFailure(callback, "This link contains " + contentType + ", not a web page");
                        return;
                    }

                    if (oEmbedRequest) {
                        Result result = extractOEmbedSummary(
                                readBoundedBody(closeableResponse.body()),
                                normalizedPageUrl,
                                youtubeOEmbedRequest ? "YouTube" : "Reddit");
                        if (result == null) {
                            postFailure(callback, (youtubeOEmbedRequest ? "YouTube" : "Reddit")
                                    + " did not return link information");
                            return;
                        }
                        StoryPreviewImageLoader.saveCachedLinkSummary(
                                context,
                                normalizedPageUrl,
                                result);
                        MAIN_HANDLER.post(() -> callback.onSuccess(result));
                        return;
                    }

                    String finalUrl = closeableResponse.request().url().toString();
                    Result result = extract(
                            readBoundedBody(closeableResponse.body()),
                            fallbackTitle,
                            contentType,
                            finalUrl);
                    StoryPreviewImageLoader.saveCachedLinkSummary(context, parsedUrl.toString(), result);
                    MAIN_HANDLER.post(() -> callback.onSuccess(result));
                } catch (Exception e) {
                    if (!call.isCanceled()) {
                        postFailure(callback, getFailureMessage(e));
                    }
                }
            }
        });
        return call::cancel;
    }

    private static SummaryRequest loadWikipediaSummary(
            @Nullable Context context,
            String pageUrl,
            @Nullable String fallbackTitle,
            Callback callback) {
        if (context == null) {
            postFailure(callback, "Wikipedia summary unavailable");
            return () -> { };
        }

        com.android.volley.Request<?> request = WikipediaGetter.getInfo(
                pageUrl,
                context,
                new WikipediaGetter.GetterCallback() {
                    @Override
                    public void onSuccess(WikipediaInfo wikipediaInfo) {
                        String firstParagraph = WikipediaGetter.getFirstParagraphText(
                                wikipediaInfo.summary);
                        if (TextUtils.isEmpty(firstParagraph)) {
                            callback.onFailure("Wikipedia did not return a summary");
                            return;
                        }

                        Result result = new Result(
                                clean(fallbackTitle),
                                "Wikipedia",
                                "",
                                "",
                                "en",
                                "application/json",
                                firstParagraph,
                                "",
                                pageUrl);
                        StoryPreviewImageLoader.saveCachedLinkSummary(context, pageUrl, result);
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onFailure(String reason) {
                        callback.onFailure(reason);
                    }
                });
        return request == null ? () -> { } : request::cancel;
    }

    @Nullable
    static String buildYoutubeOEmbedUrl(String pageUrl) {
        if (!isYoutubeVideoUrl(pageUrl)) {
            return null;
        }

        HttpUrl endpoint = HttpUrl.parse(YOUTUBE_OEMBED_ENDPOINT);
        if (endpoint == null) {
            return null;
        }

        return endpoint.newBuilder()
                .addQueryParameter("url", pageUrl)
                .addQueryParameter("format", "json")
                .build()
                .toString();
    }

    static boolean isYoutubeVideoUrl(String url) {
        return !TextUtils.isEmpty(url) && YOUTUBE_VIDEO_URL_PATTERN.matcher(url).matches();
    }

    @Nullable
    static String buildRedditOEmbedUrl(String pageUrl) {
        if (!isRedditPostUrl(pageUrl)) {
            return null;
        }

        HttpUrl endpoint = HttpUrl.parse(REDDIT_OEMBED_ENDPOINT);
        if (endpoint == null) {
            return null;
        }

        return endpoint.newBuilder()
                .addQueryParameter("url", pageUrl)
                .addQueryParameter("format", "json")
                .build()
                .toString();
    }

    static boolean isRedditPostUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }

        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            return false;
        }

        String host = parsedUrl.host().toLowerCase(Locale.US);
        if ("redd.it".equals(host) || "www.redd.it".equals(host)) {
            return parsedUrl.pathSize() > 0 && !TextUtils.isEmpty(parsedUrl.pathSegments().get(0));
        }
        if (!("reddit.com".equals(host) || host.endsWith(".reddit.com"))) {
            return false;
        }

        List<String> segments = parsedUrl.pathSegments();
        for (int i = 0; i < segments.size(); i++) {
            if ("comments".equalsIgnoreCase(segments.get(i)) && i + 1 < segments.size()
                    && !TextUtils.isEmpty(segments.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static Result extractYoutubeOEmbedSummary(String json, String pageUrl) {
        return extractOEmbedSummary(json, pageUrl, "YouTube");
    }

    @Nullable
    private static Result extractOEmbedSummary(
            String json,
            String pageUrl,
            String fallbackProviderName) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(json);
            String imageUrl = normalizeHttpUrl(jsonObject.optString("thumbnail_url", null));
            return new Result(
                    jsonObject.optString("title", ""),
                    jsonObject.optString("provider_name", fallbackProviderName),
                    jsonObject.optString("author_name", ""),
                    "",
                    "",
                    "application/json",
                    "",
                    imageUrl == null ? "" : imageUrl,
                    pageUrl);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBoundedBody(ResponseBody body) throws IOException {
        long contentLength = body.contentLength();
        if (contentLength > MAX_RESPONSE_BYTES) {
            throw new IOException("The page is too large to preview");
        }

        int initialCapacity = contentLength > 0
                ? (int) Math.min(contentLength, MAX_RESPONSE_BYTES)
                : 8192;
        try (InputStream input = body.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity)) {
            byte[] buffer = new byte[8192];
            int totalBytes = 0;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_RESPONSE_BYTES) {
                    throw new IOException("The page is too large to preview");
                }
                output.write(buffer, 0, bytesRead);
            }

            Charset charset = StandardCharsets.UTF_8;
            MediaType mediaType = body.contentType();
            if (mediaType != null) {
                charset = mediaType.charset(StandardCharsets.UTF_8);
            }
            return new String(output.toByteArray(), charset);
        }
    }

    public static Result extract(String html,
                                 @Nullable String fallbackTitle,
                                 String contentType,
                                 String finalUrl) {
        Document document = Jsoup.parse(html, finalUrl);
        String title = firstNonEmpty(
                metaContent(document, "meta[property=og:title]"),
                metaContent(document, "meta[name=twitter:title]"),
                document.title(),
                fallbackTitle);
        String siteName = firstNonEmpty(
                metaContent(document, "meta[property=og:site_name]"),
                getHost(finalUrl));
        String author = firstNonEmpty(
                metaContent(document, "meta[name=author]"),
                metaContent(document, "meta[property=article:author]"),
                metaContent(document, "meta[name=byl]"),
                elementText(document.selectFirst("[rel=author]")));
        String publishedTime = firstNonEmpty(
                metaContent(document, "meta[property=article:published_time]"),
                metaContent(document, "meta[itemprop=datePublished]"),
                metaContent(document, "meta[name=date]"),
                elementAttribute(document.selectFirst("time[datetime]"), "datetime"));
        String language = firstNonEmpty(
                elementAttribute(document.selectFirst("html[lang]"), "lang"),
                metaContent(document, "meta[http-equiv=content-language]"));
        String metadataDescription = firstNonEmpty(
                metaContent(document, "meta[property=og:description]"),
                metaContent(document, "meta[name=description]"),
                metaContent(document, "meta[name=twitter:description]"));
        String description = truncate(HtmlDescriptionExtractor.chooseDescription(
                metadataDescription,
                document,
                title,
                fallbackTitle), MAX_DESCRIPTION_CHARS);
        String imageUrl = extractImageUrl(document, finalUrl);

        return new Result(
                clean(title),
                clean(siteName),
                clean(author),
                clean(publishedTime),
                clean(language),
                clean(contentType),
                clean(description),
                imageUrl,
                finalUrl);
    }

    private static String extractImageUrl(Document document, String baseUrl) {
        for (String selector : IMAGE_SELECTORS) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String attribute = "link".equals(element.tagName()) ? "href" : "content";
            String candidate = element.attr(attribute);
            if (TextUtils.isEmpty(candidate) || candidate.trim().startsWith("data:")) {
                continue;
            }
            HttpUrl parsedBase = HttpUrl.parse(baseUrl);
            HttpUrl parsedImage = parsedBase == null
                    ? HttpUrl.parse(candidate.trim())
                    : parsedBase.resolve(candidate.trim());
            if (parsedImage != null && isHttpScheme(parsedImage)) {
                return parsedImage.toString();
            }
        }
        return "";
    }

    private static String metaContent(Document document, String selector) {
        return elementAttribute(document.selectFirst(selector), "content");
    }

    private static String elementAttribute(@Nullable Element element, String attribute) {
        return element == null ? "" : element.attr(attribute);
    }

    private static String elementText(@Nullable Element element) {
        return element == null ? "" : element.text();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(clean(value))) {
                return value;
            }
        }
        return "";
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int maxChars) {
        String cleaned = clean(value);
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        int lastSpace = cleaned.lastIndexOf(' ', maxChars - 1);
        int end = lastSpace >= maxChars * 0.75f ? lastSpace : maxChars;
        return cleaned.substring(0, end).trim() + "…";
    }

    private static String normalizeContentType(String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim();
    }

    private static String getHost(String url) {
        HttpUrl parsedUrl = HttpUrl.parse(url);
        return parsedUrl == null ? "" : parsedUrl.host();
    }

    @Nullable
    private static String normalizeHttpUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        HttpUrl parsedUrl = HttpUrl.parse(url);
        return parsedUrl == null || !isHttpScheme(parsedUrl) ? null : parsedUrl.toString();
    }

    private static boolean isHttpScheme(HttpUrl url) {
        return "http".equals(url.scheme()) || "https".equals(url.scheme());
    }

    private static String getFailureMessage(Exception error) {
        return TextUtils.isEmpty(error.getMessage()) ? "The page could not be read" : error.getMessage();
    }

    private static void postFailure(Callback callback, String message) {
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }
}
