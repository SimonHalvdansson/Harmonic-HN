package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalModelServiceTest {
    @Test
    fun transferMonitoringStartsWhenTransferStateIsRequested() {
        val transfers = RecordingTransfers()
        val runtimeDelivery = RecordingRuntimeDelivery()
        val service = LocalModelService(
            preferences = TestKeyValueStore(),
            storage = EmptyStorage,
            transfers = transfers,
            runtimeDelivery = runtimeDelivery,
            capabilities = LocalModelDeviceCapabilities(
                supportsDownloadableModels = true,
                supportsLiteRtModels = true,
            ),
            models = listOf(BuiltInModel, DownloadableModel),
        )

        assertEquals(BuiltInModel, service.selectedModel)
        assertEquals(0, transfers.observerRegistrations)
        assertEquals(0, transfers.workReads)
        assertEquals(0, runtimeDelivery.observerRegistrations)

        service.state.value
        service.state.value

        assertEquals(1, transfers.observerRegistrations)
        assertEquals(2, transfers.workReads)
        assertEquals(1, runtimeDelivery.observerRegistrations)
    }

    private class RecordingTransfers : LocalModelTransferScheduler {
        var observerRegistrations = 0
        var workReads = 0

        override fun work(modelId: String): LocalModelWorkSnapshot? {
            workReads += 1
            return null
        }

        override fun isActive(modelId: String): Boolean = false

        override fun enqueue(model: LocalModelDefinition) = Unit

        override fun cancel(modelId: String, onCancelled: () -> Unit) = onCancelled()

        override fun setObserver(observer: () -> Unit) {
            observerRegistrations += 1
        }
    }

    private class RecordingRuntimeDelivery : LocalModelRuntimeDelivery {
        var observerRegistrations = 0
        override val included = false

        override fun status(runtime: LocalModelRuntime) =
            LocalRuntimeInstallStatus(LocalRuntimeInstallState.NOT_INSTALLED, runtime = runtime)

        override fun isInstalled(runtime: LocalModelRuntime) = false

        override fun request(model: LocalModelDefinition): String? = null

        override fun cancel(runtime: LocalModelRuntime) = Unit

        override fun setObserver(observer: () -> Unit) {
            observerRegistrations += 1
        }

        override fun setModelDownloadStarter(starter: (String) -> String?) = Unit

        override fun engineClassName(runtime: LocalModelRuntime): String? = null

        override fun runtimeLabel(runtime: LocalModelRuntime) = runtime.name
    }

    private data object EmptyStorage : LocalModelStorage {
        override fun snapshot(model: LocalModelDefinition) = LocalModelStorageSnapshot()

        override fun prepareDownload(model: LocalModelDefinition) =
            LocalModelStoragePreparation.Ready(LocalModelStorageSnapshot())

        override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) = Unit

        override fun installedPath(model: LocalModelDefinition) = ""
    }

    private companion object {
        val BuiltInModel = LocalModelDefinition(
            id = LocalModelCatalog.MODEL_GEMINI_NANO,
            displayName = "Built in",
            parameterSize = "",
            quantization = "",
            brand = LocalModelBrand.GOOGLE,
            fileName = "",
            url = "",
            sizeBytes = 0,
            downloadable = false,
            runtime = LocalModelRuntime.GEMINI_NANO,
            contextTokens = 0,
        )
        val DownloadableModel = BuiltInModel.copy(
            id = "downloadable",
            displayName = "Downloadable",
            fileName = "model.gguf",
            url = "https://example.com/model.gguf",
            sizeBytes = 1_000,
            downloadable = true,
            runtime = LocalModelRuntime.LLAMA_CPP,
            contextTokens = 2_048,
        )
    }
}
