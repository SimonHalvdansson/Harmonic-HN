package com.simon.harmonichackernews

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.annotation.InternalCoilApi
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.network.CacheNetworkResponse
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowPartialImage
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
@OptIn(ExperimentalCoilApi::class, InternalCoilApi::class)
class IncompleteImageRetryTest {
    @Test
    fun truncatedCachedPngIsReplacedAndThenLoadsFromDisk() = runBlocking(Dispatchers.IO) {
        fixture { cache, client, fullPng ->
            seed(cache, fullPng.copyOf(fullPng.size * 2 / 3), fullPng.size)
            val loader = loader(cache, client)
            try {
                val result = loader.execute(request())
                assertTrue(result.toString(), result is SuccessResult)
                assertEquals(1, client.requests)
                assertEquals(Color.RED, (result as SuccessResult).image.toBitmap().getPixel(20, 63))
                val cached = loader.execute(request()) as SuccessResult
                assertEquals(DataSource.DISK, cached.dataSource)
                assertEquals(1, client.requests)
                assertEquals(Color.RED, cached.image.toBitmap().getPixel(20, 63))
            } finally {
                loader.shutdown()
            }
        }
    }

    @Test
    fun repeatedTruncatedNetworkResponseFailsAfterOneRetry() = runBlocking(Dispatchers.IO) {
        fixture { cache, client, fullPng ->
            client.bytes = fullPng.copyOf(fullPng.size * 2 / 3)
            val loader = loader(cache, client)
            try {
                assertTrue(loader.execute(request()) is ErrorResult)
                assertEquals(2, client.requests)
            } finally {
                loader.shutdown()
            }
        }
    }

    @Test
    fun completeCachedPngDoesNotFetchAgain() = runBlocking(Dispatchers.IO) {
        fixture { cache, client, fullPng ->
            seed(cache, fullPng, fullPng.size)
            val loader = loader(cache, client)
            try {
                assertEquals(DataSource.DISK, (loader.execute(request()) as SuccessResult).dataSource)
                assertEquals(0, client.requests)
            } finally {
                loader.shutdown()
            }
        }
    }

    @Test
    fun offlineOnlyRequestDoesNotRetryIncompleteImage() = runBlocking(Dispatchers.IO) {
        fixture { cache, client, fullPng ->
            seed(cache, fullPng.copyOf(fullPng.size * 2 / 3), fullPng.size)
            val loader = loader(cache, client)
            try {
                val request = request().newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
                assertTrue(loader.execute(request) is ErrorResult)
                assertEquals(0, client.requests)
            } finally {
                loader.shutdown()
            }
        }
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun request() = ImageRequest.Builder(context).data(imageUrl).build()

    private fun loader(cache: DiskCache, client: NetworkClient) = ImageLoader.Builder(context)
        .allowHardware(false)
        .allowPartialImage(false)
        .memoryCache(null)
        .diskCache(cache)
        .components {
            add(IncompleteImageRetryInterceptor())
            add(NetworkFetcher.Factory(networkClient = { client }))
        }
        .build()

    private suspend fun fixture(block: suspend (DiskCache, ImageClient, ByteArray) -> Unit) {
        val directory = File.createTempFile("incomplete-image-", "", context.cacheDir).apply {
            delete()
            mkdir()
        }
        val cache = DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(1_048_576).build()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val random = kotlin.random.Random(42)
        for (y in 0 until 64) for (x in 0 until 64) {
            bitmap.setPixel(x, y, if (y == 63) Color.RED else Color.rgb(
                random.nextInt(256), random.nextInt(256), random.nextInt(256),
            ))
        }
        val fullPng = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        bitmap.recycle()
        try {
            block(cache, ImageClient(fullPng), fullPng)
        } finally {
            cache.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun seed(cache: DiskCache, bytes: ByteArray, advertisedSize: Int) {
        val editor = checkNotNull(cache.openEditor(imageUrl))
        cache.fileSystem.write(editor.metadata) {
            CacheNetworkResponse.writeTo(response(bytes, advertisedSize), this)
        }
        cache.fileSystem.write(editor.data) { write(bytes) }
        editor.commit()
    }

    private class ImageClient(var bytes: ByteArray) : NetworkClient {
        private val advertisedSize = bytes.size
        var requests = 0
        override suspend fun <T> executeRequest(
            request: NetworkRequest,
            block: suspend (NetworkResponse) -> T,
        ): T {
            requests++
            return block(response(bytes, advertisedSize))
        }
    }

    companion object {
        private const val imageUrl = "https://image.test/preview.png"
        private fun response(bytes: ByteArray, advertisedSize: Int) = NetworkResponse(
            headers = NetworkHeaders.Builder()
                .set("Content-Type", "image/png")
                .set("Content-Length", advertisedSize.toString())
                .build(),
            body = NetworkResponseBody(Buffer().write(bytes)),
        )
    }
}
