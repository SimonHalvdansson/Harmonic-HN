package com.simon.harmonichackernews

import android.graphics.ImageDecoder
import android.os.Build
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageResult

/** Re-download an incomplete HTTP image once instead of reusing its truncated disk entry. */
internal class IncompleteImageRetryInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (Build.VERSION.SDK_INT < 28 || result !is ErrorResult) return result
        val error = result.throwable as? ImageDecoder.DecodeException ?: return result
        if (error.error != ImageDecoder.DecodeException.SOURCE_INCOMPLETE) return result

        val request = chain.request
        val url = request.data.toString()
        if ((!url.startsWith("https://") && !url.startsWith("http://")) ||
            !request.networkCachePolicy.readEnabled
        ) return result

        // Keep the caller's write permissions. A successful fetch replaces this one entry;
        // unrelated images and offline-only requests are left alone. Proceeding down the
        // existing chain also bounds recovery to one retry, even if the server truncates again.
        return chain.withRequest(
            request.newBuilder()
                .diskCachePolicy(request.diskCachePolicy.withoutReads())
                .memoryCachePolicy(request.memoryCachePolicy.withoutReads())
                .build(),
        ).proceed()
    }
}

private fun CachePolicy.withoutReads(): CachePolicy =
    if (writeEnabled) CachePolicy.WRITE_ONLY else CachePolicy.DISABLED
