package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity

/** Entry points for the Compose-only settings experience. */
object SettingsIntents {
    @JvmStatic
    fun create(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    @JvmStatic
    fun createAiSummary(context: Context): Intent =
        create(context).putExtra(
            MainActivity.EXTRA_SETTINGS_SECTION,
            SettingsSection.AiSummary.route,
        )
}
