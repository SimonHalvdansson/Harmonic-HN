package com.simon.harmonichackernews.network

import com.android.volley.VolleyError

internal object VolleyErrorUtils {
    fun describe(error: VolleyError?): String {
        if (error == null) {
            return "unknown VolleyError"
        }
        val status = if (error.networkResponse == null)
            "noNetworkResponse"
        else
            "statusCode=" + error.networkResponse.statusCode
        return error.javaClass.getSimpleName() + ", " + status + ", message=" + error.message
    }
}
