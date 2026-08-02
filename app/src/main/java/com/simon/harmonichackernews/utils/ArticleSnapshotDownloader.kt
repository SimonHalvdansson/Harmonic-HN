package com.simon.harmonichackernews.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.NetworkComponent.okHttpClientInstance
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.ToLongFunction
import kotlin.math.max
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

class ArticleSnapshotDownloader(context: Context) {
    private val appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        appContext = context.getApplicationContext()
    }

    fun download(
        storyId: Int,
        articleUrl: String,
        callback: DownloadCallback
    ): Call? {
        val request: Request
        try {
            request = Request.Builder()
                .url(articleUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }

        val call = okHttpClientInstance!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(failedCall: Call, e: IOException) {
                deliverResult(callback, failedCall, false)
            }

            override fun onResponse(completedCall: Call, response: Response) {
                var success = false
                var tempFile: File? = null
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful) {
                            throw IOException(
                                "Article server returned HTTP "
                                        + closeableResponse.code
                            )
                        }
                        val body = closeableResponse.body
                        if (body == null) {
                            throw IOException("Article response had no body")
                        }
                        val contentType = body.contentType()
                        if (!isHtml(contentType)) {
                            throw IOException("Article response was not HTML")
                        }
                        val contentLength = body.contentLength()
                        if (contentLength > Utils.MAX_CACHED_ARTICLE_BYTES) {
                            throw IOException("Article HTML exceeds the 5 MiB cache limit")
                        }

                        val cacheDir = Utils.getArticleCacheDir(appContext)
                        if ((!cacheDir.exists() && !cacheDir.mkdirs()) || !cacheDir.isDirectory()) {
                            throw IOException("Could not create article cache")
                        }
                        tempFile = File.createTempFile(
                            storyId.toString() + "-", TEMP_FILE_SUFFIX, cacheDir
                        )
                        streamBodyToFile(body, tempFile)

                        val outputFile = Utils.getArticleCacheFile(appContext, storyId)
                        synchronized(CACHE_LOCK) {
                            Companion.moveReplacing(tempFile!!, outputFile)
                            outputFile.setLastModified(System.currentTimeMillis())
                            SettingsUtils.saveStringToSharedPreferences(
                                appContext,
                                Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + storyId,
                                articleUrl
                            )
                            SettingsUtils.saveStringToSharedPreferences(
                                appContext,
                                Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + storyId,
                                contentType!!.charset(StandardCharsets.UTF_8)!!.name()
                            )
                            cleanupCache(cacheDir, outputFile)
                        }
                        tempFile = null
                        success = true
                    }
                } catch (ignored: IOException) {
                    // A failed article snapshot is reported to the batch controller below.
                } finally {
                    if (tempFile != null) {
                        tempFile!!.delete()
                    }
                    deliverResult(callback, completedCall, success)
                }
            }
        })
        return call
    }

    private fun cleanupCache(
        cacheDir: File,
        protectedFile: File
    ) {
        val files = cacheDir.listFiles()
        if (files == null) {
            return
        }

        val now = System.currentTimeMillis()
        var totalBytes = 0L
        val cachedArticles: MutableList<File> = ArrayList<File>()
        for (file in files) {
            if (!file.isFile()) {
                continue
            }
            val name = file.getName()
            if (name.endsWith(TEMP_FILE_SUFFIX)) {
                val age = max(0L, now - file.lastModified())
                if (age > MAX_TEMP_FILE_AGE_MS) {
                    file.delete()
                }
                continue
            }
            if (!name.endsWith(HTML_FILE_SUFFIX)) {
                continue
            }
            if (file.length() <= 0L || file.length() > Utils.MAX_CACHED_ARTICLE_BYTES) {
                val storyId: Int = getStoryId(file)
                if (storyId > 0) {
                    Utils.deleteCachedArticleSnapshot(appContext, storyId)
                } else {
                    file.delete()
                }
                continue
            }
            cachedArticles.add(file)
            totalBytes += file.length()
        }

        cachedArticles.sortBy { it.lastModified() }
        for (file in cachedArticles) {
            if (totalBytes <= MAX_ARTICLE_CACHE_BYTES) {
                break
            }
            if (file == protectedFile) {
                continue
            }
            val length = file.length()
            val storyId: Int = getStoryId(file)
            if (storyId > 0) {
                Utils.deleteCachedArticleSnapshot(appContext, storyId)
                if (!file.exists()) {
                    totalBytes -= length
                }
            }
        }
    }

    private fun deliverResult(
        callback: DownloadCallback,
        call: Call,
        success: Boolean
    ) {
        mainHandler.post(Runnable { callback.onComplete(call, success) })
    }

    fun interface DownloadCallback {
        fun onComplete(call: Call, success: Boolean)
    }

    companion object {
        private val MAX_ARTICLE_CACHE_BYTES = 200L * 1024L * 1024L
        private val MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1)
        private val BUFFER_SIZE_BYTES = 16 * 1024
        private const val HTML_FILE_SUFFIX = ".html"
        private const val TEMP_FILE_SUFFIX = ".download"
        private val CACHE_LOCK = Any()

        private fun isHtml(contentType: MediaType?): Boolean {
            if (contentType == null) {
                return false
            }
            val type = contentType.type.lowercase()
            val subtype = contentType.subtype.lowercase()
            return ("text" == type && "html" == subtype)
                    || ("application" == type && "xhtml+xml" == subtype)
        }

        @Throws(IOException::class)
        private fun streamBodyToFile(
            body: ResponseBody,
            outputFile: File
        ) {
            var downloadedBytes = 0L
            BufferedInputStream(body.byteStream()).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    var bytesRead: Int
                    while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                        downloadedBytes += bytesRead.toLong()
                        if (downloadedBytes > Utils.MAX_CACHED_ARTICLE_BYTES) {
                            throw IOException("Article HTML exceeds the 5 MiB cache limit")
                        }
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    if (downloadedBytes == 0L) {
                        throw IOException("Article response was empty")
                    }
                    outputStream.getFD().sync()
                }
            }
        }

        private fun getStoryId(file: File): Int {
            val name = file.getName()
            if (!name.endsWith(HTML_FILE_SUFFIX)) {
                return -1
            }
            try {
                return name.substring(0, name.length - HTML_FILE_SUFFIX.length).toInt()
            } catch (e: NumberFormatException) {
                return -1
            }
        }

        @Throws(IOException::class)
        private fun moveReplacing(
            source: File,
            target: File
        ) {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }
}
