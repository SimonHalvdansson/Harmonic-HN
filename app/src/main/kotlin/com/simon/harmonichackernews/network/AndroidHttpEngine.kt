package com.simon.harmonichackernews.network

import io.ktor.client.engine.okhttp.OkHttp

/** Android's TLS implementation and pooled HTTP/2 connections, behind the shared Ktor API. */
internal fun createAndroidHttpEngine(readOnly: Boolean = false) = OkHttp.create {
    config {
        // Feed reads must recover from stale pooled connections after backgrounding. HN actions
        // (including GET-based votes) own reconciliation and must never be silently replayed.
        // Redirect handling remains owned by Ktor for both transports.
        retryOnConnectionFailure(readOnly)
    }
}
