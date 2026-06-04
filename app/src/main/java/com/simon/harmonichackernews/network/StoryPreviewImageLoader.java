package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final String KEY_PREVIEW_IMAGE_URL =
            "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<String, String> IMAGE_CACHE = new HashMap<>();
    private static final Set<String> MISS_CACHE = new HashSet<>();
    private static final Map<String, PendingPreviewImageBatch> PENDING_CALLBACKS = new HashMap<>();

    private static final String[] IMAGE_SELECTORS = new String[]{
            "meta[property=og:image:secure_url]",
            "meta[property=og:image:url]",
            "meta[property=og:image]",
            "meta[name=twitter:image:src]",
            "meta[name=twitter:image]",
            "meta[itemprop=image]",
            "link[rel=image_src]"
    };

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
        Context appContext = context == null ? null : context.getApplicationContext();
        PendingPreviewImageRequest previewImageRequest = new PendingPreviewImageRequest(appContext, storyId, callback);
        String cachedDiskImageUrl = loadCachedPreviewImageUrl(appContext, storyId);
        if (!TextUtils.isEmpty(cachedDiskImageUrl)) {
            postResult(previewImageRequest, cachedDiskImageUrl);
            return previewImageRequest;
        }

        String normalizedPageUrl = normalizeHttpUrl(pageUrl);
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            postResult(previewImageRequest, null);
            return previewImageRequest;
        }

        if (isLikelyImageUrl(normalizedPageUrl)) {
            saveCachedPreviewImageUrl(appContext, storyId, normalizedPageUrl);
            postResult(previewImageRequest, normalizedPageUrl);
            return previewImageRequest;
        }

        PendingPreviewImageBatch pendingBatch;
        synchronized (StoryPreviewImageLoader.class) {
            String cachedImageUrl = IMAGE_CACHE.get(normalizedPageUrl);
            if (!TextUtils.isEmpty(cachedImageUrl)) {
                saveCachedPreviewImageUrl(appContext, storyId, cachedImageUrl);
                postResult(previewImageRequest, cachedImageUrl);
                return previewImageRequest;
            }

            if (MISS_CACHE.contains(normalizedPageUrl)) {
                postResult(previewImageRequest, null);
                return previewImageRequest;
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
                .url(normalizedPageUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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
                finish(normalizedPageUrl, requestBatch, null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        finish(normalizedPageUrl, requestBatch, null);
                        return;
                    }

                    String contentType = closeableResponse.header("Content-Type", "");
                    if (!TextUtils.isEmpty(contentType)
                            && !contentType.toLowerCase(Locale.US).contains("html")) {
                        finish(normalizedPageUrl, requestBatch, null);
                        return;
                    }

                    String responseBody = closeableResponse.body().string();
                    String baseUrl = closeableResponse.request().url().toString();
                    finish(normalizedPageUrl, requestBatch, extractPreviewImageUrl(responseBody, baseUrl));
                } catch (Exception e) {
                    finish(normalizedPageUrl, requestBatch, null);
                }
            }
        });
        return previewImageRequest;
    }

    private static String extractPreviewImageUrl(String html, String baseUrl) {
        if (TextUtils.isEmpty(html)) {
            return null;
        }

        Document document = Jsoup.parse(html, baseUrl);
        for (String selector : IMAGE_SELECTORS) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }

            String attribute = "link".equals(element.tagName()) ? "href" : "content";
            String imageUrl = makeAbsoluteHttpUrl(element.attr(attribute), baseUrl);
            if (!TextUtils.isEmpty(imageUrl)) {
                return imageUrl;
            }
        }

        return null;
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

    private static String makeAbsoluteHttpUrl(String candidate, String baseUrl) {
        if (TextUtils.isEmpty(candidate) || candidate.trim().startsWith("data:")) {
            return null;
        }

        HttpUrl parsedBase = HttpUrl.parse(baseUrl);
        HttpUrl parsedUrl = parsedBase == null
                ? HttpUrl.parse(candidate.trim())
                : parsedBase.resolve(candidate.trim());

        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            return null;
        }

        return parsedUrl.toString();
    }

    private static boolean isHttpScheme(HttpUrl url) {
        return "http".equals(url.scheme()) || "https".equals(url.scheme());
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

    private static void finish(String pageUrl, PendingPreviewImageBatch batch, String imageUrl) {
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

        if (!TextUtils.isEmpty(imageUrl)) {
            for (PendingPreviewImageRequest pendingRequest : pendingRequests) {
                if (!pendingRequest.isCancelled()) {
                    saveCachedPreviewImageUrl(pendingRequest.context, pendingRequest.storyId, imageUrl);
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
                        || key.startsWith(KEY_PREVIEW_IMAGE_URL)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        }
    }

    private static String loadCachedPreviewImageUrl(Context context, int storyId) {
        if (context == null || storyId <= 0) {
            return null;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            String imageUrl = preferences.getString(getPreviewImageUrlKey(storyId), null);
            if (!TextUtils.isEmpty(imageUrl)) {
                movePreviewImageCacheIdToEnd(preferences, storyId);
            }
            return imageUrl;
        }
    }

    private static void saveCachedPreviewImageUrl(Context context, int storyId, String imageUrl) {
        if (context == null || storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return;
        }

        synchronized (StoryPreviewImageLoader.class) {
            SharedPreferences preferences = getPreviewImageCachePreferences(context);
            List<String> orderedIds = readPreviewImageCacheOrder(preferences);
            String storyIdString = String.valueOf(storyId);
            orderedIds.remove(storyIdString);
            orderedIds.add(storyIdString);

            SharedPreferences.Editor editor = preferences.edit()
                    .putString(getPreviewImageUrlKey(storyId), imageUrl);

            while (orderedIds.size() > MAX_DISK_CACHE_SIZE) {
                String oldestId = orderedIds.remove(0);
                editor.remove(getPreviewImageUrlKey(oldestId));
            }

            editor.putString(KEY_PREVIEW_IMAGE_CACHE_ORDER, TextUtils.join(",", orderedIds)).apply();
        }
    }

    private static void movePreviewImageCacheIdToEnd(SharedPreferences preferences, int storyId) {
        List<String> orderedIds = readPreviewImageCacheOrder(preferences);
        String storyIdString = String.valueOf(storyId);
        orderedIds.remove(storyIdString);
        orderedIds.add(storyIdString);
        preferences.edit()
                .putString(KEY_PREVIEW_IMAGE_CACHE_ORDER, TextUtils.join(",", orderedIds))
                .apply();
    }

    private static List<String> readPreviewImageCacheOrder(SharedPreferences preferences) {
        List<String> orderedIds = new ArrayList<>();
        String order = preferences.getString(KEY_PREVIEW_IMAGE_CACHE_ORDER, "");
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

    private static SharedPreferences getPreviewImageCachePreferences(Context context) {
        return context.getSharedPreferences(PREVIEW_IMAGE_CACHE_PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String getPreviewImageUrlKey(int storyId) {
        return getPreviewImageUrlKey(String.valueOf(storyId));
    }

    private static String getPreviewImageUrlKey(String storyId) {
        return KEY_PREVIEW_IMAGE_URL + storyId;
    }
}
