package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.ArxivResolver

class ArxivInfo {
    var arxivAbstract: String? = null
    var authors: Array<String?> = emptyArray()
    var primaryCategory: String? = null
    var arxivID: String? = null

    var secondaryCategories: Array<String?> = emptyArray()

    var publishedDate: String? = null

    fun concatNames(): String? = authors.joinToString(", ")

    fun formatDate(): String {
        return publishedDate!!.substring(0, 10)
    }

    fun formatSubjects(): String {
        return buildString {
            append(ArxivResolver.resolveFull(primaryCategory))
            secondaryCategories.forEach { category ->
                append("; ")
                append(ArxivResolver.resolveFull(category))
            }
        }
    }

    val pDFURL: String
        get() = "https://arxiv.org/pdf/$arxivID.pdf"
}
