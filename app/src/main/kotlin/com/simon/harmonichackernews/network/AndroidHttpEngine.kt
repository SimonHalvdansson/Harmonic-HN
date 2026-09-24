package com.simon.harmonichackernews.network

import io.ktor.client.engine.okhttp.OkHttp

/** Android's TLS implementation and pooled HTTP/2 connections, behind the shared Ktor API. */
internal fun createAndroidHttpEngine() = OkHttp.create {
    config {
        // HN actions own retry/reconciliation after an ambiguous write failure. Do not let the
        // transport silently replay a POST. Redirect handling remains owned by Ktor as well.
        retryOnConnectionFailure(false)
    }
}
