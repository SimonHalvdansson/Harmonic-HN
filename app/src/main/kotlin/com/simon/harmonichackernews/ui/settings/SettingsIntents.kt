package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity

/** Entry points for the Compose-only settings experience. */
object SettingsIntents {
    const val ACTION_OPEN_SETTINGS = "com.simon.harmonichackernews.action.OPEN_SETTINGS"
    const val EXTRA_SETTINGS_SECTION =
        "com.simon.harmonichackernews.extra.SETTINGS_SECTION"

    @JvmStatic
    fun create(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    @JvmStatic
    fun createAiSummary(context: Context): Intent =
        create(context).putExtra(
            EXTRA_SETTINGS_SECTION,
            SettingsSection.AiSummary.route,
        )
}
