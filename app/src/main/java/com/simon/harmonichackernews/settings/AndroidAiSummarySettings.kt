package com.simon.harmonichackernews.settings

import android.content.Context
import com.simon.harmonichackernews.network.CloudSummaryConfig
import com.simon.harmonichackernews.platform.AndroidCredentialStore

/** Android persistence adapter for the configuration consumed by the shared summary use case. */
object AndroidAiSummarySettings {
    const val MODE_LOCAL = "local"
    const val MODE_CLOUD = "cloud"

    fun repository(context: Context): AiSummarySettingsRepository = AiSummarySettingsRepository(
        store = AndroidKeyValueStore.defaults(context.applicationContext),
        credentials = AndroidCredentialStore(context.applicationContext),
        changes = AndroidUserSettings.get(context.applicationContext).changes,
    )

    fun mode(context: Context): String =
        repository(context).snapshot().mode.storedValue

    fun cloudConfig(context: Context): CloudSummaryConfig = repository(context).cloudConfig()
}
