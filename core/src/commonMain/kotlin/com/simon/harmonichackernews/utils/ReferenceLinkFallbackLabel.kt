package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.utils.CollectedReferenceLinks.ReferenceLink

fun referenceLinkFallbackLabel(link: ReferenceLink): String =
    (link.resolvedTitle?.takeIf(String::isNotEmpty)
        ?: link.label?.takeIf(String::isNotEmpty)
        ?: link.url.orEmpty())
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
