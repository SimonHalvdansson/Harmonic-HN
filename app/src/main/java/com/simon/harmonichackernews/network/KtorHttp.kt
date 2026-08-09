package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse as KtorResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.appendPathSegments
import io.ktor.http.charset
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI

class NetworkUrl private constructor(internal val value: Url) {
    val scheme: String get() = value.protocol.name
    val host: String get() = value.host
    val encodedPath: String get() = value.encodedPath
    val pathSegments: List<String> get() = value.segments
    val pathSize: Int get() = pathSegments.size
    val fragment: String get() = value.fragment

    fun queryParameter(name: String): String? = value.parameters[name]

    fun newBuilder(): Builder = Builder(URLBuilder(value))

    fun resolve(relativeUrl: String): NetworkUrl? = try {
        parse(URI(toString()).resolve(relativeUrl).toString())
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun toString(): String = value.toString()

    class Builder internal constructor(private val delegate: URLBuilder) {
        fun addQueryParameter(name: String, value: String?): Builder = apply {
            delegate.parameters.append(name, value.orEmpty())
        }

        fun setQueryParameter(name: String, value: String?): Builder = apply {
            delegate.parameters.remove(name)
            delegate.parameters.append(name, value.orEmpty())
        }

        fun addPathSegment(segment: String): Builder = apply {
            delegate.appendPathSegments(segment)
        }

        fun build(): NetworkUrl = NetworkUrl(delegate.build())
    }

    companion object {
        fun parse(value: String): NetworkUrl = NetworkUrl(Url(value))
        fun parseOrNull(value: String?): NetworkUrl? = try {
            value?.let(::parse)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

fun String.toNetworkUrl(): NetworkUrl = NetworkUrl.parse(this)
fun String.toNetworkUrlOrNull(): NetworkUrl? = NetworkUrl.parseOrNull(this)

data class HttpMediaType(private val value: String) {
    val type: String get() = ContentType.parse(value).contentType
    val subtype: String get() = ContentType.parse(value).contentSubtype

    fun charset(default: java.nio.charset.Charset): java.nio.charset.Charset =
        try {
            ContentType.parse(value).charset() ?: default
        } catch (_: IllegalArgumentException) {
            default
        }

    override fun toString(): String = value
}

fun String.toHttpMediaType(): HttpMediaType = HttpMediaType(this)

open class HttpRequestBody internal constructor(
    internal val bytes: ByteArray,
    internal val mediaType: HttpMediaType?,
)

fun String.toHttpRequestBody(mediaType: HttpMediaType? = null): HttpRequestBody =
    HttpRequestBody(encodeToByteArray(), mediaType)

class FormRequestBody private constructor(bytes: ByteArray) :
    HttpRequestBody(bytes, HttpMediaType("application/x-www-form-urlencoded")) {
    class Builder {
        private val parameters = ParametersBuilder()

        fun add(name: String, value: String): Builder = apply {
            parameters.append(name, value)
        }

        fun build(): FormRequestBody =
            FormRequestBody(parameters.build().formUrlEncode().encodeToByteArray())
    }
}

class HttpRequest private constructor(
    val url: NetworkUrl,
    internal val method: HttpMethod,
    internal val headers: Map<String, String>,
    internal val body: HttpRequestBody?,
) {
    class Builder {
        private var url: NetworkUrl? = null
        private var method: HttpMethod = HttpMethod.Get
        private var body: HttpRequestBody? = null
        private val headers = linkedMapOf<String, String>()

        fun url(url: String): Builder = apply { this.url = url.toNetworkUrl() }
        fun url(url: NetworkUrl): Builder = apply { this.url = url }
        fun header(name: String, value: String): Builder = apply { headers[name] = value }
        fun get(): Builder = apply {
            method = HttpMethod.Get
            body = null
        }

        fun post(body: HttpRequestBody): Builder = apply {
            method = HttpMethod.Post
            this.body = body
        }

        fun build(): HttpRequest = HttpRequest(
            url = requireNotNull(url) { "A request URL is required" },
            method = method,
            headers = headers.toMap(),
            body = body,
        )
    }
}

interface HttpCallback {
    fun onFailure(call: HttpCall, error: IOException)
    fun onResponse(call: HttpCall, response: HttpResponse)
}

class HttpCall internal constructor(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val request: HttpRequest,
    private val readTimeoutMillis: Long,
) {
    @Volatile
    private var job: Job? = null

    @Volatile
    private var canceled = false

    fun enqueue(callback: HttpCallback) {
        if (canceled) return
        val launchedJob = scope.launch {
            try {
                callback.onResponse(this@HttpCall, await())
            } catch (_: CancellationException) {
                // Cancellation is a caller decision and intentionally has no failure callback.
            } catch (error: Throwable) {
                callback.onFailure(
                    this@HttpCall,
                    error as? IOException ?: IOException(error.message, error),
                )
            }
        }
        job = launchedJob
        if (canceled) launchedJob.cancel()
    }

    @Throws(IOException::class)
    fun execute(): HttpResponse = try {
        runBlocking { await() }
    } catch (error: Throwable) {
        throw error as? IOException ?: IOException(error.message, error)
    }

    /** Primary non-blocking execution path. Blocking and callback APIs are compatibility adapters. */
    @Throws(IOException::class)
    suspend fun await(): HttpResponse = try {
        executeInternal()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw error as? IOException ?: IOException(error.message, error)
    }

    fun cancel() {
        canceled = true
        job?.cancel()
    }

    fun isCanceled(): Boolean = canceled || job?.isCancelled == true

    private suspend fun executeInternal(): HttpResponse {
        val response = client.request(request.url.toString()) {
            method = request.method
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = readTimeoutMillis
                connectTimeoutMillis = minOf(readTimeoutMillis, DEFAULT_CONNECT_TIMEOUT_MILLIS)
            }
            request.headers.forEach { (name, value) -> header(name, value) }
            request.body?.let { body ->
                body.mediaType?.let { contentType(ContentType.parse(it.toString())) }
                setBody(body.bytes)
            }
        }
        return HttpResponse(
            response,
            HttpResponseBody(response.bodyAsChannel(), response.headers),
        )
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 30_000L
    }
}

class KtorHttpClient internal constructor(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
) {
    fun newCall(request: HttpRequest): HttpCall =
        HttpCall(client, scope, request, readTimeoutMillis)

    suspend fun execute(request: HttpRequest): HttpResponse = newCall(request).await()

    fun newBuilder(): Builder = Builder(client, scope, readTimeoutMillis)

    class Builder internal constructor(
        private val client: HttpClient,
        private val scope: CoroutineScope,
        private var readTimeoutMillis: Long,
    ) {
        fun readTimeoutMillis(timeoutMillis: Long): Builder = apply {
            readTimeoutMillis = timeoutMillis
        }

        fun build(): KtorHttpClient = KtorHttpClient(client, scope, readTimeoutMillis)
    }

    companion object {
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000L
    }
}

class HttpResponse internal constructor(
    private val delegate: KtorResponse,
    responseBody: HttpResponseBody,
) : AutoCloseable {
    val code: Int get() = delegate.status.value
    val message: String get() = delegate.status.description
    val isSuccessful: Boolean get() = code in 200..299
    val requestUrl: NetworkUrl get() = NetworkUrl.parse(delegate.call.request.url.toString())
    var body: HttpResponseBody = responseBody
        private set

    fun header(name: String, defaultValue: String? = null): String? =
        delegate.headers[name] ?: defaultValue

    @Throws(IOException::class)
    fun peekBody(maxBytes: Long): HttpResponseBody {
        require(maxBytes in 0..Int.MAX_VALUE.toLong()) { "Invalid body preview limit" }
        val originalBody = body
        val bytes = originalBody.readAtMost(maxBytes.toInt() + 1)
        if (bytes.size > maxBytes) {
            throw IOException("Response body exceeded the preview limit")
        }
        body = HttpResponseBody(ByteReadChannel(bytes), delegate.headers)
        return HttpResponseBody(ByteReadChannel(bytes), delegate.headers)
    }

    override fun close() {
        body.close()
    }

    override fun toString(): String = "HTTP $code $message"
}

class HttpResponseBody internal constructor(
    private val channel: ByteReadChannel,
    private val headers: io.ktor.http.Headers,
) : AutoCloseable {
    private val source = HttpBodySource(channel)

    fun contentLength(): Long = headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L

    fun contentType(): HttpMediaType? = headers[HttpHeaders.ContentType]?.let(::HttpMediaType)

    @Throws(IOException::class)
    fun string(): String = runBlocking { readText() }

    suspend fun readText(): String = channel.readRemaining().readText()

    @Throws(IOException::class)
    fun bytes(): ByteArray = runBlocking { readBytes() }

    suspend fun readBytes(): ByteArray = channel.readRemaining().readByteArray()

    fun source(): HttpBodySource = source

    internal fun readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(maxBytes.coerceAtMost(8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (output.size() < maxBytes) {
            val read = source.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    override fun close() {
        channel.cancel()
    }
}

class HttpBodySource internal constructor(private val channel: ByteReadChannel) {
    @Throws(IOException::class)
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int = runBlocking {
        channel.readAvailable(buffer, offset, length)
    }

    @Throws(IOException::class)
    fun readUtf8Line(): String? = runBlocking { channel.readUTF8Line() }
}
