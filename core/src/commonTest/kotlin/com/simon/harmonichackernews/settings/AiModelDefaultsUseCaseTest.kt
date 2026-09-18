package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.network.AiModel
import com.simon.harmonichackernews.network.AiModelCatalogRepository
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiModelDefaultsUseCaseTest {
    @Test
    fun slowNewProviderLookupSelectsItsDefaultAfterTheUrlWasSaved() = runTest {
        val settings = settings()
        settings.setModel("openai/previous")
        val update = settings.setBaseUrl(AiSummaryProviders.GOOGLE.baseUrl)
        assertTrue(update.needsDefaultModel)
        val catalog = DeferredCatalog()
        val defaults = AiModelDefaultsUseCase(settings, catalog)
        // Initial OpenRouter setup must not fetch unrelated models for the new provider.
        assertFalse(defaults.ensureInitialDefault())
        assertTrue(catalog.requests.isEmpty())
        val lookup = async { defaults.ensureProviderDefault(AiSummaryProviders.GOOGLE) }
        runCurrent()
        assertEquals("", settings.snapshot().model)
        catalog.result.complete(listOf(model("gemini-test")))
        assertTrue(lookup.await())
        assertEquals("gemini-test", settings.snapshot().model)
    }

    @Test
    fun slowLookupCannotOverwriteAManualChoiceOrAnotherProvider() = runTest {
        for (changeProvider in listOf(false, true)) {
            val settings = settings()
            settings.setBaseUrl(AiSummaryProviders.GOOGLE.baseUrl)
            val catalog = DeferredCatalog()
            val lookup = async {
                AiModelDefaultsUseCase(settings, catalog).ensureProviderDefault(AiSummaryProviders.GOOGLE)
            }
            runCurrent()
            if (changeProvider) settings.setBaseUrl(AiSummaryProviders.OPENAI.baseUrl)
            else settings.setModel("manual-model")
            catalog.result.complete(listOf(model("gemini-test")))
            assertFalse(lookup.await())
            assertEquals(if (changeProvider) "" else "manual-model", settings.snapshot().model)
        }
    }

    private fun settings() = AiSummarySettingsRepository(
        TestKeyValueStore(), TestCredentialStore(), emptyFlow(),
    )

    private fun model(id: String) = AiModel(
        openRouterId = "google/$id", requestId = id, name = id, created = 0L,
        inputPrice = 0.1, outputPrice = 0.2, contextLength = 10_000,
    )

    private class DeferredCatalog : AiModelCatalogRepository {
        val requests = mutableListOf<String>()
        val result = CompletableDeferred<List<AiModel>>()
        override suspend fun fetchModels(provider: AiSummaryProviders.Provider, sort: AiModelCatalogSort): List<AiModel> {
            requests += provider.id
            return result.await()
        }
        override suspend fun resolveModel(provider: AiSummaryProviders.Provider, enteredModelId: String?): AiModel =
            error("Not used")
        override suspend fun fetchUptimeLastDay(provider: AiSummaryProviders.Provider, openRouterModelId: String): Double? =
            error("Not used")
    }
}
