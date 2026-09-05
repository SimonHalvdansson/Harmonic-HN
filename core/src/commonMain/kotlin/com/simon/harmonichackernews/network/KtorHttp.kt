package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
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
import io.ktor.http.takeFrom
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUTF8Line
import kotlinx.io.readByteArray

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
        NetworkUrl(URLBuilder(value).takeFrom(relativeUrl).build())
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun toString(): String = value.toString()

    class Builder internal constructor(private val delegate: URLBuilder) {
        fun host(host: String): Builder = apply {
            delegate.host = host
        }

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

    fun charset(default: Charset): Charset =
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

class KtorHttpClient(
    private val client: suspend () -> HttpClient,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) {
    constructor(
        client: HttpClient,
        readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ) : this({ client }, readTimeoutMillis, requestTimeoutMillis)

    suspend fun execute(request: HttpRequest): HttpResponse {
        val response = client().request(request.url.toString()) {
            method = request.method
            timeout {
                requestTimeoutMillis = this@KtorHttpClient.requestTimeoutMillis
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

    /**
     * Keeps the Ktor response scoped while [block] consumes its channel. Unlike [execute], this
     * bypasses Ktor's saved-body path and therefore does not buffer large downloads in memory.
     */
    suspend fun <T> executeStreaming(
        request: HttpRequest,
        block: suspend (HttpResponse) -> T,
    ): T = client().prepareRequest(request.url.toString()) {
        method = request.method
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = readTimeoutMillis
            connectTimeoutMillis = minOf(readTimeoutMillis, DEFAULT_CONNECT_TIMEOUT_MILLIS)
        }
        request.headers.forEach { (name, value) -> header(name, value) }
        // Ktor's HttpCache consumes a cacheable response into a ByteArray before handing it to
        // the caller. Large file/model transfers must bypass that path to remain streaming.
        header(HttpHeaders.CacheControl, "no-store")
        request.body?.let { body ->
            body.mediaType?.let { contentType(ContentType.parse(it.toString())) }
            setBody(body.bytes)
        }
    }.execute { response ->
        val wrapped = HttpResponse(
            response,
            HttpResponseBody(response.bodyAsChannel(), response.headers),
        )
        try {
            block(wrapped)
        } finally {
            wrapped.close()
        }
    }

    fun newBuilder(): Builder = Builder(client, readTimeoutMillis, requestTimeoutMillis)

    class Builder internal constructor(
        private val client: suspend () -> HttpClient,
        private var readTimeoutMillis: Long,
        private var requestTimeoutMillis: Long,
    ) {
        fun readTimeoutMillis(timeoutMillis: Long): Builder = apply {
            readTimeoutMillis = timeoutMillis
            requestTimeoutMillis = maxOf(requestTimeoutMillis, timeoutMillis)
        }

        fun requestTimeoutMillis(timeoutMillis: Long): Builder = apply {
            requestTimeoutMillis = timeoutMillis
        }

        fun build(): KtorHttpClient = KtorHttpClient(
            client = client,
            readTimeoutMillis = readTimeoutMillis,
            requestTimeoutMillis = requestTimeoutMillis,
        )
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 30_000L
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000L
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 60_000L
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
    val body: HttpResponseBody = responseBody

    fun header(name: String, defaultValue: String? = null): String? =
        delegate.headers[name] ?: defaultValue

    override fun close() {
        body.close()
    }

    override fun toString(): String = "HTTP $code $message"
}

class HttpResponseBody internal constructor(
    private val channel: ByteReadChannel,
    private val headers: io.ktor.http.Headers,
) : AutoCloseable {
    fun contentLength(): Long = headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L

    fun contentType(): HttpMediaType? = headers[HttpHeaders.ContentType]?.let(::HttpMediaType)

    suspend fun readText(maxBytes: Int = DEFAULT_MAX_BUFFERED_BODY_BYTES): String =
        readBytes(maxBytes).decodeToString()

    suspend fun readBytes(maxBytes: Int = DEFAULT_MAX_BUFFERED_BODY_BYTES): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val declaredLength = contentLength()
        if (declaredLength > maxBytes) throw HttpBodyLimitException(maxBytes, declaredLength)
        val bytes = channel.readRemaining(maxBytes.toLong() + 1L).readByteArray()
        if (bytes.size > maxBytes) throw HttpBodyLimitException(maxBytes, bytes.size.toLong())
        return bytes
    }

    suspend fun readAvailable(
        buffer: ByteArray,
        offset: Int = 0,
        length: Int = buffer.size,
    ): Int = channel.readAvailable(buffer, offset, length)

    @Suppress("DEPRECATION")
    suspend fun readUtf8Line(maxChars: Int = DEFAULT_MAX_LINE_CHARS): String? {
        require(maxChars > 0) { "maxChars must be positive" }
        return channel.readUTF8Line(maxChars)
    }

    override fun close() {
        channel.cancel()
    }

    private companion object {
        const val DEFAULT_MAX_LINE_CHARS = 64 * 1024
    }
}

class HttpBodyLimitException(
    val maxBytes: Int,
    val observedBytes: Long,
) : kotlinx.io.IOException("HTTP response exceeds the $maxBytes-byte buffered-body limit")
