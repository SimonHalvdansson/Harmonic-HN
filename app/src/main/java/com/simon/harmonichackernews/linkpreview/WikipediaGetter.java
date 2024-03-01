package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.WikipediaInfo;
import com.simon.harmonichackernews.network.NetworkComponent;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikipediaGetter {

    public static boolean isValidWikipediaUrl(String url) {
        String regex = "^(https|http)://en.wikipedia.org/wiki/.+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    public static void getInfo(String wikipediaUrl, Context ctx, WikipediaGetter.GetterCallback callback) {
        try {
            String title = wikipediaUrl.split("en.wikipedia.org/wiki/")[1];

            String apiUrl = "https://en.wikipedia.org/w/api.php?format=json&action=query&prop=extracts&exintro&titles=" + title;

            StringRequest stringRequest = new StringRequest(Request.Method.GET, apiUrl,
                    response -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            JSONObject pages = jsonResponse.getJSONObject("query").getJSONObject("pages");
                            String pageId = pages.keys().next();
                            String summary = pages.getJSONObject(pageId).optString("extract");

                            if (!TextUtils.isEmpty(summary)) {
                                Document doc = Jsoup.parse(summary);

                                // Remove empty <ul> elements
                                for (Element ul : doc.select("ul")) {
                                    if (!ul.hasText()) {
                                        ul.remove();
                                    }
                                }

                                String cleanedHtml = doc.html();

                                WikipediaInfo wikiInfo = new WikipediaInfo();

                                wikiInfo.summary = cleanedHtml;
                                callback.onSuccess(wikiInfo);
                            } else {
                                callback.onFailure("Failed to retrieve Wikipedia summary");
                            }

                        } catch (Exception e) {
                            callback.onFailure("Failed to parse Wikipedia API response");
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        error.printStackTrace();
                        callback.onFailure("Couldn't connect to Wikipedia API");
                    });

            RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
            queue.add(stringRequest);

        } catch (Exception e) {
            callback.onFailure("Invalid Wikipedia URL");
        }
    }

    public interface GetterCallback {
        void onSuccess(WikipediaInfo wikiInfo);

        void onFailure(String reason);
    }
}
