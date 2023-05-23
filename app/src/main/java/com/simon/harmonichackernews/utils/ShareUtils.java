package com.simon.harmonichackernews.utils;

import android.content.Intent;

public class ShareUtils {

    public static Intent getShareIntent(String content, String title) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, title + " - " + content);
        intent.setType("text/plain");

        return Intent.createChooser(intent, null);
    }

}
