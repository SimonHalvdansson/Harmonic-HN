package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalModelStateTest {
    private val downloadable = LocalModelDefinition(
        id = "downloadable",
        displayName = "Downloadable",
        parameterSize = "1B",
        quantization = "Q4",
        brand = LocalModelBrand.PRISM,
        fileName = "model.gguf",
        url = "https://example.com/model.gguf",
        sizeBytes = 1_000,
        downloadable = true,
        runtime = LocalModelRuntime.LLAMA_CPP,
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
