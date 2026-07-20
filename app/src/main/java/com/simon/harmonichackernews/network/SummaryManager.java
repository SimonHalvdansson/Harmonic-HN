package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

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
import com.simon.harmonichackernews.summary.local.LocalModelInference;
import com.simon.harmonichackernews.summary.local.LocalModelManager;
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SummaryManager {
    private static final String TAG = "SummaryManager";
    private static final int LOCAL_SUMMARY_MIN_CHARS = 400;
    private static final int LOCAL_SUMMARY_MAX_WORDS = 3000;
    private static final int CLOUD_SUMMARY_MAX_OUTPUT_TOKENS = 1000;
    private static final String PREF_STREAM_RESPONSES = "pref_ai_summary_stream_responses";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after.";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile int cachedLocalFeatureStatus = Integer.MIN_VALUE;

    public interface SummaryCallback {
        default void onProgress(String summary) {
        }

        default void onDebugInfo(String debugInfo) {
        }

        void onSuccess(String summary);
        void onFailure(String error);
    }

    public interface LocalSummaryAvailabilityCallback {
        void onResult(boolean available, boolean downloadableFallbackRequired,
                      String statusMessage);
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
        return true;
    }

    public static void checkLocalSummaryAvailability(Context ctx, LocalSummaryAvailabilityCallback callback) {
        if (!canAttemptLocalSummarization()) {
            postLocalAvailability(callback, false, false,
                    "Gemini Nano requires Android 8.0 or newer");
            return;
        }

        Context appContext = ctx.getApplicationContext();
        new Thread(() -> {
            Summarizer summarizer = null;
            try {
                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
                int featureStatus = summarizer.checkFeatureStatus().get();
                cachedLocalFeatureStatus = featureStatus;
                postResolvedLocalAvailability(callback, featureStatus);
            } catch (ExecutionException e) {
                Log.w(TAG, "Gemini Nano availability check failed", e.getCause());
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
                postDownloadableModelAvailability(callback);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postLocalAvailability(callback, false, false,
                        "Gemini Nano availability check was interrupted");
            } catch (Exception e) {
                Log.w(TAG, "Gemini Nano availability check failed", e);
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
                postDownloadableModelAvailability(callback);
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
                summarizePreparedTextLocally(appContext,
                        prepareLocalSummaryInput(extractMainContent(articleUrl)), callback);
            } catch (ExecutionException e) {
                postFailure(callback, "Local summarization failed: "
                        + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Local summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Local summarization failed: " + getThrowableMessage(e));
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
                summarizePreparedTextLocally(appContext, prepareLocalSummaryInput(text), callback);
            } catch (ExecutionException e) {
                postFailure(callback, "Local summarization failed: "
                        + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Local summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Local summarization failed: " + getThrowableMessage(e));
            }
        }).start();
    }

    public static boolean isLocalSummaryReady(Context context) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(context);
        if (LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            return isLocalFeatureUsable(cachedLocalFeatureStatus);
        }
        return LocalModelManager.isModelSupported(selected)
                && LocalModelManager.isSelectedModelDownloaded(context);
    }

    public static boolean isLocalSummaryConfigurationKnown(Context context) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(context);
        return !LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)
                || cachedLocalFeatureStatus != Integer.MIN_VALUE;
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

    private static void summarizePreparedTextLocally(Context appContext,
                                                     String content,
                                                     SummaryCallback callback)
            throws ExecutionException, InterruptedException {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(appContext);
        if (!LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            summarizeWithDownloadedLocalModel(appContext, content, callback);
            return;
        }
        postDebugInfo(callback, "Gemini Nano · load —");

        Summarizer availabilityChecker = null;
        try {
            availabilityChecker = Summarization.getClient(createLocalSummarizerOptions(appContext));
            int featureStatus = availabilityChecker.checkFeatureStatus().get();
            cachedLocalFeatureStatus = featureStatus;
            if (isLocalFeatureUsable(featureStatus)) {
                summarizePreparedLocalText(appContext, content, callback);
                return;
            }
        } catch (ExecutionException e) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
            throw e;
        } catch (RuntimeException e) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
            throw e;
        } finally {
            if (availabilityChecker != null) {
                availabilityChecker.close();
            }
        }

        postFailure(callback,
                "Gemini Nano is unavailable. Select another local model in AI summarization settings.");
    }

    private static void summarizeWithDownloadedLocalModel(Context appContext,
                                                           String content,
                                                           SummaryCallback callback) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(appContext);
        if (!LocalModelManager.isModelSupported(selected)) {
            postFailure(callback, LocalModelManager.getModelUnsupportedReason(selected));
            return;
        }
        if (!LocalModelManager.isSelectedModelDownloaded(appContext)) {
            postFailure(callback, "Download the selected local model before using it");
            return;
        }
        if (content.length() < LOCAL_SUMMARY_MIN_CHARS) {
            postFailure(callback, "Article is too short for local summarization");
            return;
        }

        try {
            postSuccess(callback, LocalModelInference.summarize(
                    appContext,
                    content,
                    summary -> postProgress(callback, summary),
                    loadMillis -> postDebugInfo(
                            callback, formatLoadInfo(selected.displayName, loadMillis))));
        } catch (Exception e) {
            postFailure(callback, "Local model failed: " + getThrowableMessage(e));
        }
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

    private static void postResolvedLocalAvailability(LocalSummaryAvailabilityCallback callback,
                                                      int featureStatus) {
        if (isLocalFeatureUsable(featureStatus)) {
            postLocalAvailability(callback, true, false,
                    getLocalFeatureStatusMessage(featureStatus));
        } else {
            postDownloadableModelAvailability(callback);
        }
    }

    private static void postDownloadableModelAvailability(
            LocalSummaryAvailabilityCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            postLocalAvailability(callback, true, true,
                    "Gemini Nano isn't available on this device");
        } else {
            postLocalAvailability(callback, false, false,
                    "Gemini Nano is unavailable; downloadable models require Android 12 or newer");
        }
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
        postDebugInfo(callback, model + " · load —");

        String prompt = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_system_prompt", DEFAULT_SYSTEM_PROMPT);
        boolean streamResponses = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(PREF_STREAM_RESPONSES, true);
        if (AiSummaryProviders.isAnthropicBaseUrl(baseUrl)) {
            summarizeWithAnthropic(queue, baseUrl, apiKey, model, prompt, text,
                    streamResponses, callback);
        } else {
            summarizeWithChatCompletions(queue, baseUrl, apiKey, model, prompt, text,
                    streamResponses, callback);
        }
    }

    private static void summarizeWithChatCompletions(RequestQueue queue,
                                                     String baseUrl,
                                                     String apiKey,
                                                     String model,
                                                     String prompt,
                                                     String text,
                                                     boolean streamResponses,
                                                     SummaryCallback callback) {
        String url = joinUrl(baseUrl, "chat/completions");

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);
            payload.put("stream", streamResponses);
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

        requestSummary(url, payload, new okhttp3.Request.Builder()
                .header("Authorization", "Bearer " + apiKey), false,
                streamResponses, callback);
    }

    private static void summarizeWithAnthropic(RequestQueue queue,
                                               String baseUrl,
                                               String apiKey,
                                               String model,
                                               String prompt,
                                               String text,
                                               boolean streamResponses,
                                               SummaryCallback callback) {
        String url = joinUrl(baseUrl, "messages");

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);
            payload.put("max_tokens", CLOUD_SUMMARY_MAX_OUTPUT_TOKENS);
            payload.put("system", prompt);
            payload.put("stream", streamResponses);

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", text);
            messages.put(userMsg);
            payload.put("messages", messages);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        requestSummary(url, payload, new okhttp3.Request.Builder()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01"), true,
                streamResponses, callback);
    }

    private static void requestSummary(String url,
                                       JSONObject payload,
                                       okhttp3.Request.Builder requestBuilder,
                                       boolean anthropic,
                                       boolean streamResponses,
                                       SummaryCallback callback) {
        RequestBody requestBody = RequestBody.create(payload.toString(),
                MediaType.get("application/json; charset=utf-8"));
        okhttp3.Request request = requestBuilder
                .url(url)
                .header("Accept", streamResponses ? "text/event-stream" : "application/json")
                .post(requestBody)
                .build();
        OkHttpClient client = NetworkComponent.getOkHttpClientInstance().newBuilder()
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postFailure(callback, "API error: " + getThrowableMessage(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = body == null ? "" : body.string();
                        postFailure(callback, "API error: "
                                + getApiErrorMessage(errorBody, response.message()));
                        return;
                    }
                    if (body == null) {
                        postFailure(callback, "API response error");
                        return;
                    }

                    if (streamResponses) {
                        readSummaryStream(body, anthropic, callback);
                    } else {
                        readSummaryResponse(body, anthropic, callback);
                    }
                } catch (IOException e) {
                    postFailure(callback, "API error: " + getThrowableMessage(e));
                }
            }
        });
    }

    private static void readSummaryResponse(ResponseBody body,
                                            boolean anthropic,
                                            SummaryCallback callback) throws IOException {
        String summary = parseNonStreamingResponse(body.string(), anthropic);
        if (TextUtils.isEmpty(summary)) {
            postFailure(callback, "API response error");
        } else {
            postSuccess(callback, summary);
        }
    }

    private static void readSummaryStream(ResponseBody body,
                                          boolean anthropic,
                                          SummaryCallback callback) throws IOException {
        StringBuilder summary = new StringBuilder();
        StringBuilder eventData = new StringBuilder();
        StringBuilder plainResponse = new StringBuilder();
        boolean sawSseData = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (eventData.length() > 0) {
                        sawSseData = true;
                        if (appendStreamEvent(eventData.toString(), anthropic, summary, callback)) {
                            eventData.setLength(0);
                            break;
                        }
                        eventData.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) {
                        eventData.append('\n');
                    }
                    eventData.append(line.substring(5).trim());
                } else if (!line.startsWith(":")) {
                    if (plainResponse.length() > 0) {
                        plainResponse.append('\n');
                    }
                    plainResponse.append(line);
                }
            }
        }

        if (eventData.length() > 0) {
            sawSseData = true;
            appendStreamEvent(eventData.toString(), anthropic, summary, callback);
        }

        if (!sawSseData && summary.length() == 0 && plainResponse.length() > 0) {
            appendNonStreamingResponse(plainResponse.toString(), anthropic, summary, callback);
        }

        if (summary.length() == 0) {
            postFailure(callback, "API response error");
        } else {
            postSuccess(callback, summary.toString());
        }
    }

    private static boolean appendStreamEvent(String data,
                                             boolean anthropic,
                                             StringBuilder summary,
                                             SummaryCallback callback) throws IOException {
        if ("[DONE]".equals(data)) {
            return true;
        }

        try {
            JSONObject event = new JSONObject(data);
            if ("error".equals(event.optString("type")) || event.has("error")) {
                throw new IOException(getApiErrorMessage(data, "Streaming request failed"));
            }

            String chunk;
            if (anthropic) {
                JSONObject delta = event.optJSONObject("delta");
                chunk = delta == null ? "" : delta.optString("text", "");
            } else {
                JSONArray choices = event.optJSONArray("choices");
                JSONObject choice = choices == null ? null : choices.optJSONObject(0);
                JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                chunk = delta == null ? "" : delta.optString("content", "");
            }

            appendSummaryChunk(summary, chunk, callback);
            return false;
        } catch (JSONException e) {
            throw new IOException("Invalid streaming response", e);
        }
    }

    private static void appendNonStreamingResponse(String responseBody,
                                                   boolean anthropic,
                                                   StringBuilder summary,
                                                   SummaryCallback callback) throws IOException {
        appendSummaryChunk(summary, parseNonStreamingResponse(responseBody, anthropic), callback);
    }

    private static String parseNonStreamingResponse(String responseBody,
                                                    boolean anthropic) throws IOException {
        try {
            JSONObject response = new JSONObject(responseBody);
            if (anthropic) {
                return parseAnthropicSummary(response);
            }
            JSONArray choices = response.optJSONArray("choices");
            JSONObject choice = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            return message == null ? "" : message.optString("content", "");
        } catch (JSONException e) {
            throw new IOException("Invalid API response", e);
        }
    }

    private static void appendSummaryChunk(StringBuilder summary,
                                           String chunk,
                                           SummaryCallback callback) {
        if (TextUtils.isEmpty(chunk)) {
            return;
        }
        summary.append(chunk);
        postProgress(callback, summary.toString());
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

    private static String getApiErrorMessage(String body, String fallback) {
        if (TextUtils.isEmpty(body)) {
            return fallback;
        }
        try {
            JSONObject errorJson = new JSONObject(body);
            if (errorJson.has("error")) {
                Object errorObject = errorJson.get("error");
                if (errorObject instanceof JSONObject) {
                    return ((JSONObject) errorObject).optString("message", fallback);
                }
                if (errorObject instanceof String) {
                    return (String) errorObject;
                }
            }
            return errorJson.optString("message", fallback);
        } catch (JSONException ignored) {
            return fallback;
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        return AiSummaryProviders.normalizeUrl(baseUrl) + "/" + path;
    }

    private static void postSuccess(SummaryCallback callback, String summary) {
        MAIN_HANDLER.post(() -> callback.onSuccess(summary));
    }

    private static void postProgress(SummaryCallback callback, String summary) {
        MAIN_HANDLER.post(() -> callback.onProgress(summary));
    }

    private static void postDebugInfo(SummaryCallback callback, String debugInfo) {
        MAIN_HANDLER.post(() -> callback.onDebugInfo(debugInfo));
    }

    private static String formatLoadInfo(String modelName, long loadMillis) {
        if (loadMillis < 1000L) {
            return modelName + " · " + loadMillis + " ms load";
        }
        return modelName + " · "
                + String.format(Locale.US, "%.1f s", loadMillis / 1000d)
                + " load";
    }

    private static void postFailure(SummaryCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onFailure(error));
    }

    private static void postLocalAvailability(LocalSummaryAvailabilityCallback callback,
                                              boolean available,
                                              boolean downloadableFallbackRequired,
                                              String statusMessage) {
        MAIN_HANDLER.post(() -> callback.onResult(
                available, downloadableFallbackRequired, statusMessage));
    }
}
