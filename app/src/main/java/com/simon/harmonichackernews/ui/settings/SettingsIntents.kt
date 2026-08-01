package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.ComposeSettingsActivity

/** Entry points for the Compose-only settings experience. */
object SettingsIntents {
    @JvmStatic
    fun create(context: Context): Intent =
        Intent(context, ComposeSettingsActivity::class.java)

    @JvmStatic
    fun createAiSummary(context: Context): Intent =
        ComposeSettingsActivity.createAiSummaryIntent(context)
}
