package com.simon.harmonichackernews.network;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Resolves the provider artwork embedded in OpenRouter provider pages. */
public final class OpenRouterProviderIconLoader {
    private static final String OPENROUTER_URL = "https://openrouter.ai";
    private static final long REQUEST_SPACING_MS = 120L;
    private static final long RETRY_DELAY_MS = 750L;
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern UNSUPPORTED_DISPLAY_P3_STYLE = Pattern.compile(
            "\\sstyle=(\"[^\"]*color\\(display-p3[^\"]*\"|'[^']*color\\(display-p3[^']*')",
            Pattern.CASE_INSENSITIVE);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<String, Object> CACHE = new HashMap<>();
    private static final Map<String, List<CallbackListener>> IN_FLIGHT = new HashMap<>();
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static boolean queueRunning;

    private OpenRouterProviderIconLoader() {
    }

    public interface CallbackListener {
        void onResolved(String providerSlug, @Nullable Object iconData);
    }

    public static void resolve(String providerSlug, CallbackListener listener) {
        String normalizedSlug = providerSlug == null
                ? ""
                : providerSlug.trim().toLowerCase(Locale.ROOT);
        if (normalizedSlug.isEmpty()) {
            MAIN_HANDLER.post(() -> listener.onResolved(normalizedSlug, null));
            return;
        }

        boolean startQueue = false;
        synchronized (CACHE) {
            Object cached = CACHE.get(normalizedSlug);
            if (cached != null) {
                MAIN_HANDLER.post(() -> listener.onResolved(normalizedSlug, cached));
                return;
            }
            List<CallbackListener> waiting = IN_FLIGHT.get(normalizedSlug);
            if (waiting != null) {
                waiting.add(listener);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(listener);
            IN_FLIGHT.put(normalizedSlug, waiting);
            QUEUE.add(normalizedSlug);
            if (!queueRunning) {
                queueRunning = true;
                startQueue = true;
            }
        }
        if (startQueue) {
            requestNext(0L);
        }
    }

    private static void requestNext(long delayMs) {
        MAIN_HANDLER.postDelayed(() -> {
            String providerSlug;
            synchronized (CACHE) {
                providerSlug = QUEUE.poll();
                if (providerSlug == null) {
                    queueRunning = false;
                    return;
                }
            }
            requestProviderPage(providerSlug, 1);
        }, delayMs);
    }

    private static void requestProviderPage(String normalizedSlug, int attempt) {
        HttpUrl providerUrl = HttpUrl.get(OPENROUTER_URL).newBuilder()
                .addPathSegment(normalizedSlug)
                .build();
        Request request = new Request.Builder().url(providerUrl).build();
        NetworkComponent.getOkHttpClientInstance().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                retryOrFinish(normalizedSlug, attempt, null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String iconUrl = null;
                boolean retryable = false;
                try (Response closeableResponse = response) {
                    if (closeableResponse.isSuccessful() && closeableResponse.body() != null) {
                        Document page = Jsoup.parse(
                                closeableResponse.body().string(), providerUrl.toString());
                        iconUrl = findProviderIcon(page, normalizedSlug);
                    } else {
                        int code = closeableResponse.code();
                        retryable = code == 403 || code == 408 || code == 429 || code >= 500;
                    }
                } catch (Exception ignored) {
                    // Provider initials remain visible when OpenRouter changes or rejects the page.
                }
                if (retryable) {
                    retryOrFinish(normalizedSlug, attempt, iconUrl);
                } else if (iconUrl != null && isSvgUrl(iconUrl)) {
                    fetchSvg(normalizedSlug, iconUrl);
                } else {
                    finish(normalizedSlug, iconUrl);
                }
            }
        });
    }

    private static void fetchSvg(String providerSlug, String iconUrl) {
        Request request = new Request.Builder().url(iconUrl).build();
        NetworkComponent.getOkHttpClientInstance().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                finish(providerSlug, iconUrl);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        finish(providerSlug, iconUrl);
                        return;
                    }
                    byte[] rawSvg = closeableResponse.body().bytes();
                    String svg = new String(rawSvg, StandardCharsets.UTF_8);
                    String sanitized = UNSUPPORTED_DISPLAY_P3_STYLE.matcher(svg).replaceAll("");
                    finish(providerSlug, sanitized.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    finish(providerSlug, iconUrl);
                }
            }
        });
    }

    private static boolean isSvgUrl(String iconUrl) {
        return iconUrl.toLowerCase(Locale.ROOT).contains(".svg");
    }

    private static void retryOrFinish(String providerSlug, int attempt,
                                      @Nullable String iconUrl) {
        if (attempt < MAX_ATTEMPTS) {
            MAIN_HANDLER.postDelayed(
                    () -> requestProviderPage(providerSlug, attempt + 1),
                    RETRY_DELAY_MS * attempt);
        } else {
            finish(providerSlug, iconUrl);
        }
    }

    @Nullable
    private static String findProviderIcon(Document page, String providerSlug) {
        String expectedAlt = "Favicon for " + providerSlug;
        for (Element image : page.select("img[alt][src]")) {
            if (!expectedAlt.equalsIgnoreCase(image.attr("alt").trim())) {
                continue;
            }
            String iconUrl = image.absUrl("src");
            try {
                HttpUrl parsed = HttpUrl.get(iconUrl);
                if ("https".equals(parsed.scheme()) || "http".equals(parsed.scheme())) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void finish(String providerSlug, @Nullable Object iconData) {
        List<CallbackListener> listeners;
        synchronized (CACHE) {
            if (iconData != null) {
                CACHE.put(providerSlug, iconData);
            }
            listeners = IN_FLIGHT.remove(providerSlug);
        }
        if (listeners != null) {
            MAIN_HANDLER.post(() -> {
                for (CallbackListener listener : listeners) {
                    listener.onResolved(providerSlug, iconData);
                }
            });
        }
        requestNext(REQUEST_SPACING_MS);
    }
}
