package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.platform.CredentialStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class AiSummarySettingsRepositoryTest {
    @Test
    fun defaultsAndValidationArePortable() {
        val repository = AiSummarySettingsRepository(
            TestKeyValueStore(),
            TestCredentialStore(),
            emptyFlow(),
        )

        val settings = repository.snapshot()
        assertEquals(AiSummaryMode.CLOUD, settings.mode)
        assertEquals(AiSummaryProviders.defaultBaseUrl, settings.baseUrl)
        assertEquals(CloudSummaryDefaults.SYSTEM_PROMPT, settings.systemPrompt)
        assertEquals(GeminiNanoSummaryMode.THREE_BULLETS, settings.geminiNanoSummaryMode)
        assertFalse(settings.autoSummarizeArticles)
        assertFalse(settings.cloudConfigurationComplete)
        assertFalse(settings.enabled(localConfigurationReady = false))
    }

    @Test
    fun incompleteExplicitConfigurationIsDisabled() = runTest {
        val store = TestKeyValueStore(mapOf(AiSummaryPreferenceKeys.ENABLED to true))
        val repository = AiSummarySettingsRepository(store, TestCredentialStore(), emptyFlow())

        repository.awaitSnapshot()
        assertTrue(repository.disableIfConfigurationIncomplete(localConfigurationReady = false))
        assertFalse(store.getBoolean(AiSummaryPreferenceKeys.ENABLED, true))
        assertFalse(repository.disableIfConfigurationIncomplete(localConfigurationReady = false))
    }

    @Test
    fun unresolvedConfigurationNeverPersistsDisabledState() = runTest {
        val store = TestKeyValueStore(mapOf(AiSummaryPreferenceKeys.ENABLED to true))
        val repository = AiSummarySettingsRepository(store, TestCredentialStore(), emptyFlow())

        repository.awaitSnapshot()
        assertFalse(
            repository.disableIfConfigurationIncomplete(
                localConfigurationReady = false,
                configurationResolved = false,
            ),
        )
        assertTrue(store.getBoolean(AiSummaryPreferenceKeys.ENABLED, false))
    }

    @Test
    fun providerChangesTranslateCompatibleModelsAndClearIncompatibleModels() {
        val store = TestKeyValueStore(
            mapOf(
                AiSummaryPreferenceKeys.BASE_URL to AiSummaryProviders.defaultBaseUrl,
                AiSummaryPreferenceKeys.MODEL to "openai/gpt-4o",
            ),
        )
        val repository = AiSummarySettingsRepository(store, TestCredentialStore(), emptyFlow())

        val openAiUpdate = repository.setBaseUrl(AiSummaryProviders.OPENAI.baseUrl)
        assertEquals("gpt-4o", repository.snapshot().model)
        assertFalse(openAiUpdate.needsDefaultModel)

        val googleUpdate = repository.setBaseUrl(AiSummaryProviders.GOOGLE.baseUrl)
        assertEquals("", repository.snapshot().model)
        assertTrue(googleUpdate.needsDefaultModel)
        assertEquals(AiSummaryProviders.PROVIDER_GOOGLE, googleUpdate.provider?.id)
    }

    @Test
    fun secureTextUsesCredentialBoundary() = runTest {
        val credentials = TestCredentialStore()
        val repository = AiSummarySettingsRepository(TestKeyValueStore(), credentials, emptyFlow())

        assertTrue(repository.setText(AiSummaryTextSetting.API_KEY, "secret"))
        assertEquals("secret", repository.snapshot().apiKey)
        assertNull(credentials.read("unrelated"))
    }

    @Test
    fun snapshotDoesNotSynchronouslyReadCredentials() = runTest {
        val credentials = TestCredentialStore(
            mapOf(com.simon.harmonichackernews.platform.CredentialIds.AI_SUMMARY_API_KEY to "secret"),
        )
        val repository = AiSummarySettingsRepository(TestKeyValueStore(), credentials, emptyFlow())

        assertFalse(repository.snapshot().credentialsLoaded)
        assertEquals("", repository.snapshot().apiKey)
        assertEquals("secret", repository.awaitSnapshot().apiKey)
    }

    @Test
    fun localBehaviorPreferencesRoundTrip() {
        val repository = AiSummarySettingsRepository(
            TestKeyValueStore(),
            TestCredentialStore(),
            emptyFlow(),
        )

        repository.setAutoSummarizeArticles(true)
        repository.setGeminiNanoSummaryMode(GeminiNanoSummaryMode.SYSTEM_PROMPT)

        assertTrue(repository.snapshot().autoSummarizeArticles)
        assertEquals(
            GeminiNanoSummaryMode.SYSTEM_PROMPT,
            repository.snapshot().geminiNanoSummaryMode,
        )
    }
}

internal class TestCredentialStore(
    initialValues: Map<String, String> = emptyMap(),
) : CredentialStore {
    private val values = initialValues.toMutableMap()

    override fun read(id: String): String? = values[id]

    override fun write(id: String, value: String): Boolean {
        values[id] = value
        return true
    }

    override fun remove(id: String): Boolean {
        values.remove(id)
        return true
    }
}
