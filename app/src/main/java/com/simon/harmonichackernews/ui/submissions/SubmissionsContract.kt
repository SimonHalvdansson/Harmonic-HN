package com.simon.harmonichackernews.ui.submissions

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.SubmissionsDestination

/** Navigation contract for a user's Compose submissions screen.  */
object SubmissionsContract {
    const val ACTION_OPEN_SUBMISSIONS =
        "com.simon.harmonichackernews.action.OPEN_SUBMISSIONS"
    const val EXTRA_USER = "com.simon.harmonichackernews.extra.SUBMISSIONS_USER"

    fun createIntent(context: Context, userName: String?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SUBMISSIONS)
            .putExtra(EXTRA_USER, userName)
            .apply {
                userName?.takeIf(String::isNotBlank)?.let {
                    putExtra(
                        AppDestinationCodec.ANDROID_PAYLOAD_EXTRA,
                        AppDestinationCodec.encode(SubmissionsDestination(it)),
                    )
                }
            }
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
