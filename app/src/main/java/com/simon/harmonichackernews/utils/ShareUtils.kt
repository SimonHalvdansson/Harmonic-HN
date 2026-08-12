package com.simon.harmonichackernews.utils

import android.content.Intent

object ShareUtils {
    private const val SHARE_BASE_URL = "https://news.ycombinator.com/item?id="

    /**
     * Creates a share intent for sharing content.
     *
     * @param content The content to be shared.
     * @return A share intent with the provided content.
     */
    fun getShareIntent(content: String?): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        },
        null,
    )
}
