package com.simon.harmonichackernews.network

object NetworkErrorUtils {
    fun describe(error: NetworkError?): String {
        error ?: return "unknown NetworkError"
        val status = error.networkResponse?.let { "statusCode=${it.statusCode}" }
            ?: "noNetworkResponse"
        return "NetworkError, $status, message=${error.message}"
    }

    fun isRateLimitedText(vararg values: String?): Boolean = values.any { value ->
        value?.let { "429" in it || "too many requests" in it.lowercase() } == true
    }
}
