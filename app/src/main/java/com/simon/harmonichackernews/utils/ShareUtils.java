package com.simon.harmonichackernews.utils;

import android.content.Intent;

public class ShareUtils {

    /**
     * Creates a share intent for sharing content.
     *
     * @param content The content to be shared.
     * @return A share intent with the provided content.
     */
    public static Intent getShareIntent(String content) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, content);
        intent.setType("text/plain");

        return Intent.createChooser(intent, null);
    }

    /**
     * Creates a share intent for sharing content with a custom title.
     *
     * @param content The content to be shared.
     * @param title   The custom title for the shared content.
     * @return A share intent with the provided content and title.
     */
    public static Intent getShareIntent(String content, String title) {
        return getShareIntent(title + " - " + content);
    }

}
