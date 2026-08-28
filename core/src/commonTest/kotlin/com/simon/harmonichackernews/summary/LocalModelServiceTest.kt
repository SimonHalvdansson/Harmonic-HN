package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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

    @Test
    fun preloadFillsCacheWithoutRegisteringPlatformObservers() {
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

        assertTrue(service.cachedState.value.statuses.isEmpty())
        assertEquals(0, transfers.workReads)

        service.preload()

        assertEquals(2, service.cachedState.value.statuses.size)
        assertEquals(2, service.cachedState.value.runtimeStatuses.size)
        assertEquals(2, transfers.workReads)
        assertEquals(2, runtimeDelivery.statusReads)
        assertEquals(0, transfers.observerRegistrations)
        assertEquals(0, runtimeDelivery.observerRegistrations)

        service.catalog.forEach { model ->
            service.presentation(
                model = model,
                nanoAvailabilityResolved = true,
                nanoAvailable = false,
                managerState = service.cachedState.value,
            )
        }
        assertEquals(2, transfers.workReads)
        assertEquals(2, runtimeDelivery.statusReads)

        transfers.invokeObserverOnRegistration = true
        runtimeDelivery.invokeObserverOnRegistration = true
        service.startMonitoring()

        assertEquals(2, transfers.workReads)
        assertEquals(1, transfers.observerRegistrations)
        assertEquals(1, runtimeDelivery.observerRegistrations)
    }

    @Test
    fun changingStorageDirectoryRefreshesStorageAndClearsUnavailableSelection() {
        val transfers = RecordingTransfers()
        val location = RecordingStorageLocation("old")
        val service = LocalModelService(
            preferences = TestKeyValueStore(),
            storage = LocationAwareStorage(location),
            transfers = transfers,
            runtimeDelivery = RecordingRuntimeDelivery(),
            capabilities = LocalModelDeviceCapabilities(
                supportsDownloadableModels = true,
                supportsLiteRtModels = true,
            ),
            models = listOf(BuiltInModel, DownloadableModel),
            storageLocation = location,
        )

        assertTrue(service.select(DownloadableModel.id))
        assertEquals(DownloadableModel, service.selectedModel)

        assertNull(service.changeStorageDirectory("new"))

        assertEquals("new", service.storageDirectoryPath)
        assertEquals(BuiltInModel, service.selectedModel)
        assertEquals(1, transfers.resets)
    }

    @Test
    fun reportsAndClearsAllStoredModelBytesThroughTheOwningService() = runTest {
        val storage = RecordingClearStorage()
        val transfers = RecordingTransfers()
        val service = LocalModelService(
            preferences = TestKeyValueStore(),
            storage = storage,
            transfers = transfers,
            runtimeDelivery = RecordingRuntimeDelivery(),
            capabilities = LocalModelDeviceCapabilities(
                supportsDownloadableModels = true,
                supportsLiteRtModels = true,
            ),
            models = listOf(BuiltInModel, DownloadableModel),
        )

        assertTrue(service.select(DownloadableModel.id))
        assertEquals(1_500L, service.storedModelBytes())

        assertTrue(service.clearStoredModels())

        assertEquals(0L, service.storedModelBytes())
        assertTrue(storage.cleared)
        assertEquals(listOf(DownloadableModel.id), transfers.cancelled)
        assertEquals(BuiltInModel, service.selectedModel)
    }

    private class RecordingTransfers : LocalModelTransferScheduler {
        var observerRegistrations = 0
        var workReads = 0
        var resets = 0
        var invokeObserverOnRegistration = false
        val cancelled = mutableListOf<String>()

        override fun work(modelId: String): LocalModelWorkSnapshot? {
            workReads += 1
            return null
        }

        override fun isActive(modelId: String): Boolean = false

        override fun enqueue(model: LocalModelDefinition) = Unit

        override fun cancel(modelId: String, onCancelled: () -> Unit) {
            cancelled += modelId
            onCancelled()
        }

        override fun setObserver(observer: () -> Unit) {
            observerRegistrations += 1
            if (invokeObserverOnRegistration) observer()
        }

        override fun reset() {
            resets += 1
        }
    }

    private class RecordingRuntimeDelivery : LocalModelRuntimeDelivery {
        var observerRegistrations = 0
        var statusReads = 0
        var invokeObserverOnRegistration = false
        override val included = false

        override fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus {
            statusReads += 1
            return LocalRuntimeInstallStatus(
                LocalRuntimeInstallState.NOT_INSTALLED,
                runtime = runtime,
            )
        }

        override fun isInstalled(runtime: LocalModelRuntime) = false

        override fun request(model: LocalModelDefinition): String? = null

        override fun cancel(runtime: LocalModelRuntime) = Unit

        override fun setObserver(observer: () -> Unit) {
            observerRegistrations += 1
            if (invokeObserverOnRegistration) observer()
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

        override fun storedBytes() = 0L

        override fun clearStoredModels() = true
    }

    private class RecordingStorageLocation(
        override var directoryPath: String,
    ) : LocalModelStorageLocation {
        override fun changeDirectory(path: String): String? {
            directoryPath = path
            return null
        }
    }

    private class LocationAwareStorage(
        private val location: RecordingStorageLocation,
    ) : LocalModelStorage {
        override fun snapshot(model: LocalModelDefinition) = LocalModelStorageSnapshot(
            finalFileBytes = model.sizeBytes.takeIf {
                model.downloadable && location.directoryPath == "old"
            },
        )

        override fun prepareDownload(model: LocalModelDefinition) =
            LocalModelStoragePreparation.Ready(snapshot(model))

        override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) = Unit

        override fun installedPath(model: LocalModelDefinition) = location.directoryPath

        override fun storedBytes() = 0L

        override fun clearStoredModels() = true
    }

    private class RecordingClearStorage : LocalModelStorage {
        var bytes = 1_500L
        var cleared = false

        override fun snapshot(model: LocalModelDefinition) = LocalModelStorageSnapshot(
            finalFileBytes = model.sizeBytes.takeIf { model.downloadable },
        )

        override fun prepareDownload(model: LocalModelDefinition) =
            LocalModelStoragePreparation.Ready(snapshot(model))

        override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) = Unit

        override fun installedPath(model: LocalModelDefinition) = "model.gguf"

        override fun storedBytes() = bytes

        override fun clearStoredModels(): Boolean {
            cleared = true
            bytes = 0L
            return true
        }
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
