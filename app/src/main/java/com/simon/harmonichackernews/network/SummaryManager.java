package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

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
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface SummaryCallback {
        void onSuccess(String summary);
        void onFailure(String error);
    }

    public interface LocalSummaryAvailabilityCallback {
        void onResult(boolean available, String statusMessage);
    }

    public static void fetchModels(Context ctx, RequestQueue queue, SummaryCallback callback) {
        String baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_base_url", "https://api.openai.com/v1");
        String apiKey = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_api_key", "");
        String url = baseUrl + "/models";

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
                String content = extractMainContent(articleUrl);
                if (content.length() > 15000) {
                    content = content.substring(0, 15000);
                }
                summarizeWithLLM(ctx, queue, content, callback);
            } catch (Exception e) {
                postFailure(callback, "Extraction failed: " + getThrowableMessage(e));
            }
        }).start();
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
                postLocalAvailability(callback, false,
                        "Gemini Nano availability check failed: " + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postLocalAvailability(callback, false, "Gemini Nano availability check was interrupted");
            } catch (Exception e) {
                postLocalAvailability(callback, false,
                        "Gemini Nano availability check failed: " + getThrowableMessage(e));
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
            Summarizer summarizer = null;
            boolean summarizerReleased = false;
            try {
                String content = prepareLocalSummaryInput(extractMainContent(articleUrl));
                if (content.length() < LOCAL_SUMMARY_MIN_CHARS) {
                    postFailure(callback, "Article is too short for Gemini Nano summarization");
                    return;
                }

                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
                int featureStatus = summarizer.checkFeatureStatus().get();
                if (featureStatus == FeatureStatus.UNAVAILABLE) {
                    postFailure(callback, "Gemini Nano summarization is not available on this device");
                } else if (featureStatus == FeatureStatus.DOWNLOADABLE) {
                    downloadLocalFeatureAndSummarize(content, summarizer, callback);
                    summarizerReleased = true;
                } else if (featureStatus == FeatureStatus.DOWNLOADING || featureStatus == FeatureStatus.AVAILABLE) {
                    runLocalInference(content, summarizer, callback);
                    summarizerReleased = true;
                } else {
                    postFailure(callback, "Gemini Nano summarization is not available on this device");
                }
            } catch (ExecutionException e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postFailure(callback, "Gemini Nano summarization was interrupted");
            } catch (Exception e) {
                postFailure(callback, "Gemini Nano failed: " + getThrowableMessage(e));
            } finally {
                if (summarizer != null && !summarizerReleased) {
                    summarizer.close();
                }
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

    private static String getThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isEmpty()) {
            return "Unknown error";
        }
        return throwable.getMessage();
    }

    private static void summarizeWithLLM(Context ctx, RequestQueue queue, String text, SummaryCallback callback) {
        String apiKey = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_api_key", "");
        String baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_base_url", "https://api.openai.com/v1");
        String model = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_model", "gpt-3.5-turbo");

        if (apiKey.isEmpty()) {
            postFailure(callback, "API Key missing");
            return;
        }

        String url = baseUrl + "/chat/completions";

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);
            JSONArray messages = new JSONArray();

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            String defaultPrompt = "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after.";
            String prompt = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_system_prompt", defaultPrompt);
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
                String finalErrorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                if (error.networkResponse != null && error.networkResponse.data != null) {
                    try {
                        String body = new String(error.networkResponse.data, "UTF-8");
                        JSONObject errorJson = new JSONObject(body);
                        if (errorJson.has("error")) {
                            finalErrorMsg = errorJson.getJSONObject("error").optString("message", finalErrorMsg);
                        }
                    } catch (Exception ignored) {}
                }
                String errorForCallback = finalErrorMsg;
                postFailure(callback, "API error: " + errorForCallback);
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
