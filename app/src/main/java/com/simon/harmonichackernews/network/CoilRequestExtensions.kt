package com.simon.harmonichackernews.network

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** Adds one HTTP header without exposing a platform-specific networking client to callers. */
fun ImageRequest.Builder.networkHeader(name: String, value: String): ImageRequest.Builder =
    httpHeaders(
        NetworkHeaders.Builder()
            .set(name, value)
            .build(),
    )
