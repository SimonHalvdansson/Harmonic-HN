package com.simon.harmonichackernews.utils;

import android.content.Intent;

public class ShareUtils {

    private final static String SHARE_BASE_URL = "https://news.ycombinator.com/item?id=";

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
     * Creates a share intent for sharing content by id.
     *
     * @param id ID of the content to be shared.
     * @return A share intent with the provided content and title.
     */
    public static Intent getShareIntent(int id) {
        return getShareIntent(SHARE_BASE_URL + id);
    }

    public static Intent getShareIntentWithTitle(String title, String url) {
        return getShareIntent(title + " | " + url);
    }

    public static Intent getShareIntentWithTitle(String title, int id) {
        return getShareIntent(title + " | " + SHARE_BASE_URL + id);
    }

}
