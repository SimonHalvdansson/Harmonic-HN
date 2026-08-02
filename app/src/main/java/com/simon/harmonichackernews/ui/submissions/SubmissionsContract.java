package com.simon.harmonichackernews.ui.submissions;

import android.content.Context;
import android.content.Intent;

import com.simon.harmonichackernews.MainActivity;

/** Navigation contract for a user's Compose submissions screen. */
public final class SubmissionsContract {
    public static final String ACTION_OPEN_SUBMISSIONS =
            "com.simon.harmonichackernews.action.OPEN_SUBMISSIONS";
    public static final String EXTRA_USER =
            "com.simon.harmonichackernews.extra.SUBMISSIONS_USER";

    private SubmissionsContract() {
    }

    public static Intent createIntent(Context context, String userName) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN_SUBMISSIONS)
                .putExtra(EXTRA_USER, userName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
