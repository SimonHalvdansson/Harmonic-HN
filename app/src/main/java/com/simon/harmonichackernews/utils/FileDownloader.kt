package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.network.CachedDownloadService
import com.simon.harmonichackernews.network.DownloadCachePolicy
import com.simon.harmonichackernews.network.KtorTransferClient
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android cache-path and callback adapter for the shared suspend-first download service. */
class FileDownloader(ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDirectory: File? = (ctx.externalCacheDir ?: ctx.cacheDir)?.let {
        File(it, CACHE_DIR_NAME)
    }
    private val service = CachedDownloadService(
        client = KtorTransferClient(AndroidAppComposition.get(ctx).network.httpClient),
        store = AndroidDownloadStore(
            root = cacheDirectory,
            fileNameForKey = { url -> sha256Hex(url) + CACHE_FILE_SUFFIX },
            targetSuffix = CACHE_FILE_SUFFIX,
        ),
        policy = DownloadCachePolicy(
            maxFileBytes = MAX_CACHE_SIZE_BYTES,
            maxCacheBytes = MAX_CACHE_SIZE_BYTES,
            maxFileAgeMillis = MAX_CACHE_FILE_AGE_MS,
            maxTemporaryAgeMillis = MAX_TEMP_FILE_AGE_MS,
        ),
        cacheLabel = "PDF",
    )

    fun downloadFile(
        url: String?,
        mimeType: String,
        callback: FileDownloaderCallback,
    ): Job = scope.launch {
        try {
            val target = url?.takeIf(String::isNotBlank)
                ?: throw IOException("PDF cache is unavailable")
            val downloaded = service.download(
                url = target,
                accept = mimeType,
                nowMillis = System.currentTimeMillis(),
            )
            withContext(Dispatchers.Main.immediate) {
                callback.onSuccess(downloaded.reference)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            withContext(Dispatchers.Main.immediate) {
                callback.onFailure(error as? IOException ?: IOException(error.message, error))
            }
        }
    }

    interface FileDownloaderCallback {
        fun onFailure(error: IOException?)
        fun onSuccess(filePath: String?)
    }

    private companion object {
        const val CACHE_DIR_NAME = "pdf_cache"
        const val CACHE_FILE_SUFFIX = ".pdf"
        val MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L
        val MAX_CACHE_FILE_AGE_MS = TimeUnit.DAYS.toMillis(30)
        val MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1)

        fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
