package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.serialization.JsonStringCodec

object NitterPreview {
    private val statusPathPattern =
        Regex("^/(?:[A-Za-z0-9_]{1,15}/status/\\d+|i/web/status/\\d+)(?:/[^/]*)*$")

    /** DOM extraction program shared by WebView, WKWebView, and desktop browser adapters. */
    val extractionScript: String = """
        (function() {
            function absoluteUrl(value) {
                if (!value || value === 'null') return null;
                try { return new URL(value, window.location.origin).href; } catch (e) { return value; }
            }
            function text(parent, selector) {
                var element = selector ? (parent ? parent.querySelector(selector) : null) : parent;
                return element ? element.textContent.trim() : '';
            }
            function html(parent, selector) {
                var element = parent ? parent.querySelector(selector) : null;
                return element ? element.innerHTML : '';
            }
            function media(parent) {
                if (!parent) return { imgSrc: null, hasVideo: false };
                var video = parent.querySelector('.attachment.video-container video, .gallery-video video, .media-gif video, video');
                if (video) {
                    var poster = parent.querySelector('.attachment.video-container img, .gallery-video img, .media-gif img');
                    return {
                        imgSrc: absoluteUrl(video.getAttribute('poster') || (poster ? poster.getAttribute('src') : null)),
                        hasVideo: true
                    };
                }
                var videoImage = parent.querySelector('.attachment.video-container img, .gallery-video img, .media-gif img');
                if (videoImage) return { imgSrc: absoluteUrl(videoImage.getAttribute('src')), hasVideo: true };
                var imageLink = parent.querySelector('.attachments a.still-image[href], .gallery-row a.still-image[href], .attachment a.still-image[href], a.still-image[href]');
                var image = parent.querySelector('.attachments img, .gallery-row img, .attachment img');
                return {
                    imgSrc: absoluteUrl(imageLink ? imageLink.getAttribute('href') : (image ? image.getAttribute('src') : null)),
                    hasVideo: false
                };
            }
            var mainTweet = document.querySelector('.main-tweet');
            var beforeTweet = document.querySelector('.before-tweet');
            if (!mainTweet) return null;
            var mainMedia = media(mainTweet);
            var beforeMedia = media(beforeTweet);
            var replyElement = mainTweet.querySelector('.icon-comment');
            var repostElement = mainTweet.querySelector('.icon-retweet');
            var likeElement = mainTweet.querySelector('.icon-heart');
            return JSON.stringify({
                text: html(mainTweet, '.tweet-content'),
                userName: text(mainTweet, '.fullname'),
                userTag: text(mainTweet, '.username'),
                date: text(mainTweet, '.tweet-date'),
                replyCount: text(replyElement ? replyElement.parentNode : null),
                reposts: text(repostElement ? repostElement.parentNode : null),
                likes: text(likeElement ? likeElement.parentNode : null),
                beforeName: text(beforeTweet, '.fullname'),
                beforeTag: text(beforeTweet, '.username'),
                beforeText: html(beforeTweet, '.tweet-content'),
                beforeDate: text(beforeTweet, '.tweet-date'),
                beforeImgSrc: beforeMedia.imgSrc,
                imgSrc: mainMedia.imgSrc,
                hasVideo: mainMedia.hasVideo
            });
        })();
    """.trimIndent()

    fun parseEvaluationResult(value: String?): NitterInfo? {
        val json = JsonStringCodec.decodeJavascriptString(value)
            ?: error("Nitter returned an invalid script result")
        if (json == "null" || json.isBlank()) return null
        return parseJavascriptResult(json)
    }

    fun isNitterUrl(url: String?): Boolean =
        url?.toNetworkUrlOrNull()?.host?.lowercase()?.removePrefix("www.") == "nitter.net"

    fun isConvertibleUrl(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
            .removePrefix("www.")
            .removePrefix("mobile.")
        if (host != "twitter.com" && host != "x.com") return false
        val path = "/" + parsed.pathSegments.filter(String::isNotEmpty).joinToString("/")
        return statusPathPattern.matches(path)
    }

    fun convertUrl(url: String): String {
        val parsed = url.toNetworkUrlOrNull()
            ?: throw IllegalArgumentException("Invalid X/Twitter URL")
        require(isConvertibleUrl(url)) { "URL is not a supported X/Twitter status" }
        val builder = parsed.newBuilder()
        return builder.host("nitter.net").build().toString()
    }

    fun statusId(url: String?): String? {
        val segments = url?.toNetworkUrlOrNull()?.pathSegments.orEmpty()
        val statusIndex = segments.indexOf("status")
        return segments.getOrNull(statusIndex + 1)?.takeIf(String::isNotEmpty)
    }

    fun isSamePage(firstUrl: String?, secondUrl: String?): Boolean {
        val first = firstUrl?.toNetworkUrlOrNull()
        val second = secondUrl?.toNetworkUrlOrNull()
        if (first == null || second == null) return firstUrl == secondUrl
        val firstStatus = statusId(firstUrl)
        val secondStatus = statusId(secondUrl)
        if (!firstStatus.isNullOrEmpty() && !secondStatus.isNullOrEmpty()) {
            return firstStatus == secondStatus
        }
        return normalizeHost(first.host) == normalizeHost(second.host) &&
            trimTrailingSlash(first.encodedPath) == trimTrailingSlash(second.encodedPath)
    }

    fun parseJavascriptResult(json: String): NitterInfo {
        val value = JsonObject(json)
        return NitterInfo().apply {
            text = value.optString("text").replace("\n", "<br>")
            userName = value.optString("userName")
            userTag = value.optString("userTag")
            date = value.optString("date")
            replyCount = value.optString("replyCount")
            reposts = value.optString("reposts")
            likes = value.optString("likes")
            imgSrc = value.nullableString("imgSrc")
            hasVideo = value.optBoolean("hasVideo")
            beforeUserName = value.optString("beforeName")
            beforeUserTag = value.optString("beforeTag")
            beforeText = value.optString("beforeText")
            beforeDate = value.optString("beforeDate")
            beforeImgSrc = value.nullableString("beforeImgSrc")
        }
    }

    private fun JsonObject.nullableString(key: String): String? {
        val value: String? = optString(key, null)
        return value?.takeUnless { it.isEmpty() || it == "null" }
    }

    private fun normalizeHost(host: String): String = host.lowercase().removePrefix("www.")

    private fun trimTrailingSlash(path: String): String = when {
        path.isEmpty() || path == "/" -> ""
        else -> path.trimEnd('/')
    }
}
