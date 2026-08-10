package com.simon.harmonichackernews.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.CloudSummaryConfig
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore

/** Android persistence adapter for the configuration consumed by the shared summary use case. */
object AndroidAiSummarySettings {
    const val MODE_LOCAL = "local"
    const val MODE_CLOUD = "cloud"

    private const val PREF_MODE = "pref_ai_summary_mode"
    private const val PREF_SYSTEM_PROMPT = "pref_ai_summary_system_prompt"
    private const val PREF_STREAM_RESPONSES = "pref_ai_summary_stream_responses"

    fun mode(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_MODE, MODE_CLOUD) ?: MODE_CLOUD

    fun cloudConfig(context: Context): CloudSummaryConfig {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return CloudSummaryConfig(
            baseUrl = preferences.getString(
                AndroidAiModelDefaults.PREF_BASE_URL,
                AiSummaryProviders.defaultBaseUrl,
            ) ?: AiSummaryProviders.defaultBaseUrl,
            apiKey = AiSummaryApiKeyStore.getApiKey(context),
            model = preferences.getString(AndroidAiModelDefaults.PREF_MODEL, "").orEmpty(),
            systemPrompt = preferences.getString(
                PREF_SYSTEM_PROMPT,
                CloudSummaryDefaults.SYSTEM_PROMPT,
            ) ?: CloudSummaryDefaults.SYSTEM_PROMPT,
            streamResponses = preferences.getBoolean(PREF_STREAM_RESPONSES, true),
        )
    }
}
