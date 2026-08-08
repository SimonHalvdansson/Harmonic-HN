package com.simon.harmonichackernews.network

internal object NetworkErrorUtils {
    fun describe(error: NetworkError?): String {
        error ?: return "unknown NetworkError"
        val status = error.networkResponse?.let { "statusCode=${it.statusCode}" }
            ?: "noNetworkResponse"
        return "NetworkError, $status, message=${error.message}"
    }
}
