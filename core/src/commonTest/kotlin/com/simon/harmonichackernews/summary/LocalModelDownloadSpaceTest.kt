package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class LocalModelDownloadSpaceTest {
    private val model = LocalModelCatalog.embeddingModel.copy(sizeBytes = 4 * GIB)

    @Test
    fun nativeResumeDoesNotRequireSpaceForBytesAlreadyRetainedByTheOs() = runTest {
        val transfers = Transfers(receivedBytes = 3 * GIB)
        val result = lifecycle(partial = 0, free = 2 * GIB, transfers).requestDownload(model.id)
        assertEquals(LocalModelDownloadResult.Started, result)
        assertTrue(transfers.enqueued)
    }

    @Test
    fun legacyPartialReservesTheTemporaryRemainderAndItsInstallationCopy() = runTest {
        val transfers = Transfers(receivedBytes = 2 * GIB)
        val result = lifecycle(partial = 2 * GIB, free = 3 * GIB, transfers).requestDownload(model.id)
        assertEquals(LocalModelDownloadResult.NotEnoughSpace(4 * GIB + BUFFER), result)
        assertFalse(transfers.enqueued)
    }

    @Test
    fun legacyPartialStartsWhenPeakAllocationAndBufferFit() = runTest {
        val transfers = Transfers(receivedBytes = 2 * GIB)
        val result = lifecycle(partial = 2 * GIB, free = 4 * GIB + BUFFER, transfers).requestDownload(model.id)
        assertEquals(LocalModelDownloadResult.Started, result)
    }

    @Test
    fun resumingTheRemainderStillReservesTheWholeInstallationCopy() {
        assertEquals(3 * GIB, LocalModelDownloadSpacePolicy.backgroundFileBytes(4 * GIB, 2 * GIB, 3 * GIB))
    }

    @Test
    fun serverIgnoringRangeCanDeliverAFullReplacement() {
        assertEquals(4 * GIB, LocalModelDownloadSpacePolicy.backgroundFileBytes(4 * GIB, 3 * GIB, 3 * GIB))
    }

    @Test
    fun absentResumeDataRequiresSpaceForTheFullFreshDownload() = runTest {
        val transfers = Transfers(receivedBytes = 0)
        val result = lifecycle(partial = 0, free = 2 * GIB, transfers).requestDownload(model.id)
        assertEquals(LocalModelDownloadResult.NotEnoughSpace(4 * GIB + BUFFER), result)
        assertFalse(transfers.enqueued)
    }

    @Test
    fun streamingSchedulersKeepTheirExistingSingleCopySpaceRequirement() = runTest {
        val transfers = object : LocalModelTransferScheduler {
            override fun work(modelId: String): LocalModelWorkSnapshot? = null
            override fun isActive(modelId: String) = false
            override fun enqueue(model: LocalModelDefinition) = Unit
            override fun cancel(modelId: String, onCancelled: () -> Unit) = onCancelled()
        }
        val result = lifecycle(partial = 2 * GIB, free = 3 * GIB, transfers).requestDownload(model.id)
        assertEquals(LocalModelDownloadResult.Started, result)
    }

    private fun TestScope.lifecycle(partial: Long, free: Long, transfers: LocalModelTransferScheduler): LocalModelLifecycle {
        val storage = object : LocalModelStorage {
            override fun snapshot(model: LocalModelDefinition) = LocalModelStorageSnapshot(
                partialFileBytes = partial, usableSpaceBytes = free,
            )
            override fun prepareDownload(model: LocalModelDefinition) = LocalModelStoragePreparation.Ready(snapshot(model))
            override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) = Unit
            override fun installedPath(model: LocalModelDefinition) = "unused"
            override fun storedBytes() = partial
            override fun clearStoredModels() = true
        }
        return LocalModelLifecycle(
            models = listOf(model),
            stateStore = LocalModelStateStore(listOf(model), TestKeyValueStore(), "model", model.id),
            storage = storage,
            transfers = transfers,
            capabilities = LocalModelDeviceCapabilities(true, true),
            storageDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private class Transfers(private val receivedBytes: Long) : LocalModelTransferScheduler {
        var enqueued = false
        override fun work(modelId: String): LocalModelWorkSnapshot? = null
        override fun isActive(modelId: String) = false
        override fun enqueue(model: LocalModelDefinition) { enqueued = true }
        override fun cancel(modelId: String, onCancelled: () -> Unit) = onCancelled()
        override fun requiredSpaceBytes(model: LocalModelDefinition, partialFileBytes: Long) =
            LocalModelDownloadSpacePolicy.backgroundFileBytes(model.sizeBytes, partialFileBytes, receivedBytes)
    }

    private companion object {
        const val GIB = 1_024L * 1_024L * 1_024L
        const val BUFFER = LocalModelLifecycle.DEFAULT_STORAGE_BUFFER_BYTES
    }
}
