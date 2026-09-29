package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalModelStorageDispatchTest {
    @Test
    fun monitoringAndModelActionsKeepDiskAccessOffTheOwnerThread() = runBlocking {
        val owner = Thread.currentThread()
        val diskThreads = CopyOnWriteArrayList<Thread>()
        var observer: () -> Unit = {}
        val model = LocalModelCatalog.models.first { it.downloadable }
        var downloaded = false
        val storage = object : LocalModelStorage {
            override fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot {
                diskThreads += Thread.currentThread()
                return LocalModelStorageSnapshot(model.sizeBytes.takeIf { downloaded }, usableSpaceBytes = Long.MAX_VALUE)
            }
            override fun prepareDownload(model: LocalModelDefinition): LocalModelStoragePreparation {
                diskThreads += Thread.currentThread()
                return LocalModelStoragePreparation.Ready(snapshot(model))
            }
            override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) {
                diskThreads += Thread.currentThread()
                downloaded = false
            }
            override fun installedPath(model: LocalModelDefinition) = "model.gguf"
            override fun storedBytes(): Long = 0
            override fun clearStoredModels(): Boolean {
                diskThreads += Thread.currentThread()
                downloaded = false
                return true
            }
        }
        val transfers = object : LocalModelTransferScheduler {
            override fun work(modelId: String): LocalModelWorkSnapshot? = null
            override fun isActive(modelId: String) = false
            override fun enqueue(model: LocalModelDefinition) { assertTrue(Thread.currentThread() === owner) }
            override fun cancel(modelId: String, onCancelled: () -> Unit) {
                assertTrue(Thread.currentThread() === owner)
                onCancelled()
            }
            override fun setObserver(value: () -> Unit) { observer = value }
        }
        val delivery = object : LocalModelRuntimeDelivery {
            override val included = true
            override fun status(runtime: LocalModelRuntime) = LocalRuntimeInstallStatus(LocalRuntimeInstallState.INSTALLED, runtime = runtime)
            override fun isInstalled(runtime: LocalModelRuntime) = true
            override suspend fun request(model: LocalModelDefinition): String? = null
            override fun cancel(runtime: LocalModelRuntime) = Unit
            override fun setObserver(observer: () -> Unit) = Unit
            override fun setModelDownloadStarter(starter: suspend (String) -> String?) = Unit
            override fun engineClassName(runtime: LocalModelRuntime): String? = null
            override fun runtimeLabel(runtime: LocalModelRuntime) = runtime.name
        }
        val service = LocalModelService(
            InMemoryKeyValueStore(), storage, transfers, delivery,
            LocalModelDeviceCapabilities(true, true), models = listOf(model), scope = this,
        )
        try {
            service.preload()
            service.startMonitoring()
            repeat(20) { observer() }
            service.requestModelDownload(model.id)
            downloaded = true
            assertTrue(service.select(model.id))
            service.remove(model.id)
            service.cancel(model.id)
            assertTrue(service.clearStoredModels())
            assertTrue(diskThreads.isNotEmpty())
            assertTrue(diskThreads.all { it !== owner })
        } finally {
            service.close()
        }
    }
}
