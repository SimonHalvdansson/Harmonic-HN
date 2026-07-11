package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class StoryPreviewImageLoader {

    public interface PreviewImageCallback {
        void onPreviewImageUrlLoaded(String imageUrl);
    }

    public interface PreviewImageRequest {
        void cancel();

        boolean isCancelled();
    }

    private static final int MAX_CACHE_SIZE = 300;
    private static final int MAX_DISK_CACHE_SIZE = 1000;
    private static final String PREVIEW_IMAGE_CACHE_PREFERENCES =
            "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES";
    private static final String KEY_PREVIEW_IMAGE_CACHE_ORDER =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_CACHE_ORDER";
    private static final String KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER";
    private static final String KEY_LINK_SUMMARY_CACHE_ORDER =
            "com.simon.harmonichackernews.KEY_LINK_SUMMARY_CACHE_ORDER";
    private static final String KEY_PREVIEW_IMAGE_URL =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL";
    private static final String KEY_PREVIEW_IMAGE_URL_LOADED =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL_LOADED";
    private static final String KEY_PREVIEW_IMAGE_TINT_COLOR =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_TINT_COLOR";
    private static final String KEY_LINK_SUMMARY =
            "com.simon.harmonichackernews.KEY_LINK_SUMMARY";
    private static final String YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed";
    private static final String YOUTUBE_OEMBED_CACHE_SUFFIX = "youtube_oembed";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<String, String> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, LinkSummaryLoader.Result> LINK_SUMMARY_CACHE = new HashMap<>();
    private static final Set<String> MISS_CACHE = new HashSet<>();
    private static final Map<String, PendingPreviewImageBatch> PENDING_CALLBACKS = new HashMap<>();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final Pattern YOUTUBE_VIDEO_URL_PATTERN = Pattern.compile(
            "^https?://(?:(?:www|m|music)\\.)?(?:youtube\\.com|youtube-nocookie\\.com)/"
                    + "(?:watch\\?(?:[^#]*&)?v=|embed/|v/|shorts/|live/)"
                    + "([A-Za-z0-9_-]{11})(?:[?&#/].*)?$"
                    + "|^https?://(?:www\\.)?youtu\\.be/([A-Za-z0-9_-]{11})(?:[?&#/].*)?$",
            Pattern.CASE_INSENSITIVE);

    private static class PendingPreviewImageBatch {
        final List<PendingPreviewImageRequest> requests = new ArrayList<>();
        Call call;
    }

    private static class PendingPreviewImageRequest implements PreviewImageRequest {
        final Context context;
        final int storyId;
        final PreviewImageCallback callback;
        private boolean cancelled;
        private String pageUrl;
        private PendingPreviewImageBatch batch;

        PendingPreviewImageRequest(Context context, int storyId, PreviewImageCallback callback) {
            this.context = context;
            this.storyId = storyId;
            this.callback = callback;
        }

        void attach(String pageUrl, PendingPreviewImageBatch batch) {
            this.pageUrl = pageUrl;
            this.batch = batch;
        }

        void detach(PendingPreviewImageBatch detachedBatch) {
            if (batch == detachedBatch) {
                pageUrl = null;
                batch = null;
            }
        }

        @Override
        public void cancel() {
            synchronized (StoryPreviewImageLoader.class) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                if (pageUrl == null || batch == null) {
                    return;
                }

                batch.requests.remove(this);
                if (batch.requests.isEmpty() && PENDING_CALLBACKS.get(pageUrl) == batch) {
                    PENDING_CALLBACKS.remove(pageUrl);
                    if (batch.call != null) {
                        batch.call.cancel();
                    }
                }
                pageUrl = null;
                batch = null;
            }
        }

        @Override
        public boolean isCancelled() {
            synchronized (StoryPreviewImageLoader.class) {
                return cancelled;
            }
        }
    }

    public static PreviewImageRequest loadPreviewImageUrl(String pageUrl, PreviewImageCallback callback) {
        return loadPreviewImageUrl(null, 0, pageUrl, callback);
    }

    public static PreviewImageRequest loadPreviewImageUrl(Context context, int storyId, String pageUrl, PreviewImageCallback callback) {
        return loadPreviewImageUrl(context, storyId, pageUrl, false, callback);
    }

    public static PreviewImageRequest loadPreviewImageUrl(Context context, int storyId, String pageUrl, boolean forceRefresh, PreviewImageCallback callback) {
        Context appContext = context == null ? null : context.getApplicationContext();
        PendingPreviewImageRequest previewImageRequest = new PendingPreviewImageRequest(appContext, storyId, callback);
        String normalizedPageUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            postResult(previewImageRequest, null);
            return previewImageRequest;
        }

        String previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl);
        if (!forceRefresh) {
            CachedPreviewImageUrl cachedDiskImageUrl = loadCachedPreviewImageUrl(
                    appContext,
                    previewImageCacheEntryId,
                    true);
            if (cachedDiskImageUrl.loaded) {
                postResult(previewImageRequest, cachedDiskImageUrl.imageUrl);
                return previewImageRequest;
            }
        }

        if (isLikelyImageUrl(normalizedPageUrl)) {
            saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, normalizedPageUrl);
            postResult(previewImageRequest, normalizedPageUrl);
            return previewImageRequest;
        }

        String youtubeOEmbedUrl = buildYoutubeOEmbedUrl(normalizedPageUrl);
        boolean youtubeOEmbedRequest = !TextUtils.isEmpty(youtubeOEmbedUrl);
        String requestUrl = youtubeOEmbedRequest ? youtubeOEmbedUrl : normalizedPageUrl;
        String acceptHeader = youtubeOEmbedRequest
                ? "application/json"
                : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        PendingPreviewImageBatch pendingBatch;
        synchronized (StoryPreviewImageLoader.class) {
            if (!forceRefresh) {
                String cachedImageUrl = IMAGE_CACHE.get(normalizedPageUrl);
                if (!TextUtils.isEmpty(cachedImageUrl)) {
                    saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, cachedImageUrl);
                    postResult(previewImageRequest, cachedImageUrl);
                    return previewImageRequest;
                }

                if (MISS_CACHE.contains(normalizedPageUrl)) {
                    postResult(previewImageRequest, null);
                    return previewImageRequest;
                }
            }

            pendingBatch = PENDING_CALLBACKS.get(normalizedPageUrl);
            if (pendingBatch != null) {
                previewImageRequest.attach(normalizedPageUrl, pendingBatch);
                pendingBatch.requests.add(previewImageRequest);
                return previewImageRequest;
            }

            pendingBatch = new PendingPreviewImageBatch();
            previewImageRequest.attach(normalizedPageUrl, pendingBatch);
            pendingBatch.requests.add(previewImageRequest);
            PENDING_CALLBACKS.put(normalizedPageUrl, pendingBatch);
        }

        Request request = new Request.Builder()
                .url(requestUrl)
                .header("Accept", acceptHeader)
                .get()
                .build();

        Call call = NetworkComponent.getOkHttpClientInstance().newCall(request);
        final PendingPreviewImageBatch requestBatch = pendingBatch;
        synchronized (StoryPreviewImageLoader.class) {
            if (PENDING_CALLBACKS.get(normalizedPageUrl) == requestBatch) {
                requestBatch.call = call;
            } else {
                call.cancel();
            }
        }

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                finish(normalizedPageUrl, requestBatch, null, null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        finish(normalizedPageUrl, requestBatch, null, null);
                        return;
                    }

                    String contentType = closeableResponse.header("Content-Type", "");
                    if (!youtubeOEmbedRequest
                            && !TextUtils.isEmpty(contentType)
                            && !contentType.toLowerCase(Locale.US).contains("html")) {
                        finish(normalizedPageUrl, requestBatch, null, null);
                        return;
                    }

                    String responseBody = closeableResponse.body().string();
                    if (youtubeOEmbedRequest) {
                        LinkSummaryLoader.Result summary = extractYoutubeOEmbedSummary(
                                responseBody,
                                normalizedPageUrl);
                        finish(
                                normalizedPageUrl,
                                requestBatch,
                                summary == null ? null : summary.imageUrl,
                                summary);
                        return;
                    }

                    String baseUrl = closeableResponse.request().url().toString();
                    LinkSummaryLoader.Result summary = LinkSummaryLoader.extract(
                            responseBody,
                            null,
                            normalizeContentType(contentType),
                            baseUrl);
                    finish(normalizedPageUrl, requestBatch, summary.imageUrl, summary);
                } catch (Exception e) {
                    finish(normalizedPageUrl, requestBatch, null, null);
                }
            }
        });
        return previewImageRequest;
    }

    public static String getCachedPreviewImageUrl(Context context, int storyId, String pageUrl) {
        Context appContext = context == null ? null : context.getApplicationContext();
        String normalizedPageUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            return null;
        }

        String previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl);
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).imageUrl;
    }

    public static boolean isCachedPreviewImageUrlLoaded(Context context, int storyId, String pageUrl) {
        Context appContext = context == null ? null : context.getApplicationContext();
        String normalizedPageUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            return false;
        }

        String previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl);
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).loaded;
    }

    private static LinkSummaryLoader.Result extractYoutubeOEmbedSummary(String json, String pageUrl) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(json);
            String imageUrl = normalizeHttpUrl(jsonObject.optString("thumbnail_url", null));
            return new LinkSummaryLoader.Result(
                    jsonObject.optString("title", ""),
                    jsonObject.optString("provider_name", "YouTube"),
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

    private static String normalizeContentType(String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim();
    }

    private static String normalizeHttpUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            return null;
        }
        return parsedUrl.toString();
    }

    private static boolean isHttpScheme(HttpUrl url) {
        return "http".equals(url.scheme()) || "https".equals(url.scheme());
    }

    private static String buildYoutubeOEmbedUrl(String pageUrl) {
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

    private static boolean isYoutubeVideoUrl(String url) {
        return !TextUtils.isEmpty(url) && YOUTUBE_VIDEO_URL_PATTERN.matcher(url).matches();
    }

    private static boolean isLikelyImageUrl(String url) {
        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null) {
            return false;
        }

        String path = parsedUrl.encodedPath().toLowerCase(Locale.US);
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".gif")
                || path.endsWith(".webp")
                || path.endsWith(".avif");
    }

    private static void finish(
            String pageUrl,
            PendingPreviewImageBatch batch,
            String imageUrl,
            LinkSummaryLoader.Result summary) {
        List<PendingPreviewImageRequest> pendingRequests;
        synchronized (StoryPreviewImageLoader.class) {
            if (PENDING_CALLBACKS.get(pageUrl) != batch) {
                return;
            }
            PENDING_CALLBACKS.remove(pageUrl);
            pendingRequests = new ArrayList<>(batch.requests);
            for (PendingPreviewImageRequest pendingRequest : pendingRequests) {
                pendingRequest.detach(batch);
            }
            if (TextUtils.isEmpty(imageUrl)) {
                MISS_CACHE.add(pageUrl);
            } else {
                if (IMAGE_CACHE.size() >= MAX_CACHE_SIZE) {
                    IMAGE_CACHE.clear();
                    MISS_CACHE.clear();
                }
                IMAGE_CACHE.put(pageUrl, imageUrl);
            }
        }

        if (pendingRequests == null) {
            return;
        }

        for (PendingPreviewImageRequest pendingRequest : pendingRequests) {
            if (!pendingRequest.isCancelled()) {
                saveCachedPreviewImageUrl(
                        pendingRequest.context,
                        getPreviewImageCacheEntryId(pendingRequest.storyId, pageUrl),
                        imageUrl);
                if (summary != null) {
                    saveCachedLinkSummary(pendingRequest.context, pageUrl, summary);
                }
            }
        }

        MAIN_HANDLER.post(() -> {
            for (PendingPreviewImageRequest pendingRequest : pendingRequests) {
                if (!pendingRequest.isCancelled()) {
                    pendingRequest.callback.onPreviewImageUrlLoaded(imageUrl);
                }
            }
        });
    }

    private static void postResult(PendingPreviewImageRequest request, String imageUrl) {
        MAIN_HANDLER.post(() -> {
            if (!request.isCancelled()) {
                request.callback.onPreviewImageUrlLoaded(imageUrl);
            }
        });
    }

    public static void clearDiskCache(Context context) {
        if (context == null) {
            return;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (KEY_PREVIEW_IMAGE_CACHE_ORDER.equals(key)
                        || KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER.equals(key)
                        || KEY_LINK_SUMMARY_CACHE_ORDER.equals(key)
                        || key.startsWith(KEY_PREVIEW_IMAGE_URL)
                        || key.startsWith(KEY_PREVIEW_IMAGE_URL_LOADED)
                        || key.startsWith(KEY_PREVIEW_IMAGE_TINT_COLOR)
                        || key.startsWith(KEY_LINK_SUMMARY)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        }
    }

    private static CachedPreviewImageUrl loadCachedPreviewImageUrl(Context context, String previewImageCacheEntryId) {
        return loadCachedPreviewImageUrl(context, previewImageCacheEntryId, true);
    }

    private static CachedPreviewImageUrl loadCachedPreviewImageUrl(
            Context context,
            String previewImageCacheEntryId,
            boolean updateCacheOrder) {
        if (context == null || TextUtils.isEmpty(previewImageCacheEntryId)) {
            return new CachedPreviewImageUrl(false, null);
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String imageUrl = preferences.getString(getPreviewImageUrlKey(previewImageCacheEntryId), null);
            boolean loaded = preferences.getBoolean(getPreviewImageUrlLoadedKey(previewImageCacheEntryId), false)
                    || !TextUtils.isEmpty(imageUrl);
            if (updateCacheOrder && loaded) {
                movePreviewImageCacheIdToEnd(preferences, previewImageCacheEntryId);
            }
            return new CachedPreviewImageUrl(loaded, imageUrl);
        }
    }

    private static void saveCachedPreviewImageUrl(Context context, String previewImageCacheEntryId, String imageUrl) {
        if (context == null || TextUtils.isEmpty(previewImageCacheEntryId)) {
            return;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            List<String> orderedIds = readPreviewImageCacheOrder(preferences);
            orderedIds.remove(previewImageCacheEntryId);
            orderedIds.add(previewImageCacheEntryId);

            SharedPreferences.Editor editor = preferences.edit()
                    .putBoolean(getPreviewImageUrlLoadedKey(previewImageCacheEntryId), true);
            if (TextUtils.isEmpty(imageUrl)) {
                editor.remove(getPreviewImageUrlKey(previewImageCacheEntryId));
            } else {
                editor.putString(getPreviewImageUrlKey(previewImageCacheEntryId), imageUrl);
            }

            while (orderedIds.size() > MAX_DISK_CACHE_SIZE) {
                String oldestId = orderedIds.remove(0);
                editor.remove(getPreviewImageUrlKey(oldestId));
                editor.remove(getPreviewImageUrlLoadedKey(oldestId));
            }

            editor.putString(KEY_PREVIEW_IMAGE_CACHE_ORDER, TextUtils.join(",", orderedIds)).apply();
        }
    }

    public static void saveCachedPreviewImageTintColor(
            Context context,
            int storyId,
            String imageUrl,
            int baseColor,
            String paletteTintMode,
            int tintColor) {
        if (context == null || storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String tintColorKey = getPreviewImageTintColorKey(storyId, imageUrl, baseColor, paletteTintMode);
            List<String> orderedKeys = readPreviewImageTintCacheOrder(preferences);
            orderedKeys.remove(tintColorKey);
            orderedKeys.add(tintColorKey);

            SharedPreferences.Editor editor = preferences.edit()
                    .putInt(tintColorKey, tintColor);
            while (orderedKeys.size() > MAX_DISK_CACHE_SIZE) {
                editor.remove(orderedKeys.remove(0));
            }

            editor.putString(KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER, TextUtils.join(",", orderedKeys)).apply();
        }
    }

    public static Integer loadCachedPreviewImageTintColor(
            Context context,
            int storyId,
            String imageUrl,
            int baseColor,
            String paletteTintMode) {
        if (context == null || storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String key = getPreviewImageTintColorKey(storyId, imageUrl, baseColor, paletteTintMode);
            if (!preferences.contains(key)) {
                return null;
            }

            int tintColor = preferences.getInt(key, baseColor);
            movePreviewImageTintCacheKeyToEnd(preferences, key);
            return tintColor;
        }
    }

    private static void movePreviewImageCacheIdToEnd(SharedPreferences preferences, String previewImageCacheEntryId) {
        List<String> orderedIds = readPreviewImageCacheOrder(preferences);
        orderedIds.remove(previewImageCacheEntryId);
        orderedIds.add(previewImageCacheEntryId);
        preferences.edit()
                .putString(KEY_PREVIEW_IMAGE_CACHE_ORDER, TextUtils.join(",", orderedIds))
                .apply();
    }

    private static List<String> readPreviewImageCacheOrder(SharedPreferences preferences) {
        return readCacheOrder(preferences, KEY_PREVIEW_IMAGE_CACHE_ORDER);
    }

    private static List<String> readPreviewImageTintCacheOrder(SharedPreferences preferences) {
        return readCacheOrder(preferences, KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER);
    }

    private static List<String> readLinkSummaryCacheOrder(SharedPreferences preferences) {
        return readCacheOrder(preferences, KEY_LINK_SUMMARY_CACHE_ORDER);
    }

    private static List<String> readCacheOrder(SharedPreferences preferences, String orderKey) {
        List<String> orderedIds = new ArrayList<>();
        String order = preferences.getString(orderKey, "");
        if (TextUtils.isEmpty(order)) {
            return orderedIds;
        }

        String[] storyIds = order.split(",");
        for (String storyId : storyIds) {
            if (!TextUtils.isEmpty(storyId) && !orderedIds.contains(storyId)) {
                orderedIds.add(storyId);
            }
        }
        return orderedIds;
    }

    private static void movePreviewImageTintCacheKeyToEnd(SharedPreferences preferences, String tintColorKey) {
        List<String> orderedKeys = readPreviewImageTintCacheOrder(preferences);
        orderedKeys.remove(tintColorKey);
        orderedKeys.add(tintColorKey);
        preferences.edit()
                .putString(KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER, TextUtils.join(",", orderedKeys))
                .apply();
    }

    private static SharedPreferences getPreviewImageCachePreferences(Context context) {
        return context.getSharedPreferences(PREVIEW_IMAGE_CACHE_PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String getPreviewImageCacheEntryId(int storyId, String pageUrl) {
        if (storyId <= 0) {
            return null;
        }

        if (isYoutubeVideoUrl(pageUrl)) {
            return storyId + ":" + YOUTUBE_OEMBED_CACHE_SUFFIX;
        }
        return String.valueOf(storyId);
    }

    private static String getPreviewImageUrlKey(String previewImageCacheEntryId) {
        return KEY_PREVIEW_IMAGE_URL + previewImageCacheEntryId;
    }

    private static String getPreviewImageUrlLoadedKey(String previewImageCacheEntryId) {
        return KEY_PREVIEW_IMAGE_URL_LOADED + previewImageCacheEntryId;
    }

    private static String getPreviewImageTintColorKey(
            int storyId,
            String imageUrl,
            int baseColor,
            String paletteTintMode) {
        return KEY_PREVIEW_IMAGE_TINT_COLOR
                + getPreviewImageTintColorCacheId(storyId, imageUrl, baseColor, paletteTintMode);
    }

    public static LinkSummaryLoader.Result getCachedLinkSummary(
            @Nullable Context context,
            String pageUrl) {
        String normalizedUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedUrl)) {
            return null;
        }
        synchronized (StoryPreviewImageLoader.class) {
            LinkSummaryLoader.Result memoryResult = LINK_SUMMARY_CACHE.get(normalizedUrl);
            if (memoryResult != null) {
                return memoryResult;
            }
            if (context == null) {
                return null;
            }
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String key = getLinkSummaryKey(normalizedUrl);
            String serialized = preferences.getString(key, null);
            LinkSummaryLoader.Result result = deserializeLinkSummary(serialized);
            if (result != null) {
                LINK_SUMMARY_CACHE.put(normalizedUrl, result);
                List<String> order = readLinkSummaryCacheOrder(preferences);
                order.remove(key);
                order.add(key);
                preferences.edit()
                        .putString(KEY_LINK_SUMMARY_CACHE_ORDER, TextUtils.join(",", order))
                        .apply();
            }
            return result;
        }
    }

    public static void saveCachedLinkSummary(
            @Nullable Context context,
            String pageUrl,
            @Nullable LinkSummaryLoader.Result result) {
        String normalizedUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedUrl) || result == null) {
            return;
        }
        synchronized (StoryPreviewImageLoader.class) {
            if (LINK_SUMMARY_CACHE.size() >= MAX_CACHE_SIZE) {
                LINK_SUMMARY_CACHE.clear();
            }
            LINK_SUMMARY_CACHE.put(normalizedUrl, result);
            if (context == null) {
                return;
            }
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String key = getLinkSummaryKey(normalizedUrl);
            List<String> order = readLinkSummaryCacheOrder(preferences);
            order.remove(key);
            order.add(key);
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(key, serializeLinkSummary(result));
            while (order.size() > MAX_DISK_CACHE_SIZE) {
                editor.remove(order.remove(0));
            }
            editor.putString(KEY_LINK_SUMMARY_CACHE_ORDER, TextUtils.join(",", order)).apply();
        }
    }

    private static String getLinkSummaryKey(String pageUrl) {
        return KEY_LINK_SUMMARY + sha256Hex(pageUrl);
    }

    private static String serializeLinkSummary(LinkSummaryLoader.Result result) {
        try {
            return new JSONObject()
                    .put("title", result.title)
                    .put("site", result.siteName)
                    .put("author", result.author)
                    .put("published", result.publishedTime)
                    .put("language", result.language)
                    .put("type", result.contentType)
                    .put("description", result.description)
                    .put("image", result.imageUrl)
                    .put("url", result.finalUrl)
                    .toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static LinkSummaryLoader.Result deserializeLinkSummary(String serialized) {
        if (TextUtils.isEmpty(serialized)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(serialized);
            return new LinkSummaryLoader.Result(
                    json.optString("title", ""),
                    json.optString("site", ""),
                    json.optString("author", ""),
                    json.optString("published", ""),
                    json.optString("language", ""),
                    json.optString("type", ""),
                    json.optString("description", ""),
                    json.optString("image", ""),
                    json.optString("url", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String getPreviewImageTintColorCacheId(
            int storyId,
            String imageUrl,
            int baseColor,
            String paletteTintMode) {
        return ""
                + storyId
                + ":"
                + PreviewImageTintUtils.TINT_ALGORITHM_VERSION
                + ":"
                + baseColor
                + ":"
                + SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
                + ":"
                + sha256Hex(imageUrl);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int unsignedByte = bytes[i] & 0xff;
                hex[i * 2] = HEX_DIGITS[unsignedByte >>> 4];
                hex[i * 2 + 1] = HEX_DIGITS[unsignedByte & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static class CachedPreviewImageUrl {
        final boolean loaded;
        final String imageUrl;

        CachedPreviewImageUrl(boolean loaded, String imageUrl) {
            this.loaded = loaded;
            this.imageUrl = imageUrl;
        }
    }
}
