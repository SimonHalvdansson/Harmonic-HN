package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

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

public class SummaryManager {
    private static final String TAG = "SummaryManager";

    public interface SummaryCallback {
        void onSuccess(String summary);
        void onFailure(String error);
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
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure("Extraction failed: " + e.getMessage()));
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

    private static void summarizeWithLLM(Context ctx, RequestQueue queue, String text, SummaryCallback callback) {
        String apiKey = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_api_key", "");
        String baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_base_url", "https://api.openai.com/v1");
        String model = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_model", "gpt-3.5-turbo");

        if (apiKey.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onFailure("API Key missing"));
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
                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(summary));
                } catch (JSONException e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onFailure("API response error"));
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
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure("API error: " + errorForCallback));
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
}
