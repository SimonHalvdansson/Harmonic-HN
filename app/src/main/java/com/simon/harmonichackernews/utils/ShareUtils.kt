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
    fun getShareIntent(content: String?): Intent? {
        val intent = Intent()
        intent.setAction(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, content)
        intent.setType("text/plain")

        return Intent.createChooser(intent, null)
    }

    /**
     * Creates a share intent for sharing content by id.
     * 
     * @param id ID of the content to be shared.
     * @return A share intent with the provided content and title.
     */
    fun getShareIntent(id: Int): Intent? {
        return getShareIntent(SHARE_BASE_URL + id)
    }

    fun getShareIntentWithTitle(title: String?, url: String?): Intent? {
        return getShareIntent(title + " | " + url)
    }

    fun getShareIntentWithTitle(title: String?, id: Int): Intent? {
        return getShareIntent(title + " | " + SHARE_BASE_URL + id)
    }

    fun getShareIntentWithTitle(title: String, id: Int, url: String?): Intent? {
        val content = String.format(
            "%1\$s | %2\$s\n\n---\n\nHacker News Comments | %3\$s",
            title,
            url,
            SHARE_BASE_URL + id
        )

        return getShareIntent(content)
    }
}
