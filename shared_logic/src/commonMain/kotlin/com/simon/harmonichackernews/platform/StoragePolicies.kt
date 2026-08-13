package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.StableHash

/** Stable, filesystem-safe addressing shared by all host storage adapters. */
object StorageKeyPolicy {
    const val SHARED_CACHE_DIRECTORY = "shared_cache"
    const val SHARED_FILES_DIRECTORY = "shared_files"
    const val PDF_CACHE_DIRECTORY = "pdf_cache"
    const val HTTP_CACHE_DIRECTORY = "ktor_http_cache"

    fun safeName(value: String): String {
        require(value.isNotBlank()) { "A non-blank storage key is required" }
        return StableHash.sha256Hex(value)
    }
}
