package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

/** Small callback request API retained while callers move from callbacks to suspending functions. */
object QueueRequest {
    object Method {
        const val GET = 0
        const val POST = 1
    }
}

object QueueResponse {
    fun interface Listener<T> {
        fun onResponse(response: T)
    }

    fun interface ErrorListener {
        fun onErrorResponse(error: NetworkError?)
    }
}

data class NetworkResponse(
    val statusCode: Int,
    val body: String? = null,
)

open class NetworkError(
    message: String? = null,
    cause: Throwable? = null,
    val networkResponse: NetworkResponse? = null,
) : Exception(message, cause)

class NetworkTimeoutError(cause: Throwable? = null) : NetworkError(
    message = cause?.message ?: "Request timed out",
    cause = cause,
)

data class RetryPolicy(
    val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val backoffMultiplier: Float = DEFAULT_BACKOFF_MULT,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000
        const val DEFAULT_MAX_RETRIES = 1
        const val DEFAULT_BACKOFF_MULT = 1f
    }
}

typealias DefaultRetryPolicy = RetryPolicy

abstract class QueuedRequest<T>(
    val method: Int,
    val url: String,
    private val successListener: QueueResponse.Listener<T>,
    private val errorListener: QueueResponse.ErrorListener,
) {
    var retryPolicy: RetryPolicy = RetryPolicy()
    var tag: Any? = null
    private var shouldCache: Boolean = true
    private var job: Job? = null
    private var canceled = false

    open fun getHeaders(): MutableMap<String, String> = mutableMapOf()

    open fun getParams(): MutableMap<String, String> = mutableMapOf()

    internal abstract fun parse(body: String): T

    internal fun deliverSuccess(body: String) = successListener.onResponse(parse(body))

    internal fun deliverError(error: NetworkError?) = errorListener.onErrorResponse(error)

    internal fun canUseCache(): Boolean = shouldCache

    fun setShouldCache(shouldCache: Boolean) {
        this.shouldCache = shouldCache
    }

    fun setRetryPolicy(retryPolicy: RetryPolicy): QueuedRequest<T> = apply {
        this.retryPolicy = retryPolicy
    }

    fun cancel() {
        canceled = true
        job?.cancel()
    }

    internal fun bind(job: Job) {
        this.job = job
        if (canceled) job.cancel()
    }
}

open class StringRequest(
    method: Int,
    url: String,
    listener: QueueResponse.Listener<String?>,
    errorListener: QueueResponse.ErrorListener,
) : QueuedRequest<String?>(method, url, listener, errorListener) {
    override fun parse(body: String): String = body
}

open class JsonObjectRequest(
    method: Int,
    url: String,
    private val requestBody: JSONObject?,
    listener: QueueResponse.Listener<JSONObject?>,
    errorListener: QueueResponse.ErrorListener,
) : QueuedRequest<JSONObject?>(method, url, listener, errorListener) {
    override fun parse(body: String): JSONObject = JSONObject(body)

    internal fun jsonBody(): String? = requestBody?.toString()
}

class RequestQueue internal constructor(
    private val client: HttpClient,
    private val workerScope: CoroutineScope,
    private val callbackDispatcher: CoroutineDispatcher,
) {
    private val taggedJobs = mutableMapOf<Any, MutableSet<Job>>()

    /** Suspend-first GET path. [add] remains as a compatibility adapter for callback callers. */
    suspend fun getString(
        url: String,
        retryPolicy: RetryPolicy = RetryPolicy(),
        shouldCache: Boolean = true,
    ): String {
        val request = StringRequest(
            QueueRequest.Method.GET,
            url,
            QueueResponse.Listener {},
            QueueResponse.ErrorListener {},
        ).apply {
            setRetryPolicy(retryPolicy)
            setShouldCache(shouldCache)
        }
        return runRequest(request).getOrElse { throw it.asNetworkError() }
    }

    fun <T> add(request: QueuedRequest<T>): QueuedRequest<T> {
        val job = workerScope.launch {
            val result = runRequest(request)
            val requestJob = coroutineContext[Job]
            withContext(callbackDispatcher) {
                unregister(request.tag, requestJob)
                result.fold(
                    onSuccess = request::deliverSuccess,
                    onFailure = { throwable ->
                        if (throwable !is CancellationException) {
                            request.deliverError(throwable.asNetworkError())
                        }
                    },
                )
            }
        }
        request.bind(job)
        request.tag?.let { register(it, job) }
        return request
    }

    fun cancelAll(tag: Any?) {
        if (tag == null) return
        taggedJobs.remove(tag)?.forEach(Job::cancel)
    }

    private suspend fun runRequest(request: QueuedRequest<*>): Result<String> {
        val policy = request.retryPolicy
        var attempt = 0
        var timeoutMillis = policy.timeoutMillis.toLong()
        while (true) {
            try {
                val response = client.request(request.url) {
                    method = when (request.method) {
                        QueueRequest.Method.POST -> HttpMethod.Post
                        else -> HttpMethod.Get
                    }
                    timeout { requestTimeoutMillis = timeoutMillis }
                    request.getHeaders().forEach { (name, value) -> header(name, value) }
                    if (!request.canUseCache()) {
                        header(HttpHeaders.CacheControl, "no-cache, no-store")
                    }
                    when {
                        request is JsonObjectRequest && request.jsonBody() != null -> {
                            contentType(ContentType.Application.Json)
                            setBody(request.jsonBody().orEmpty())
                        }
                        request.method == QueueRequest.Method.POST -> {
                            contentType(ContentType.Application.FormUrlEncoded)
                            setBody(parameters {
                                request.getParams().forEach { (name, value) -> append(name, value) }
                            }.formUrlEncode())
                        }
                    }
                }
                val body = response.bodyAsText()
                if (response.status.value !in 200..299) {
                    throw NetworkError(
                        message = "HTTP ${response.status.value}",
                        networkResponse = NetworkResponse(response.status.value, body),
                    )
                }
                return Result.success(body)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (attempt >= policy.maxRetries || !error.isRetryable()) {
                    return Result.failure(error)
                }
                attempt++
                timeoutMillis += (timeoutMillis * policy.backoffMultiplier).toLong()
            }
        }
    }

    private fun register(tag: Any, job: Job) {
        taggedJobs.getOrPut(tag) { mutableSetOf() }.add(job)
    }

    private fun unregister(tag: Any?, job: Job?) {
        if (tag == null || job == null) return
        taggedJobs[tag]?.let { jobs ->
            jobs.remove(job)
            if (jobs.isEmpty()) taggedJobs.remove(tag)
        }
    }
}

private fun Throwable.isRetryable(): Boolean =
    this is HttpRequestTimeoutException

private fun Throwable.asNetworkError(): NetworkError = when (this) {
    is NetworkError -> this
    is HttpRequestTimeoutException -> NetworkTimeoutError(this)
    else -> NetworkError(message = message, cause = this)
}
