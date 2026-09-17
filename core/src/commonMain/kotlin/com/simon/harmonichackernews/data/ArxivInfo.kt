package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.ArxivResolver
import kotlinx.serialization.Serializable

@Serializable
data class ArxivInfo(
    val arxivAbstract: String? = null,
    val authors: List<String?> = emptyList(),
    val primaryCategory: String? = null,
    val arxivID: String? = null,
    val secondaryCategories: List<String?> = emptyList(),
    val publishedDate: String? = null,
    val htmlUrl: String? = null,
) {
    fun concatNames(): String = authors.joinToString(", ")
    fun formatDate(): String = publishedDate.orEmpty().take(10)
    fun formatSubjects(): String = buildString {
        append(ArxivResolver.resolveFull(primaryCategory))
        secondaryCategories.forEach { append("; "); append(ArxivResolver.resolveFull(it)) }
    }
    val pDFURL: String get() = "https://arxiv.org/pdf/$arxivID.pdf"
}
