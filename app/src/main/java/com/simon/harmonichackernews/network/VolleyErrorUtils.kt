package com.simon.harmonichackernews.network

import com.android.volley.VolleyError

internal object VolleyErrorUtils {
    fun describe(error: VolleyError?): String {
        error ?: return "unknown VolleyError"
        val status = error.networkResponse?.let { "statusCode=${it.statusCode}" }
            ?: "noNetworkResponse"
        return "${error.javaClass.simpleName}, $status, message=${error.message}"
    }
}
