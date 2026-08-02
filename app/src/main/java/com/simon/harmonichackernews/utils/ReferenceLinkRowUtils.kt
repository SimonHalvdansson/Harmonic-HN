package com.simon.harmonichackernews.utils

import android.text.TextUtils
import com.simon.harmonichackernews.utils.CollectedReferenceLinks.ReferenceLink

object ReferenceLinkRowUtils {
    fun getReferenceLinkLabel(link: ReferenceLink): String {
        val resolvedTitle = link.resolvedTitle
        if (!TextUtils.isEmpty(resolvedTitle)) {
            return resolvedTitle!!.replace('\n', ' ').replace("\\s+".toRegex(), " ")
                .trim { it <= ' ' }
        }

        val label = link.label
        if (TextUtils.isEmpty(label)) {
            return link.url.orEmpty()
        }
        return label!!.replace('\n', ' ').replace("\\s+".toRegex(), " ").trim { it <= ' ' }
    }
}
