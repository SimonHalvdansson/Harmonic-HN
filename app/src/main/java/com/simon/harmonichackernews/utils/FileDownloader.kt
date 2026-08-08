package com.simon.harmonichackernews.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.annotation.NonNull
import com.simon.harmonichackernews.network.HttpCall
import com.simon.harmonichackernews.network.HttpCallback
import com.simon.harmonichackernews.network.HttpRequest
import com.simon.harmonichackernews.network.HttpResponse
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.NetworkComponent.httpClientInstance
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Comparator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.function.ToLongFunction
import kotlin.math.max

class FileDownloader(ctx: Context) {
    private val mCacheDir: File?
    private val mMainHandler: Handler

    init {
        val externalCacheDir = ctx.getExternalCacheDir()
        val baseCacheDir = if (externalCacheDir == null) ctx.getCacheDir() else externalCacheDir
        mCacheDir = if (baseCacheDir == null) null else File(baseCacheDir, CACHE_DIR_NAME)
        mMainHandler = Handler(Looper.getMainLooper())
    }

    /**
     * Enqueues an asynchronous download for the provided URL. This method may be
     * called from the main thread.
     */
    fun downloadFile(url: String?, mimeType: String, callback: FileDownloaderCallback) {
        if (TextUtils.isEmpty(url) || mCacheDir == null) {
            deliverFailure(callback, null, IOException("PDF cache is unavailable"))
            return
        }

        CACHE_EXECUTOR.execute(Runnable { prepareDownload(url!!, mimeType, callback) })
    }

    private fun prepareDownload(
        url: String,
        mimeType: String,
        callback: FileDownloaderCallback
    ) {
        if ((!mCacheDir!!.exists() && !mCacheDir.mkdirs()) || !mCacheDir.isDirectory()) {
            deliverFailure(callback, null, IOException("Could not create PDF cache"))
            return
        }

        val outputFile: File
        val tempFile: File
        try {
            outputFile = File(mCacheDir, sha256Hex(url) + CACHE_FILE_SUFFIX)
            cleanupCache(if (outputFile.isFile() && outputFile.length() > 0L) outputFile else null)
            if (outputFile.isFile() && outputFile.length() > 0L) {
                outputFile.setLastModified(System.currentTimeMillis())
                deliverSuccess(callback, outputFile)
                return
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
            tempFile = File.createTempFile(sha256Hex(url) + "-", TEMP_FILE_SUFFIX, mCacheDir)
        } catch (e: IOException) {
            deliverFailure(callback, null, e)
            return
        }

        val request: HttpRequest
        try {
            request = HttpRequest.Builder().url(url)
                .header("Accept", mimeType)
                .build()
        } catch (e: IllegalArgumentException) {
            tempFile.delete()
            deliverFailure(callback, null, IOException("Invalid PDF URL", e))
            return
        }

        httpClientInstance.newCall(request).enqueue(object : HttpCallback {
            override fun onFailure(call: HttpCall, e: IOException) {
                tempFile.delete()
                deliverFailure(callback, call, e)
            }

            override fun onResponse(call: HttpCall, response: HttpResponse) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful) {
                            throw IOException(
                                "PDF server returned HTTP "
                                        + closeableResponse.code
                            )
                        }
                        val body = closeableResponse.body
                        if (body == null) {
                            throw IOException("PDF response had no body")
                        }
                        if (body.contentLength() > MAX_CACHE_SIZE_BYTES) {
                            throw IOException("PDF is larger than the 250 MiB viewer limit")
                        }
                        FileOutputStream(tempFile).use { output ->
                            val source = body.source()
                            val buffer = ByteArray(8 * 1024)
                            var downloadedBytes = 0L
                            while (true) {
                                val bytesRead = source.read(buffer)
                                if (bytesRead == -1) break
                                downloadedBytes += bytesRead
                                if (downloadedBytes > MAX_CACHE_SIZE_BYTES) {
                                    throw IOException(
                                        "PDF is larger than the 250 MiB viewer limit"
                                    )
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                        if (tempFile.length() <= 0L) {
                            throw IOException("Downloaded PDF was empty")
                        }
                        CACHE_EXECUTOR.execute(
                            Runnable { finishDownload(call, tempFile, outputFile, callback) })
                    }
                } catch (e: IOException) {
                    tempFile.delete()
                    deliverFailure(callback, call, e)
                }
            }
        })
    }

    private fun finishDownload(
        call: HttpCall?,
        tempFile: File,
        outputFile: File,
        callback: FileDownloaderCallback
    ) {
        try {
            moveReplacing(tempFile, outputFile)
            outputFile.setLastModified(System.currentTimeMillis())
            cleanupCache(outputFile)
            deliverSuccess(callback, outputFile)
        } catch (e: IOException) {
            tempFile.delete()
            deliverFailure(callback, call, e)
        }
    }

    private fun cleanupCache(protectedFile: File?) {
        val files = mCacheDir!!.listFiles()
        if (files == null) {
            return
        }

        val now = System.currentTimeMillis()
        var totalBytes = 0L
        val cachedPdfs: MutableList<File> = ArrayList<File>()
        for (file in files) {
            if (!file.isFile()) {
                continue
            }
            val name = file.getName()
            val age = max(0L, now - file.lastModified())
            if (name.endsWith(TEMP_FILE_SUFFIX)) {
                if (age > MAX_TEMP_FILE_AGE_MS) {
                    file.delete()
                }
                continue
            }
            if (!name.endsWith(CACHE_FILE_SUFFIX)) {
                continue
            }
            if (file != protectedFile && age > MAX_CACHE_FILE_AGE_MS) {
                if (file.delete()) {
                    continue
                }
            }
            cachedPdfs.add(file)
            totalBytes += file.length()
        }

        cachedPdfs.sortBy { it.lastModified() }
        for (file in cachedPdfs) {
            if (totalBytes <= MAX_CACHE_SIZE_BYTES) {
                break
            }
            if (file == protectedFile) {
                continue
            }
            val length = file.length()
            if (file.delete()) {
                totalBytes -= length
            }
        }
    }

    private fun deliverSuccess(callback: FileDownloaderCallback, file: File) {
        mMainHandler.post(Runnable { callback.onSuccess(file.getPath()) })
    }

    private fun deliverFailure(
        callback: FileDownloaderCallback,
        call: HttpCall?,
        exception: IOException?
    ) {
        mMainHandler.post(Runnable { callback.onFailure(call, exception) })
    }

    interface FileDownloaderCallback {
        fun onFailure(call: HttpCall?, e: IOException?)
        fun onSuccess(filePath: String?)
    }

    companion object {
        private const val CACHE_DIR_NAME = "pdf_cache"
        private const val CACHE_FILE_SUFFIX = ".pdf"
        private const val TEMP_FILE_SUFFIX = ".download"
        private val MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L
        private val MAX_CACHE_FILE_AGE_MS = TimeUnit.DAYS.toMillis(30)
        private val MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1)
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
        private val CACHE_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor(
            ThreadFactory { runnable: Runnable? ->
                val thread = Thread(runnable, "harmonic-pdf-cache")
                thread.setDaemon(true)
                thread
            })

        @Throws(IOException::class)
        private fun moveReplacing(source: File, target: File) {
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

        @Throws(IOException::class)
        private fun sha256Hex(value: String): String {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
                val hex = CharArray(bytes.size * 2)
                for (i in bytes.indices) {
                    val unsignedByte = bytes[i].toInt() and 0xff
                    hex[i * 2] = HEX_DIGITS[unsignedByte ushr 4]
                    hex[i * 2 + 1] = HEX_DIGITS[unsignedByte and 0x0f]
                }
                return String(hex)
            } catch (e: NoSuchAlgorithmException) {
                throw IOException("SHA-256 is unavailable", e)
            }
        }
    }
}
