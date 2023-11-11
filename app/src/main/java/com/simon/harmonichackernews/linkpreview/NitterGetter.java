package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.WebView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.NitterInfo;
import com.simon.harmonichackernews.data.WikipediaInfo;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NitterGetter {

    public static boolean isValidNitterUrl(String url) {
        return url.contains("nitter.net");
    }

    public static boolean isConvertibleToNitter(String url) {
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            return host.equals("twitter.com") || host.equals("x.com");
        } catch (MalformedURLException e) {
            return false; // URL is not valid
        }
    }

    public static String convertToNitterUrl(String url) {
        return url.replace("twitter.com", "nitter.net").replace("x.com", "nitter.net");
    }

    public static void getInfo(WebView webView, Context ctx, NitterGetter.GetterCallback callback) {
        NitterInfo nitterInfo = new NitterInfo();

        webView.evaluateJavascript("(function() { " +
                "var imgElement = document.querySelector('.main-tweet .attachment.image img');" +
                "var beforeImgElement = document.querySelector('.before-tweet .attachment.image img');" +
                "var imgSrc = imgElement ? (window.location.origin + imgElement.getAttribute('src')) : null;" +
                "var beforeImgSrc = beforeImgElement ? (window.location.origin + beforeImgElement.getAttribute('src')) : null;" +  // Fixed this line
                "var beforeTweet = document.querySelector('.before-tweet');" +
                "return JSON.stringify({" +
                "   text: document.querySelector('.main-tweet .tweet-content').innerHTML," +
                "   userName: document.querySelector('.main-tweet .fullname').textContent," +
                "   userTag: document.querySelector('.main-tweet .username').textContent," +
                "   date: document.querySelector('.main-tweet .tweet-date').textContent," +
                "   replyCount: document.querySelector('.main-tweet .icon-comment').parentNode.textContent.trim()," +
                "   reposts: document.querySelector('.main-tweet .icon-retweet').parentNode.textContent.trim()," +
                "   quotes: document.querySelector('.main-tweet .icon-quote').parentNode.textContent.trim()," +
                "   likes: document.querySelector('.main-tweet .icon-heart').parentNode.textContent.trim()," +
                "   beforeName: beforeTweet ? beforeTweet.querySelector('.fullname').textContent : null," +
                "   beforeTag: beforeTweet ? beforeTweet.querySelector('.username').textContent : null," +
                "   beforeText: beforeTweet ? beforeTweet.querySelector('.tweet-content').innerHTML : null," +
                "   beforeDate: beforeTweet ? beforeTweet.querySelector('.tweet-date').textContent : null," +
                "   beforeImgSrc: beforeImgSrc," +
                "   imgSrc: imgSrc" +
                "});" +
                "}) ();", resp -> {
            try {
                //Get rid of double escaping things or something like that...
                resp = resp.substring(1, resp.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");

                JSONObject jsonObject = new JSONObject(resp);
                nitterInfo.text = jsonObject.getString("text").replace("\n", "<br>");
                nitterInfo.userName = jsonObject.getString("userName");
                nitterInfo.userTag = jsonObject.getString("userTag");
                nitterInfo.date = jsonObject.getString("date");
                nitterInfo.replyCount = jsonObject.getString("replyCount");
                nitterInfo.reposts = jsonObject.getString("reposts");
                nitterInfo.quotes = jsonObject.getString("quotes");
                nitterInfo.likes = jsonObject.getString("likes");
                nitterInfo.imgSrc = jsonObject.optString("imgSrc");

                nitterInfo.beforeUserName = jsonObject.optString("beforeName");
                nitterInfo.beforeUserTag = jsonObject.optString("beforeTag");
                nitterInfo.beforeText = jsonObject.optString("beforeText");
                nitterInfo.beforeDate = jsonObject.optString("beforeDate");
                nitterInfo.beforeImgSrc = jsonObject.optString("beforeImgSrc");

                if (nitterInfo.imgSrc != null && nitterInfo.imgSrc.equals("null")) {
                    nitterInfo.imgSrc = null;
                }

                if (nitterInfo.beforeImgSrc != null && nitterInfo.beforeImgSrc.equals("null")) {
                    nitterInfo.beforeImgSrc = null;
                }

                callback.onSuccess(nitterInfo);
            } catch (Exception e) {
                e.printStackTrace();
                callback.onFailure("Failed at getting Nitter info");
            }
        });
    }

    public interface GetterCallback {
        void onSuccess(NitterInfo nitterInfo);
        void onFailure(String reason);
    }

}
