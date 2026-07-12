package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.summarization.Summarization;
import com.google.mlkit.genai.summarization.SummarizationRequest;
import com.google.mlkit.genai.summarization.SummarizationResult;
import com.google.mlkit.genai.summarization.Summarizer;
import com.google.mlkit.genai.summarization.SummarizerOptions;
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class SummaryManager {
    private static final String TAG = "SummaryManager";
    private static final int LOCAL_SUMMARY_MIN_CHARS = 400;
    private static final int LOCAL_SUMMARY_MAX_WORDS = 3000;
    private static final int CLOUD_SUMMARY_MAX_OUTPUT_TOKENS = 1000;
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after.";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface SummaryCallback {
        void onSuccess(String summary);
        void onFailure(String error);
    }

    public interface LocalSummaryAvailabilityCallback {
        void onResult(boolean available, String statusMessage);
    }

    public static void fetchModels(Context ctx, RequestQueue queue, SummaryCallback callback) {
        String baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_base_url", AiSummaryProviders.getDefaultBaseUrl());
        String apiKey = AiSummaryApiKeyStore.getApiKey(ctx);
        String url = joinUrl(baseUrl, "models");

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
            response -> {
                try {
                    JSONArray models = response.getJSONArray("data");
                    List<String> modelNames = new ArrayList<>();
                    for (int i = 0; i < models.length(); i++) {
                        modelNames.add(models.getJSONObject(i).getString("id"));
                    }
                    callback.onSuccess(String.join(",", modelNames));
                } catch (JSONException e) {
                    callback.onFailure("Failed to parse models");
                }
            },
            error -> callback.onFailure(error.getMessage() != null ? error.getMessage() : "Unknown error")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                if (!apiKey.isEmpty()) {
                    headers.put("Authorization", "Bearer " + apiKey);
                }
                return headers;
            }
        };

        queue.add(request);
    }

    public static void summarizeArticle(Context ctx, RequestQueue queue, String articleUrl, SummaryCallback callback) {
        new Thread(() -> {
            try {
                summarizeText(ctx, queue, extractMainContent(articleUrl), callback);
            } catch (Exception e) {
                postFailure(callback, "Extraction failed: " + getThrowableMessage(e));
            }
        }).start();
    }

    public static void summarizeText(Context ctx, RequestQueue queue, String text, SummaryCallback callback) {
        summarizeWithLLM(ctx, queue, prepareCloudSummaryInput(text), callback);
    }

    public static boolean canAttemptLocalSummarization() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static void checkLocalSummaryAvailability(Context ctx, LocalSummaryAvailabilityCallback callback) {
        if (!canAttemptLocalSummarization()) {
            postLocalAvailability(callback, false, "Gemini Nano requires Android 8.0 or newer");
            return;
        }

        Context appContext = ctx.getApplicationContext();
        new Thread(() -> {
            Summarizer summarizer = null;
            try {
                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
                int featureStatus = summarizer.checkFeatureStatus().get();
                postLocalAvailability(callback, isLocalFeatureUsable(featureStatus),
                        getLocalFeatureStatusMessage(featureStatus));
            } catch (ExecutionException e) {
                Log.w(TAG, "Gemini Nano availability check failed", e.getCause());
                postLocalAvailability(callback, false, getLocalAvailabilityFailureMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postLocalAvailability(callback, false, "Gemini Nano availability check was interrupted");
            } catch (Exception e) {
                Log.w(TAG, "Gemini Nano availability check failed", e);
                postLocalAvailability(callback, false, getLocalAvailabilityFailureMessage());
            } finally {
                if (summarizer != null) {
                    summarizer.close();
                }
            }
        }).start();
    }

    public static void summarizeArticleWithGeminiNano(Context ctx, String articleUrl, SummaryCallback callback) {
        if (!canAttemptLocalSummarization()) {
            postFailure(callback, "Gemini Nano requires Android 8.0 or newer");
            return;
        }

        Context appContext = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                summarizePreparedLocalText(appContext, prepareLocalSummaryInput(extractMainContent(articleUrl)), callback);
            } catch (ExecutionException e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Gemini Nano summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e));
            }
        }).start();
    }

    public static void summarizeTextWithGeminiNano(Context ctx, String text, SummaryCallback callback) {
        if (!canAttemptLocalSummarization()) {
            postFailure(callback, "Gemini Nano requires Android 8.0 or newer");
            return;
        }

        Context appContext = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                summarizePreparedLocalText(appContext, prepareLocalSummaryInput(text), callback);
            } catch (ExecutionException e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Gemini Nano summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e));
            }
        }).start();
    }

    public static String extractMainContent(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(10000)
                .get();
        Element body = doc.body();
        return body.text();
    }

    private static SummarizerOptions createLocalSummarizerOptions(Context context) {
        return SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build();
    }

    private static void summarizePreparedLocalText(Context appContext, String content, SummaryCallback callback)
            throws ExecutionException, InterruptedException {
        Summarizer summarizer = null;
        boolean summarizerReleased = false;
        try {
            if (content.length() < LOCAL_SUMMARY_MIN_CHARS) {
                postFailure(callback, "Article is too short for Gemini Nano summarization");
                return;
            }

            summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
            int featureStatus = summarizer.checkFeatureStatus().get();
            if (featureStatus == FeatureStatus.UNAVAILABLE) {
                postFailure(callback, "Gemini Nano summarization is not available on this device");
            } else if (featureStatus == FeatureStatus.DOWNLOADABLE
                    || featureStatus == FeatureStatus.DOWNLOADING) {
                downloadLocalFeatureAndSummarize(content, summarizer, callback);
                summarizerReleased = true;
            } else if (featureStatus == FeatureStatus.AVAILABLE) {
                runLocalInference(content, summarizer, callback);
                summarizerReleased = true;
            } else {
                postFailure(callback, "Gemini Nano summarization is not available on this device");
            }
        } finally {
            if (summarizer != null && !summarizerReleased) {
                summarizer.close();
            }
        }
    }

    private static void downloadLocalFeatureAndSummarize(String text, Summarizer summarizer, SummaryCallback callback) {
        summarizer.downloadFeature(new DownloadCallback() {
            @Override
            public void onDownloadCompleted() {
                runLocalInference(text, summarizer, callback);
            }

            @Override
            public void onDownloadFailed(GenAiException e) {
                summarizer.close();
                postFailure(callback, "Gemini Nano download failed: " + getThrowableMessage(e));
            }

            @Override
            public void onDownloadProgress(long totalBytesDownloaded) {
            }

            @Override
            public void onDownloadStarted(long bytesToDownload) {
            }
        });
    }

    private static void runLocalInference(String text, Summarizer summarizer, SummaryCallback callback) {
        new Thread(() -> {
            try {
                SummarizationRequest request = SummarizationRequest.builder(text).build();
                SummarizationResult result = summarizer.runInference(request).get();
                postSuccess(callback, result.getSummary());
            } catch (ExecutionException e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Gemini Nano summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e));
            } finally {
                summarizer.close();
            }
        }).start();
    }

    private static String prepareLocalSummaryInput(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return normalized;
        }

        String[] words = normalized.split("\\s+");
        if (words.length <= LOCAL_SUMMARY_MAX_WORDS) {
            return normalized;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < LOCAL_SUMMARY_MAX_WORDS; i++) {
            if (i > 0) {
                truncated.append(' ');
            }
            truncated.append(words[i]);
        }
        return truncated.toString();
    }

    private static String prepareCloudSummaryInput(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() > 15000) {
            return normalized.substring(0, 15000);
        }
        return normalized;
    }

    private static boolean isLocalFeatureUsable(int featureStatus) {
        return featureStatus == FeatureStatus.AVAILABLE
                || featureStatus == FeatureStatus.DOWNLOADABLE
                || featureStatus == FeatureStatus.DOWNLOADING;
    }

    private static String getLocalFeatureStatusMessage(int featureStatus) {
        if (featureStatus == FeatureStatus.AVAILABLE) {
            return "";
        } else if (featureStatus == FeatureStatus.DOWNLOADABLE) {
            return "Gemini Nano will download before the first local summary";
        } else if (featureStatus == FeatureStatus.DOWNLOADING) {
            return "Gemini Nano is downloading";
        } else {
            return "Gemini Nano not available on this device";
        }
    }

    private static String getLocalAvailabilityFailureMessage() {
        return "Gemini Nano not available on this device";
    }

    private static String getThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isEmpty()) {
            return "Unknown error";
        }
        return throwable.getMessage();
    }

    private static void summarizeWithLLM(Context ctx, RequestQueue queue, String text, SummaryCallback callback) {
        String apiKey = AiSummaryApiKeyStore.getApiKey(ctx);
        String baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_base_url", AiSummaryProviders.getDefaultBaseUrl());
        String model = AiSummaryProviders.getModelForRequest(baseUrl,
                PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_model", ""));

        if (apiKey.isEmpty()) {
            postFailure(callback, "API Key missing");
            return;
        }
        if (model.isEmpty()) {
            postFailure(callback, "Model missing. Open AI summarization settings and choose a model.");
            return;
        }

        String prompt = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_system_prompt", DEFAULT_SYSTEM_PROMPT);
        if (AiSummaryProviders.isAnthropicBaseUrl(baseUrl)) {
            summarizeWithAnthropic(queue, baseUrl, apiKey, model, prompt, text, callback);
        } else {
            summarizeWithChatCompletions(queue, baseUrl, apiKey, model, prompt, text, callback);
        }
    }

    private static void summarizeWithChatCompletions(RequestQueue queue,
                                                     String baseUrl,
                                                     String apiKey,
                                                     String model,
                                                     String prompt,
                                                     String text,
                                                     SummaryCallback callback) {
        String url = joinUrl(baseUrl, "chat/completions");

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);
            JSONArray messages = new JSONArray();

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", prompt);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", text);

            messages.put(systemMsg);
            messages.put(userMsg);
            payload.put("messages", messages);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, payload,
            response -> {
                try {
                    JSONArray choices = response.getJSONArray("choices");
                    String summary = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    postSuccess(callback, summary);
                } catch (JSONException e) {
                    postFailure(callback, "API response error");
                }
            },
            error -> {
                postFailure(callback, "API error: " + getApiErrorMessage(error));
            }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + apiKey);
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(120000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    private static void summarizeWithAnthropic(RequestQueue queue,
                                               String baseUrl,
                                               String apiKey,
                                               String model,
                                               String prompt,
                                               String text,
                                               SummaryCallback callback) {
        String url = joinUrl(baseUrl, "messages");

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);
            payload.put("max_tokens", CLOUD_SUMMARY_MAX_OUTPUT_TOKENS);
            payload.put("system", prompt);

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", text);
            messages.put(userMsg);
            payload.put("messages", messages);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, payload,
            response -> {
                String summary = parseAnthropicSummary(response);
                if (TextUtils.isEmpty(summary)) {
                    postFailure(callback, "API response error");
                } else {
                    postSuccess(callback, summary);
                }
            },
            error -> postFailure(callback, "API error: " + getApiErrorMessage(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(120000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    private static String parseAnthropicSummary(JSONObject response) {
        JSONArray content = response.optJSONArray("content");
        if (content == null) {
            return null;
        }

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String text = block.optString("text", "");
            if (!text.isEmpty()) {
                if (summary.length() > 0) {
                    summary.append("\n");
                }
                summary.append(text);
            }
        }
        return summary.toString();
    }

    private static String getApiErrorMessage(com.android.volley.VolleyError error) {
        String finalErrorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
        if (error.networkResponse != null && error.networkResponse.data != null) {
            try {
                String body = new String(error.networkResponse.data, "UTF-8");
                JSONObject errorJson = new JSONObject(body);
                if (errorJson.has("error")) {
                    Object errorObject = errorJson.get("error");
                    if (errorObject instanceof JSONObject) {
                        finalErrorMsg = ((JSONObject) errorObject).optString("message", finalErrorMsg);
                    } else if (errorObject instanceof String) {
                        finalErrorMsg = (String) errorObject;
                    }
                }
            } catch (Exception ignored) {}
        }
        return finalErrorMsg;
    }

    private static String joinUrl(String baseUrl, String path) {
        return AiSummaryProviders.normalizeUrl(baseUrl) + "/" + path;
    }

    private static void postSuccess(SummaryCallback callback, String summary) {
        MAIN_HANDLER.post(() -> callback.onSuccess(summary));
    }

    private static void postFailure(SummaryCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onFailure(error));
    }

    private static void postLocalAvailability(LocalSummaryAvailabilityCallback callback,
                                              boolean available,
                                              String statusMessage) {
        MAIN_HANDLER.post(() -> callback.onResult(available, statusMessage));
    }
}
