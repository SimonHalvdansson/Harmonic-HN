package com.simon.harmonichackernews.utils;

import android.text.TextUtils;

public final class ReferenceLinkRowUtils {

    private ReferenceLinkRowUtils() {
    }

    public static String getReferenceLinkLabel(CollectedReferenceLinks.ReferenceLink link) {
        String resolvedTitle = link.getResolvedTitle();
        if (!TextUtils.isEmpty(resolvedTitle)) {
            return resolvedTitle.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        }

        String label = link.getLabel();
        if (TextUtils.isEmpty(label)) {
            return link.getUrl();
        }
        return label.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
