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

    private static final String UNSUPPORTED_PREVIEW_ELEMENTS =
            "script, style, svg, wiki-chart, table, figure, iframe, canvas, noscript, object, embed";

    public static boolean isValidWikipediaUrl(String url) {
        String regex = "^(https|http)://en.wikipedia.org/wiki/.+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    public static Request<?> getInfo(String wikipediaUrl, Context ctx, WikipediaGetter.GetterCallback callback) {
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
                                Document doc = sanitizeSummaryHtml(summary);

                                if (doc.body().hasText()) {
                                    WikipediaInfo wikiInfo = new WikipediaInfo();
                                    wikiInfo.summary = doc.body().html();
                                    callback.onSuccess(wikiInfo);
                                } else {
                                    callback.onFailure("Wikipedia did not return a visible summary");
                                }
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
            return stringRequest;

        } catch (Exception e) {
            callback.onFailure("Invalid Wikipedia URL");
            return null;
        }
    }

    private static Document sanitizeSummaryHtml(String summaryHtml) {
        Document document = Jsoup.parseBodyFragment(summaryHtml);

        // HtmlTextView cannot render rich MediaWiki content such as charts or tables. Remove
        // these elements with their contents so embedded SVG labels and CSS are not shown as
        // flattened preview text.
        document.select(UNSUPPORTED_PREVIEW_ELEMENTS).remove();

        // HtmlTextView gives blockquotes a hard-coded white background. Keep the quoted text and
        // inline formatting, but render it as ordinary preview content.
        for (Element blockquote : document.select("blockquote")) {
            blockquote.unwrap();
        }

        for (Element element : document.select("p, ul, ol")) {
            if (!element.hasText()) {
                element.remove();
            }
        }

        return document;
    }

    public static String getFirstParagraphText(String summaryHtml) {
        if (TextUtils.isEmpty(summaryHtml)) {
            return "";
        }

        Document document = Jsoup.parse(summaryHtml);
        Element firstParagraph = document.selectFirst("p");
        return firstParagraph == null ? document.text() : firstParagraph.text();
    }

    public interface GetterCallback {
        void onSuccess(WikipediaInfo wikiInfo);

        void onFailure(String reason);
    }
}
