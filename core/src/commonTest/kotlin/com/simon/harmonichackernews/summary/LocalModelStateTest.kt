package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalModelStateTest {
    @Test
    fun catalogUpgradePreservesReplacementSelectionAndFallsBackForRetiredModels() {
        val preferences = TestKeyValueStore(mapOf("selection" to LocalModelCatalog.MODEL_MINISTRAL_3B))
        val store = LocalModelStateStore(
            LocalModelCatalog.models, preferences, "selection", LocalModelCatalog.MODEL_GEMINI_NANO,
        )
        assertEquals(LocalModelCatalog.MODEL_MINISTRAL_3B, store.selectedModelId)
        // The previous GGUF byte count must never mark the replacement as installed.
        assertEquals(
            LocalModelTransferState.NOT_DOWNLOADED,
            store.resolveStatus(LocalModelCatalog.MODEL_MINISTRAL_3B, 2_147_023_008, 0, null).state,
        )
        preferences.putString("selection", "bonsai-1.7b")
        assertEquals(LocalModelCatalog.MODEL_GEMINI_NANO, store.selectedModelId)
        preferences.putString("selection", "lfm-2.5-1.2b")
        assertEquals(LocalModelCatalog.MODEL_GEMINI_NANO, store.selectedModelId)
    }

    private val downloadable = LocalModelDefinition(
        id = "downloadable",
        displayName = "Downloadable",
        parameterSize = "1B",
        quantization = "Q4",
        brand = LocalModelBrand.LIQUID,
        fileName = "model.litertlm",
        url = "https://example.com/model.litertlm",
        sizeBytes = 1_000,
        downloadable = true,
        runtime = LocalModelRuntime.LITERT_LM,
        contextTokens = 2_048,
    )
    private val bundled = downloadable.copy(
        id = "bundled",
        fileName = "",
        url = "",
        sizeBytes = 0,
        downloadable = false,
    )

    @Test
    fun invalidPersistedSelectionFallsBackToTheDefault() {
        val store = stateStore(TestKeyValueStore(mapOf("selection" to "missing")))

        assertEquals("bundled", store.selectedModelId)
    }

    @Test
    fun downloadableModelCanOnlyBeSelectedWhenSupportedAndDownloaded() {
        val preferences = TestKeyValueStore()
        val store = stateStore(preferences)

        assertFalse(store.select("downloadable", supported = false, downloaded = true))
        assertFalse(store.select("downloadable", supported = true, downloaded = false))
        assertTrue(store.select("downloadable", supported = true, downloaded = true))
        assertEquals("downloadable", store.selectedModelId)
    }

    @Test
    fun activeWorkTakesPriorityOverPartialFileState() {
        val status = stateStore().resolveStatus(
            modelId = "downloadable",
            finalFileBytes = null,
            partialFileBytes = 400,
            work = LocalModelWorkSnapshot(
                state = LocalModelWorkState.RUNNING,
                receivedBytes = 650,
            ),
        )

        assertEquals(LocalModelTransferState.DOWNLOADING, status.state)
        assertEquals(650, status.receivedBytes)
    }

    @Test
    fun transferStatusDistinguishesCompletePartialAndFailedDownloads() {
        val store = stateStore()

        assertEquals(
            LocalModelTransferState.DOWNLOADED,
            store.resolveStatus("downloadable", 1_000, 0, null).state,
        )
        assertEquals(
            LocalModelTransferState.PARTIALLY_DOWNLOADED,
            store.resolveStatus("downloadable", null, 250, null).state,
        )
        assertEquals(
            "Model download failed",
            store.resolveStatus(
                "downloadable",
                null,
                0,
                LocalModelWorkSnapshot(LocalModelWorkState.FAILED),
            ).error,
        )
    }

    @Test
    fun progressAndByteFormattingAreClampedAndStable() {
        assertEquals(0, localModelProgressPercent(50, 0))
        assertEquals(100, localModelProgressPercent(2_000, 1_000))
        assertEquals(25, localModelProgressPercent(250, 1_000))
        assertEquals("1.5 kB", formatDecimalBytes(1_500))
        assertEquals("1.5 MB", formatDecimalBytes(1_500_000))
        assertEquals("1.50 GB", formatDecimalBytes(1_500_000_000))
        assertEquals("0 B", formatDecimalBytes(0))
    }

    @Test
    fun hostCanExplainThatLiteRtIsUnavailableForAPlatformSpecificReason() {
        val liteRt = downloadable.copy(runtime = LocalModelRuntime.LITERT_LM)
        val capabilities = LocalModelDeviceCapabilities(
            supportsDownloadableModels = true,
            supportsLiteRtModels = false,
            liteRtUnsupportedReason = LocalModelUnsupportedReason.RUNTIME_UNAVAILABLE,
        )

        assertEquals(
            LocalModelUnsupportedReason.RUNTIME_UNAVAILABLE,
            capabilities.unsupportedReason(liteRt),
        )
    }

    private fun stateStore(preferences: TestKeyValueStore = TestKeyValueStore()) =
        LocalModelStateStore(
            models = listOf(bundled, downloadable),
            preferences = preferences,
            selectionKey = "selection",
            defaultModelId = "bundled",
        )
}
