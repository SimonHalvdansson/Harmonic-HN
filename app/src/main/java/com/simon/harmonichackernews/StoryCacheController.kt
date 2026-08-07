package com.simon.harmonichackernews

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.utils.ArticleSnapshotDownloader
import com.simon.harmonichackernews.utils.ArticleSnapshotDownloader.DownloadCallback
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class StoryCacheController(private val callbacks: Callbacks) {
    internal interface Callbacks {
        val context: Context?

        val requestQueue: RequestQueue?

        val requestTag: Any

        fun onCacheProgressChanged()
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    var isCachingStories: Boolean = false
        private set
    var isProgressVisible: Boolean = false
        private set
    private var progressAnimationGeneration = 0
    private var cacheStoriesTotal = 1
    var progress: Int = 0
        private set
    private var progressStatus: String = CACHE_PROGRESS_STATUS_CACHING
    private val pendingArticleDownloads = ArrayDeque<ArticleDownload>()
    private val activeArticleDownloads = HashSet<Call>()
    private var articleSnapshotDownloader: ArticleSnapshotDownloader? = null
    private var articleDownloadGeneration = 0

    fun dispose() {
        articleDownloadGeneration++
        pendingArticleDownloads.clear()
        for (call in activeArticleDownloads) {
            call.cancel()
        }
        activeArticleDownloads.clear()
        articleSnapshotDownloader = null
        progressAnimationGeneration++
        progressHandler.removeCallbacksAndMessages(null)
        isCachingStories = false
        isProgressVisible = false
        resetProgressState()
    }

    val progressMax: Int
        get() = max(cacheStoriesTotal, 1)

    fun getProgressStatus(): String {
        return if (isCachingStories) cachingStatus else progressStatus
    }

    fun cacheStories() {
        if (isCachingStories) {
            return
        }

        val context = callbacks.context
        val queue = callbacks.requestQueue
        if (context == null || queue == null) {
            return
        }

        val storiesToCache = SettingsUtils.getStoriesToCache(context)
        startProgress(storiesToCache)
        val cacheArticles = SettingsUtils.shouldUseIntegratedWebView(context)
        articleSnapshotDownloader = if (cacheArticles)
            ArticleSnapshotDownloader(context)
        else
            null
        val request = StringRequest(
            Request.Method.GET, Utils.URL_TOP,
            Response.Listener { response: String ->
                try {
                    val arr = JSONArray(response)
                    val storyCount = storiesToCache
                    if (storyCount == 0) {
                        finishProgress(CACHE_PROGRESS_STATUS_EMPTY)
                        return@Listener
                    }

                    val remaining = intArrayOf(storyCount)
                    val articleFailures = intArrayOf(0)
                    for (i in 0..<storyCount) {
                        val id = arr.getInt(i)
                        val url = "https://hn.algolia.com/api/v1/items/" + id
                        val storyRequest = StringRequest(
                            Request.Method.GET,
                            url,
                            Response.Listener { storyResponse: String ->
                                Utils.cacheStory(context, id, storyResponse)
                                if (cacheArticles) {
                                    cacheStoryArticleSnapshot(
                                        id,
                                        storyResponse,
                                        articleFailures,
                                        { onCacheStoryFinished(remaining) })
                                } else {
                                    onCacheStoryFinished(remaining)
                                }
                            },
                            Response.ErrorListener {
                                onCacheStoryFinished(remaining)
                            })
                        storyRequest.setTag(callbacks.requestTag)
                        queue.add(storyRequest)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    finishProgress(CACHE_PROGRESS_STATUS_FAILED)
                }
            }, Response.ErrorListener {
                finishProgress(CACHE_PROGRESS_STATUS_FAILED)
            })

        request.setTag(callbacks.requestTag)
        queue.add(request)
    }

    private fun startProgress(total: Int) {
        progressHandler.removeCallbacksAndMessages(null)
        progressAnimationGeneration++
        isCachingStories = true
        isProgressVisible = true
        cacheStoriesTotal = max(total, 1)
        progress = 0
        progressStatus = CACHE_PROGRESS_STATUS_CACHING
        callbacks.onCacheProgressChanged()
    }

    private fun incrementProgress() {
        progress = min(progress + 1, cacheStoriesTotal)
        callbacks.onCacheProgressChanged()
    }

    private fun finishProgress(status: String = CACHE_PROGRESS_STATUS_FINISHED) {
        isCachingStories = false
        isProgressVisible = true
        progressStatus = status
        callbacks.onCacheProgressChanged()

        val animationGeneration = ++progressAnimationGeneration
        progressHandler.postDelayed(Runnable progressTask@ {
            if (progressAnimationGeneration != animationGeneration) {
                return@progressTask
            }
            isProgressVisible = false
            resetProgressState()
            callbacks.onCacheProgressChanged()
        }, CACHE_PROGRESS_FINISHED_HOLD_MS)
    }

    private fun resetProgressState() {
        cacheStoriesTotal = 1
        progress = 0
        progressStatus = CACHE_PROGRESS_STATUS_CACHING
    }

    private val cachingStatus: String
        get() = "Caching $cacheStoriesTotal" +
            if (cacheStoriesTotal == 1) " story" else " stories"

    private fun onCacheStoryFinished(remaining: IntArray) {
        incrementProgress()
        remaining[0]--
        if (remaining[0] > 0) {
            return
        }

        finishProgress()
    }

    private fun cacheStoryArticleSnapshot(
        id: Int,
        storyJson: String,
        articleFailures: IntArray,
        onComplete: () -> Unit
    ) {
        if (articleSnapshotDownloader == null) {
            onComplete()
            return
        }

        try {
            val storyObject = JSONObject(storyJson)
            if (!storyObject.has("url") || storyObject.isNull("url")) {
                onComplete()
                return
            }

            val articleUrl = storyObject.optString("url", "")
            if (articleUrl.isEmpty() ||
                !(articleUrl.startsWith("http://") || articleUrl.startsWith("https://"))
            ) {
                onComplete()
                return
            }

            pendingArticleDownloads.add(
                ArticleDownload(
                    id, articleUrl, articleFailures, onComplete, articleDownloadGeneration
                )
            )
            startPendingArticleDownloads()
        } catch (e: JSONException) {
            e.printStackTrace()
            articleFailures[0]++
            onComplete()
        }
    }

    private fun startPendingArticleDownloads() {
        val downloader = articleSnapshotDownloader ?: return

        while (activeArticleDownloads.size < MAX_CONCURRENT_ARTICLE_DOWNLOADS
            && pendingArticleDownloads.isNotEmpty()
        ) {
            val download = pendingArticleDownloads.removeFirst()
            if (download.generation != articleDownloadGeneration) {
                continue
            }

            val call = downloader.download(
                download.storyId,
                download.articleUrl,
                DownloadCallback downloadCallback@ { completedCall: Call, success: Boolean ->
                    if (download.generation != articleDownloadGeneration) {
                        return@downloadCallback
                    }
                    activeArticleDownloads.remove(completedCall)
                    if (!success) {
                        download.articleFailures[0]++
                    }
                    download.onComplete()
                    startPendingArticleDownloads()
                })
            if (call == null) {
                download.articleFailures[0]++
                download.onComplete()
                continue
            }
            activeArticleDownloads.add(call)
        }
    }

    private class ArticleDownload(
        val storyId: Int,
        val articleUrl: String,
        val articleFailures: IntArray,
        val onComplete: () -> Unit,
        val generation: Int
    )

    companion object {
        private const val CACHE_PROGRESS_FINISHED_HOLD_MS: Long = 1000
        private const val CACHE_PROGRESS_STATUS_CACHING = "Caching stories"
        private const val CACHE_PROGRESS_STATUS_FINISHED = "Finished"
        private const val CACHE_PROGRESS_STATUS_FAILED = "Caching failed"
        private const val CACHE_PROGRESS_STATUS_EMPTY = "No stories to cache"
        private const val MAX_CONCURRENT_ARTICLE_DOWNLOADS = 4
    }
}
