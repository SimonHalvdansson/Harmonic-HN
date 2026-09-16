package com.simon.harmonichackernews.data

class ArxivInfo {
    var arxivAbstract: String? = null
    var authors: Array<String?> = emptyArray()
    var primaryCategory: String? = null
    var arxivID: String? = null
    var htmlUrl: String? = null

    var secondaryCategories: Array<String?> = emptyArray()

    var publishedDate: String? = null

    fun formatDate(): String {
        return publishedDate!!.substring(0, 10)
    }

    val pDFURL: String
        get() = "https://arxiv.org/pdf/$arxivID.pdf"
}
