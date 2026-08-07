package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.utils.CollectedReferenceLinks.ReferenceLink

object ReferenceLinkRowUtils {
    private val whitespace = "\\s+".toRegex()

    fun getReferenceLinkLabel(link: ReferenceLink): String {
        val label = link.resolvedTitle?.takeIf(String::isNotEmpty)
            ?: link.label?.takeIf(String::isNotEmpty)
            ?: return link.url.orEmpty()
        return label.replace('\n', ' ').replace(whitespace, " ").trim { it <= ' ' }
    }
}
