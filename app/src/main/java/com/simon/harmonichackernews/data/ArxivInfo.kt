package com.simon.harmonichackernews.data

import android.text.TextUtils
import com.simon.harmonichackernews.utils.ArxivResolver

class ArxivInfo {
    var arxivAbstract: String? = null
    var authors: Array<String?> = emptyArray()
    var primaryCategory: String? = null
    var arxivID: String? = null

    var secondaryCategories: Array<String?> = emptyArray()

    var publishedDate: String? = null

    fun concatNames(): String? {
        return TextUtils.join(", ", authors)
    }

    fun formatDate(): String {
        return publishedDate!!.substring(0, 10)
    }

    fun formatSubjects(): String {
        val allSubjects = StringBuilder(ArxivResolver.resolveFull(primaryCategory))

        for (secondaryCategory in secondaryCategories) {
            allSubjects.append("; ").append(ArxivResolver.resolveFull(secondaryCategory))
        }
        return allSubjects.toString()
    }

    val pDFURL: String
        get() = "https://arxiv.org/pdf/" + arxivID + ".pdf"
}

