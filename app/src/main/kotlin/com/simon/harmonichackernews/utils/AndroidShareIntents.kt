package com.simon.harmonichackernews.utils

import android.content.Intent

object AndroidShareIntents {
    /**
     * Creates a share intent for sharing content.
     *
     * @param content The content to be shared.
     * @return A share intent with the provided content.
     */
    fun createTextShareChooser(content: String?): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        },
        null,
    )
}
