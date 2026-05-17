package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import com.simon.harmonichackernews.data.NitterInfo;

import org.json.JSONArray;
import org.json.JSONObject;

public class NitterGetter {

    public static boolean isValidNitterUrl(String url) {
        return url != null && url.contains("nitter.net");
    }

    public static boolean isConvertibleToNitter(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String host = u.getHost();
            if (host == null) return false;

            host = host.toLowerCase(java.util.Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            if (host.startsWith("mobile.")) host = host.substring(7);
            if (!host.equals("twitter.com") && !host.equals("x.com")) return false;

            String path = u.getPath();
            if (path == null) return false;

            // /<handle>/status/<digits> or /i/web/status/<digits>, allow trailing segments like /photo/1
            return path.matches("^/(?:[A-Za-z0-9_]{1,15}/status/\\d+|i/web/status/\\d+)(?:/[^/]*)*$");
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }


    public static String convertToNitterUrl(String url) {
        return url.replace("twitter.com", "nitter.net").replace("x.com", "nitter.net");
    }

    public static void getInfo(WebView webView, Context ctx, NitterGetter.GetterCallback callback) {
        NitterInfo nitterInfo = new NitterInfo();

        webView.evaluateJavascript("(function() { " +
                "function absoluteUrl(value) {" +
                "   if (!value || value === 'null') return null;" +
                "   try { return new URL(value, window.location.origin).href; } catch (e) { return value; }" +
                "}" +
                "function text(parent, selector) {" +
                "   var element = selector ? (parent ? parent.querySelector(selector) : null) : parent;" +
                "   return element ? element.textContent.trim() : '';" +
                "}" +
                "function html(parent, selector) {" +
                "   var element = parent ? parent.querySelector(selector) : null;" +
                "   return element ? element.innerHTML : '';" +
                "}" +
                "function media(parent) {" +
                "   if (!parent) return { imgSrc: null, hasVideo: false };" +
                "   var video = parent.querySelector('.attachment.video-container video, .gallery-video video, .media-gif video, video');" +
                "   if (video) {" +
                "       var posterImage = parent.querySelector('.attachment.video-container img, .gallery-video img, .media-gif img');" +
                "       return {" +
                "           imgSrc: absoluteUrl(video.getAttribute('poster') || (posterImage ? posterImage.getAttribute('src') : null))," +
                "           hasVideo: true" +
                "       };" +
                "   }" +
                "   var videoImage = parent.querySelector('.attachment.video-container img, .gallery-video img, .media-gif img');" +
                "   if (videoImage) {" +
                "       return { imgSrc: absoluteUrl(videoImage.getAttribute('src')), hasVideo: true };" +
                "   }" +
                "   var imageLink = parent.querySelector('.attachments a.still-image[href], .gallery-row a.still-image[href], .attachment a.still-image[href], a.still-image[href]');" +
                "   var image = parent.querySelector('.attachments img, .gallery-row img, .attachment img');" +
                "   return { imgSrc: absoluteUrl(imageLink ? imageLink.getAttribute('href') : (image ? image.getAttribute('src') : null)), hasVideo: false };" +
                "}" +
                "var mainTweet = document.querySelector('.main-tweet');" +
                "var beforeTweet = document.querySelector('.before-tweet');" +
                "if (!mainTweet) return null;" +
                "var mainMedia = media(mainTweet);" +
                "var beforeMedia = media(beforeTweet);" +
                "var replyElement = mainTweet.querySelector('.icon-comment');" +
                "var repostElement = mainTweet.querySelector('.icon-retweet');" +
                "var likeElement = mainTweet.querySelector('.icon-heart');" +
                "return JSON.stringify({" +
                "   text: html(mainTweet, '.tweet-content')," +
                "   userName: text(mainTweet, '.fullname')," +
                "   userTag: text(mainTweet, '.username')," +
                "   date: text(mainTweet, '.tweet-date')," +
                "   replyCount: text(replyElement ? replyElement.parentNode : null)," +
                "   reposts: text(repostElement ? repostElement.parentNode : null)," +
                "   likes: text(likeElement ? likeElement.parentNode : null)," +
                "   beforeName: text(beforeTweet, '.fullname')," +
                "   beforeTag: text(beforeTweet, '.username')," +
                "   beforeText: html(beforeTweet, '.tweet-content')," +
                "   beforeDate: text(beforeTweet, '.tweet-date')," +
                "   beforeImgSrc: beforeMedia.imgSrc," +
                "   imgSrc: mainMedia.imgSrc," +
                "   hasVideo: mainMedia.hasVideo" +
                "});" +
                "}) ();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String resp) {
                try {
                    resp = new JSONArray("[" + resp + "]").getString(0);

                    JSONObject jsonObject = new JSONObject(resp);
                    nitterInfo.text = jsonObject.getString("text").replace("\n", "<br>");
                    nitterInfo.userName = jsonObject.getString("userName");
                    nitterInfo.userTag = jsonObject.getString("userTag");
                    nitterInfo.date = jsonObject.getString("date");
                    nitterInfo.replyCount = jsonObject.getString("replyCount");
                    nitterInfo.reposts = jsonObject.getString("reposts");
                    nitterInfo.likes = jsonObject.getString("likes");
                    nitterInfo.imgSrc = jsonObject.optString("imgSrc");
                    nitterInfo.hasVideo = jsonObject.optBoolean("hasVideo");

                    nitterInfo.beforeUserName = jsonObject.optString("beforeName");
                    nitterInfo.beforeUserTag = jsonObject.optString("beforeTag");
                    nitterInfo.beforeText = jsonObject.optString("beforeText");
                    nitterInfo.beforeDate = jsonObject.optString("beforeDate");
                    nitterInfo.beforeImgSrc = jsonObject.optString("beforeImgSrc");

                    if (TextUtils.isEmpty(nitterInfo.imgSrc) || nitterInfo.imgSrc.equals("null")) {
                        nitterInfo.imgSrc = null;
                    }

                    if (TextUtils.isEmpty(nitterInfo.beforeImgSrc) || nitterInfo.beforeImgSrc.equals("null")) {
                        nitterInfo.beforeImgSrc = null;
                    }

                    callback.onSuccess(nitterInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure("Failed at getting Nitter info");
                }
            }
        });

    }

    public interface GetterCallback {
        void onSuccess(NitterInfo nitterInfo);
        void onFailure(String reason);
    }

}
