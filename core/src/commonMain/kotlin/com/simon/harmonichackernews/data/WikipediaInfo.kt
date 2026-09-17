package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable data class WikipediaInfo(
    val summary: String? = null,
    val title: String? = null,
)
