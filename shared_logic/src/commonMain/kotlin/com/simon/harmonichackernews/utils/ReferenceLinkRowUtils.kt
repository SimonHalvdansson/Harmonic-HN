package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.utils.CollectedReferenceLinks.ReferenceLink

object ReferenceLinkRowUtils {
    fun getReferenceLinkLabel(link: ReferenceLink): String =
        (link.resolvedTitle?.takeIf(String::isNotEmpty)
            ?: link.label?.takeIf(String::isNotEmpty)
            ?: link.url.orEmpty())
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}
