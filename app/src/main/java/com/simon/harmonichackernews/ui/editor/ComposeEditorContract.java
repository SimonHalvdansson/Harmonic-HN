package com.simon.harmonichackernews.ui.editor;

import android.content.Context;
import android.content.Intent;

import com.simon.harmonichackernews.MainActivity;

/** Navigation contract for the Compose post/comment editor. */
public final class ComposeEditorContract {
    public static final String ACTION_OPEN_EDITOR =
            "com.simon.harmonichackernews.action.OPEN_EDITOR";
    public static final String EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID";
    public static final String EXTRA_PARENT_TEXT =
            "com.simon.harmonichackernews.EXTRA_PARENT_TEXT";
    public static final String EXTRA_POST_TITLE =
            "com.simon.harmonichackernews.EXTRA_POST_TITLE";
    public static final String EXTRA_USER = "com.simon.harmonichackernews.EXTRA_USER";
    public static final String EXTRA_TYPE = "com.simon.harmonichackernews.EXTRA_TYPE";

    public static final int TYPE_TOP_COMMENT = 0;
    public static final int TYPE_COMMENT_REPLY = 1;
    public static final int TYPE_POST = 2;

    private ComposeEditorContract() {
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN_EDITOR)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
