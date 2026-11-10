package com.simon.harmonichackernews.platform

/** Hosts may require user permission before contacting a configured AI server. */
fun interface CloudEndpointAccess {
    suspend fun ensureAccess(baseUrl: String)

    companion object {
        val Unrestricted = CloudEndpointAccess { }
    }
}
