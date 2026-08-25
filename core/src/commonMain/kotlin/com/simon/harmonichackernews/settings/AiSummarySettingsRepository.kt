package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.CloudSummaryConfig
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.platform.CredentialIds
import com.simon.harmonichackernews.platform.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

object AiSummaryPreferenceKeys {
    const val ENABLED = "pref_ai_summary_enabled"
    const val MODE = "pref_ai_summary_mode"
    const val BASE_URL = "pref_ai_summary_base_url"
    const val MODEL = "pref_ai_summary_model"
    const val SYSTEM_PROMPT = "pref_ai_summary_system_prompt"
    const val STREAM_RESPONSES = "pref_ai_summary_stream_responses"
}

enum class AiSummaryMode(val storedValue: String) {
    LOCAL("local"),
    CLOUD("cloud");

    companion object {
        fun fromStored(value: String?): AiSummaryMode =
            entries.firstOrNull { it.storedValue == value } ?: CLOUD
    }
}

enum class AiSummaryTextSetting { API_KEY, SYSTEM_PROMPT }

data class AiSummarySettingsSnapshot(
    val explicitlyEnabled: Boolean?,
    val mode: AiSummaryMode,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val streamResponses: Boolean,
) {
    val cloudConfigurationComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank() &&
            systemPrompt.isNotBlank()

    fun configurationComplete(localConfigurationReady: Boolean): Boolean =
        if (mode == AiSummaryMode.LOCAL) localConfigurationReady else cloudConfigurationComplete

    fun enabled(localConfigurationReady: Boolean): Boolean {
        val complete = configurationComplete(localConfigurationReady)
        return complete && (explicitlyEnabled ?: complete)
    }

    val apiKeyPreview: String
        get() = if (apiKey.isBlank()) "Not set" else apiKey.take(8) + "…"
}

data class AiBaseUrlUpdate(
    val provider: AiSummaryProviders.Provider?,
    val needsDefaultModel: Boolean,
)

/** Portable persistence, validation and provider-transition rules for AI summaries. */
class AiSummarySettingsRepository(
    private val store: KeyValueStore,
    private val credentials: CredentialStore,
    private val changes: Flow<Unit>,
) {
    fun snapshot(): AiSummarySettingsSnapshot = AiSummarySettingsSnapshot(
        explicitlyEnabled = if (store.contains(AiSummaryPreferenceKeys.ENABLED)) {
            store.getBoolean(AiSummaryPreferenceKeys.ENABLED, false)
        } else {
            null
        },
        mode = AiSummaryMode.fromStored(store.getString(AiSummaryPreferenceKeys.MODE)),
        baseUrl = store.getString(
            AiSummaryPreferenceKeys.BASE_URL,
            AiSummaryProviders.defaultBaseUrl,
        ) ?: AiSummaryProviders.defaultBaseUrl,
        apiKey = credentials.read(CredentialIds.AI_SUMMARY_API_KEY).orEmpty(),
        model = store.getString(AiSummaryPreferenceKeys.MODEL, "").orEmpty(),
        systemPrompt = store.getString(
            AiSummaryPreferenceKeys.SYSTEM_PROMPT,
            CloudSummaryDefaults.SYSTEM_PROMPT,
        ) ?: CloudSummaryDefaults.SYSTEM_PROMPT,
        streamResponses = store.getBoolean(AiSummaryPreferenceKeys.STREAM_RESPONSES, true),
    )

    val updates: Flow<AiSummarySettingsSnapshot> = flow {
        emit(snapshot())
        changes.collect { emit(snapshot()) }
    }.distinctUntilChanged()

    fun setEnabled(value: Boolean) {
        store.putBoolean(AiSummaryPreferenceKeys.ENABLED, value)
    }

    fun setMode(value: AiSummaryMode) {
        store.putString(AiSummaryPreferenceKeys.MODE, value.storedValue)
    }

    fun forceCloudMode() = setMode(AiSummaryMode.CLOUD)

    fun setStreamResponses(value: Boolean) {
        store.putBoolean(AiSummaryPreferenceKeys.STREAM_RESPONSES, value)
    }

    fun disableIfConfigurationIncomplete(localConfigurationReady: Boolean): Boolean {
        val current = snapshot()
        if (current.explicitlyEnabled != true || current.configurationComplete(localConfigurationReady)) {
            return false
        }
        setEnabled(false)
        return true
    }

    fun text(setting: AiSummaryTextSetting): String = when (setting) {
        AiSummaryTextSetting.API_KEY -> snapshot().apiKey
        AiSummaryTextSetting.SYSTEM_PROMPT -> snapshot().systemPrompt
    }

    fun setText(setting: AiSummaryTextSetting, value: String): Boolean = when (setting) {
        AiSummaryTextSetting.API_KEY -> credentials.write(CredentialIds.AI_SUMMARY_API_KEY, value)
        AiSummaryTextSetting.SYSTEM_PROMPT -> {
            store.putString(AiSummaryPreferenceKeys.SYSTEM_PROMPT, value)
            true
        }
    }

    fun setBaseUrl(value: String): AiBaseUrlUpdate {
        val previous = snapshot()
        val oldProvider = AiSummaryProviders.getProviderForBaseUrl(previous.baseUrl)
        val newProvider = AiSummaryProviders.getProviderForBaseUrl(value)
        store.putString(AiSummaryPreferenceKeys.BASE_URL, value)
        if (newProvider != null && newProvider.id != oldProvider?.id) {
            val translated = oldProvider?.let {
                AiSummaryProviders.translateModelId(it, newProvider, previous.model)
            }.orEmpty()
            if (translated.isEmpty()) {
                store.remove(AiSummaryPreferenceKeys.MODEL)
            } else {
                store.putString(AiSummaryPreferenceKeys.MODEL, translated)
            }
        }
        return AiBaseUrlUpdate(
            provider = newProvider,
            needsDefaultModel = newProvider != null && !store.contains(AiSummaryPreferenceKeys.MODEL),
        )
    }

    fun modelForPicker(): String {
        val current = snapshot()
        return AiSummaryProviders.getModelForRequest(current.baseUrl, current.model)
    }

    fun setModelForCurrentProvider(openRouterModelId: String) {
        val current = snapshot()
        val provider = AiSummaryProviders.getProviderForBaseUrl(current.baseUrl)
            ?: AiSummaryProviders.defaultProvider
        store.putString(
            AiSummaryPreferenceKeys.MODEL,
            AiSummaryProviders.toProviderModelId(provider, openRouterModelId),
        )
    }

    fun setModel(value: String) {
        store.putString(AiSummaryPreferenceKeys.MODEL, value)
    }

    fun hasModelSelection(): Boolean = store.contains(AiSummaryPreferenceKeys.MODEL)

    fun cloudConfig(): CloudSummaryConfig = snapshot().let { current ->
        CloudSummaryConfig(
            baseUrl = current.baseUrl,
            apiKey = current.apiKey,
            model = current.model,
            systemPrompt = current.systemPrompt,
            streamResponses = current.streamResponses,
        )
    }
}
