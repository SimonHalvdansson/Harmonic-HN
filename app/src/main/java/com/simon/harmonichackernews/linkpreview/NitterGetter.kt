package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.network.NitterPreview
import com.simon.harmonichackernews.serialization.JsonStringCodec

object NitterGetter {
    fun getInfo(webView: WebView, ctx: Context?, callback: GetterCallback) {
        webView.evaluateJavascript(
            "(function() { " +
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
                    "}) ();", object : ValueCallback<String> {
                override fun onReceiveValue(value: String?) {
                    try {
                        val response = JsonStringCodec.decodeJavascriptString(value)
                            ?: error("Nitter returned an invalid script result")
                        callback.onSuccess(NitterPreview.parseJavascriptResult(response))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure("Failed at getting Nitter info")
                    }
                }
            })
    }

    interface GetterCallback {
        fun onSuccess(nitterInfo: NitterInfo?)
        fun onFailure(reason: String?)
    }
}
