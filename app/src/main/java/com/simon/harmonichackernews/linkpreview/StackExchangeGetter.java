package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.StackExchangeInfo;
import com.simon.harmonichackernews.network.NetworkComponent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StackExchangeGetter {

    private static final Map<String, String> SITE_PARAMS = new HashMap<>();
    private static final Map<String, String> SITE_NAMES = new HashMap<>();

    static {
        SITE_PARAMS.put("stackoverflow.com", "stackoverflow");
        SITE_PARAMS.put("serverfault.com", "serverfault");
        SITE_PARAMS.put("superuser.com", "superuser");
        SITE_PARAMS.put("askubuntu.com", "askubuntu");
        SITE_PARAMS.put("mathoverflow.net", "mathoverflow");
        SITE_PARAMS.put("stackapps.com", "stackapps");
        SITE_PARAMS.put("meta.stackoverflow.com", "meta.stackoverflow");
        SITE_PARAMS.put("meta.serverfault.com", "meta.serverfault");
        SITE_PARAMS.put("meta.superuser.com", "meta.superuser");
        SITE_PARAMS.put("meta.askubuntu.com", "meta.askubuntu");
        SITE_PARAMS.put("meta.mathoverflow.net", "meta.mathoverflow");

        SITE_NAMES.put("stackoverflow", "Stack Overflow");
        SITE_NAMES.put("serverfault", "Server Fault");
        SITE_NAMES.put("superuser", "Super User");
        SITE_NAMES.put("askubuntu", "Ask Ubuntu");
        SITE_NAMES.put("mathoverflow", "MathOverflow");
        SITE_NAMES.put("stackapps", "Stack Apps");
        SITE_NAMES.put("meta.stackoverflow", "Meta Stack Overflow");
        SITE_NAMES.put("meta.serverfault", "Meta Server Fault");
        SITE_NAMES.put("meta.superuser", "Meta Super User");
        SITE_NAMES.put("meta.askubuntu", "Meta Ask Ubuntu");
        SITE_NAMES.put("meta.mathoverflow", "Meta MathOverflow");
        SITE_NAMES.put("meta", "Meta Stack Exchange");
    }

    public static boolean isValidStackExchangeUrl(String url) {
        return getRequestInfo(url) != null;
    }

    public static void getInfo(String stackExchangeUrl, Context ctx, GetterCallback callback) {
        try {
            RequestInfo requestInfo = getRequestInfo(stackExchangeUrl);
            if (requestInfo == null) {
                callback.onFailure("Invalid Stack Exchange URL");
                return;
            }

            String endpoint = requestInfo.isAnswer
                    ? "https://api.stackexchange.com/2.3/answers/" + requestInfo.id + "/questions"
                    : "https://api.stackexchange.com/2.3/questions/" + requestInfo.id;
            String apiUrl = endpoint + "?site=" + Uri.encode(requestInfo.siteParam) + "&filter=withbody";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, apiUrl,
                    response -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            JSONArray items = jsonResponse.getJSONArray("items");

                            if (items.length() == 0) {
                                callback.onFailure("Stack Exchange question not found");
                                return;
                            }

                            JSONObject item = items.getJSONObject(0);
                            StackExchangeInfo stackExchangeInfo = new StackExchangeInfo();

                            stackExchangeInfo.site = getSiteName(requestInfo.siteParam);
                            stackExchangeInfo.title = cleanText(item.optString("title"));
                            stackExchangeInfo.questionText = cleanBodyText(item.optString("body"));
                            stackExchangeInfo.score = item.optInt("score");
                            stackExchangeInfo.answerCount = item.optInt("answer_count");
                            stackExchangeInfo.viewCount = item.optInt("view_count");
                            stackExchangeInfo.isAnswered = item.optBoolean("is_answered");
                            stackExchangeInfo.hasAcceptedAnswer = item.has("accepted_answer_id");

                            if (item.has("owner") && !item.get("owner").toString().equals("null")) {
                                JSONObject owner = item.getJSONObject("owner");
                                stackExchangeInfo.author = cleanText(owner.optString("display_name"));
                            }

                            if (item.has("tags")) {
                                JSONArray tags = item.getJSONArray("tags");
                                stackExchangeInfo.tags = new String[tags.length()];
                                for (int i = 0; i < tags.length(); i++) {
                                    stackExchangeInfo.tags[i] = tags.getString(i);
                                }
                            }

                            callback.onSuccess(stackExchangeInfo);

                        } catch (Exception e) {
                            callback.onFailure("Failed to parse Stack Exchange API response");
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        error.printStackTrace();
                        callback.onFailure("Couldn't connect to Stack Exchange API");
                    });

            RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
            queue.add(stringRequest);

        } catch (Exception e) {
            callback.onFailure("Invalid Stack Exchange URL");
        }
    }

    private static RequestInfo getRequestInfo(String url) {
        try {
            Uri uri = Uri.parse(url);
            String siteParam = getSiteParam(uri.getHost());
            if (siteParam == null) {
                return null;
            }

            List<String> segments = uri.getPathSegments();
            for (int i = 0; i < segments.size() - 1; i++) {
                String segment = segments.get(i);
                if (segment.equals("questions") || segment.equals("q")) {
                    return new RequestInfo(siteParam, segments.get(i + 1), false);
                } else if (segment.equals("a")) {
                    return new RequestInfo(siteParam, segments.get(i + 1), true);
                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private static String getSiteParam(String host) {
        if (host == null) {
            return null;
        }

        host = host.toLowerCase();
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }

        if (SITE_PARAMS.containsKey(host)) {
            return SITE_PARAMS.get(host);
        }

        if (host.endsWith(".stackexchange.com")) {
            String subdomain = host.substring(0, host.length() - ".stackexchange.com".length());
            if (subdomain.equals("meta")) {
                return "meta";
            }
            return subdomain;
        }

        return null;
    }

    private static String getSiteName(String siteParam) {
        if (SITE_NAMES.containsKey(siteParam)) {
            return SITE_NAMES.get(siteParam);
        }

        String[] parts = siteParam.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String cleanText(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        return Jsoup.parse(text).text();
    }

    private static String cleanBodyText(String html) {
        if (TextUtils.isEmpty(html)) {
            return null;
        }

        Document document = Jsoup.parse(html);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        document.select("br").append("\\n");
        document.select("p, pre, blockquote, ul, ol").before("\\n");
        document.select("li").before("\\n");
        String text = document.wholeText();
        return text
                .replace("\\n", "\n")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static class RequestInfo {
        final String siteParam;
        final String id;
        final boolean isAnswer;

        RequestInfo(String siteParam, String id, boolean isAnswer) {
            this.siteParam = siteParam;
            this.id = id;
            this.isAnswer = isAnswer;
        }
    }

    public interface GetterCallback {
        void onSuccess(StackExchangeInfo stackExchangeInfo);

        void onFailure(String reason);
    }
}
