package com.simon.harmonichackernews.summary

data class LocalModelDeviceCapabilities(
    val supportsDownloadableModels: Boolean,
    val supportsLiteRtModels: Boolean,
)

enum class LocalModelUnsupportedReason {
    PLATFORM_VERSION,
    PROCESS_ARCHITECTURE,
}

fun LocalModelDeviceCapabilities.unsupportedReason(
    model: LocalModelDefinition,
): LocalModelUnsupportedReason? = when {
    !model.downloadable -> null
    !supportsDownloadableModels -> LocalModelUnsupportedReason.PLATFORM_VERSION
    model.runtime == LocalModelRuntime.LITERT_LM && !supportsLiteRtModels ->
        LocalModelUnsupportedReason.PROCESS_ARCHITECTURE
    else -> null
}

data class LocalModelStorageSnapshot(
    val finalFileBytes: Long? = null,
    val partialFileBytes: Long = 0L,
    val usableSpaceBytes: Long = 0L,
)

sealed interface LocalModelStoragePreparation {
    data class Ready(val snapshot: LocalModelStorageSnapshot) : LocalModelStoragePreparation
    data class Failed(val message: String) : LocalModelStoragePreparation
}

/** Filesystem operations required by the portable model lifecycle. */
interface LocalModelStorage {
    fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot
    fun prepareDownload(model: LocalModelDefinition): LocalModelStoragePreparation
    fun remove(model: LocalModelDefinition, includeFinalFile: Boolean)
    fun installedPath(model: LocalModelDefinition): String
}

/** Background-transfer operations required by the portable model lifecycle. */
interface LocalModelTransferScheduler {
    fun work(modelId: String): LocalModelWorkSnapshot?
    fun isActive(modelId: String): Boolean
    fun enqueue(model: LocalModelDefinition)
    fun cancel(modelId: String, onCancelled: () -> Unit)
    fun setObserver(observer: () -> Unit) = Unit
}

sealed interface LocalModelDownloadResult {
    data object Started : LocalModelDownloadResult
    data object AlreadyDownloaded : LocalModelDownloadResult
    data object AlreadyActive : LocalModelDownloadResult
    data object BuiltIn : LocalModelDownloadResult
    data class Unsupported(val reason: LocalModelUnsupportedReason) : LocalModelDownloadResult
    data class NotEnoughSpace(val requiredBytes: Long) : LocalModelDownloadResult
    data class StorageFailure(val message: String) : LocalModelDownloadResult
}

/**
 * Platform-neutral selection, support, storage-space and transfer lifecycle for local models.
 * Hosts provide file and background-work adapters, but do not duplicate lifecycle decisions.
 */
class LocalModelLifecycle(
    private val models: List<LocalModelDefinition>,
    private val stateStore: LocalModelStateStore,
    private val storage: LocalModelStorage,
    private val transfers: LocalModelTransferScheduler,
    private val capabilities: LocalModelDeviceCapabilities,
    private val storageBufferBytes: Long = DEFAULT_STORAGE_BUFFER_BYTES,
) {
    val selectedModel: LocalModelDefinition
        get() = model(stateStore.selectedModelId)

    fun model(id: String?): LocalModelDefinition =
        models.firstOrNull { it.id == id } ?: models.first()

    fun unsupportedReason(model: LocalModelDefinition): LocalModelUnsupportedReason? =
        capabilities.unsupportedReason(model)

    fun isSupported(model: LocalModelDefinition): Boolean = unsupportedReason(model) == null

    fun isDownloaded(model: LocalModelDefinition): Boolean =
        model.downloadable && storage.snapshot(model).finalFileBytes == model.sizeBytes

    fun select(modelId: String?): Boolean {
        val candidate = model(modelId)
        return stateStore.select(candidate.id, isSupported(candidate), isDownloaded(candidate))
    }

    fun clearSelection() = stateStore.clearSelection()

    fun status(model: LocalModelDefinition): LocalModelTransferStatus {
        val snapshot = storage.snapshot(model)
        return stateStore.resolveStatus(
            modelId = model.id,
            finalFileBytes = snapshot.finalFileBytes,
            partialFileBytes = snapshot.partialFileBytes,
            work = transfers.work(model.id),
        )
    }

    fun requestDownload(modelId: String?): LocalModelDownloadResult {
        val candidate = model(modelId)
        if (!candidate.downloadable) return LocalModelDownloadResult.BuiltIn
        unsupportedReason(candidate)?.let { return LocalModelDownloadResult.Unsupported(it) }
        if (isDownloaded(candidate)) return LocalModelDownloadResult.AlreadyDownloaded
        if (transfers.isActive(candidate.id)) return LocalModelDownloadResult.AlreadyActive

        val prepared = storage.prepareDownload(candidate)
        if (prepared is LocalModelStoragePreparation.Failed) {
            return LocalModelDownloadResult.StorageFailure(prepared.message)
        }
        val snapshot = (prepared as LocalModelStoragePreparation.Ready).snapshot
        val remainingBytes = (candidate.sizeBytes - snapshot.partialFileBytes).coerceAtLeast(0L)
        val requiredBytes = remainingBytes + storageBufferBytes
        if (snapshot.usableSpaceBytes < requiredBytes) {
            return LocalModelDownloadResult.NotEnoughSpace(requiredBytes)
        }
        transfers.enqueue(candidate)
        return LocalModelDownloadResult.Started
    }

    fun cancel(modelId: String?, onCancelled: () -> Unit = {}) {
        val candidate = model(modelId)
        transfers.cancel(candidate.id) {
            storage.remove(candidate, includeFinalFile = false)
            onCancelled()
        }
    }

    fun remove(modelId: String?, onChanged: () -> Unit = {}) {
        val candidate = model(modelId)
        if (!candidate.downloadable) return
        if (transfers.isActive(candidate.id)) {
            cancel(candidate.id, onChanged)
            return
        }
        storage.remove(candidate, includeFinalFile = true)
        if (candidate.id == selectedModel.id) clearSelection()
        onChanged()
    }

    fun installedPath(model: LocalModelDefinition = selectedModel): String =
        storage.installedPath(model)

    companion object {
        const val DEFAULT_STORAGE_BUFFER_BYTES: Long = 256L * 1024L * 1024L
    }
}
