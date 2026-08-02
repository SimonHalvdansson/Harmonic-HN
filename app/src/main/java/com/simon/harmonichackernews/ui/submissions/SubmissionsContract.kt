package com.simon.harmonichackernews.ui.submissions

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity

/** Navigation contract for a user's Compose submissions screen.  */
object SubmissionsContract {
    const val ACTION_OPEN_SUBMISSIONS: String =
        "com.simon.harmonichackernews.action.OPEN_SUBMISSIONS"
    const val EXTRA_USER: String = "com.simon.harmonichackernews.extra.SUBMISSIONS_USER"

    fun createIntent(context: Context?, userName: String?): Intent {
        return Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SUBMISSIONS)
            .putExtra(EXTRA_USER, userName)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
