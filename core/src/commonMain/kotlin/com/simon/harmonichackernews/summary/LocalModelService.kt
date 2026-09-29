package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Platform delivery boundary for optional local-inference runtimes. */
interface LocalModelRuntimeDelivery {
    val included: Boolean
    fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus
    fun isInstalled(runtime: LocalModelRuntime): Boolean
    suspend fun request(model: LocalModelDefinition): String?
    fun cancel(runtime: LocalModelRuntime)
    fun setObserver(observer: () -> Unit)
    fun setModelDownloadStarter(starter: suspend (String) -> String?)
    fun clearModelDownloadError(modelId: String) = Unit
    fun engineClassName(runtime: LocalModelRuntime): String?
    fun runtimeLabel(runtime: LocalModelRuntime): String
}

/**
 * Application-scoped local-model service. It owns catalog policy, persisted selection, model
 * lifecycle, runtime/model sequencing, presentation inputs and observable state. Platforms only
 * implement storage, background transfer, and optional runtime delivery.
 */
class LocalModelService(
    preferences: KeyValueStore,
    private val storage: LocalModelStorage,
    private val transfers: LocalModelTransferScheduler,
    private val runtimeDelivery: LocalModelRuntimeDelivery,
    capabilities: LocalModelDeviceCapabilities,
    private val models: List<LocalModelDefinition> = LocalModelCatalog.models,
    selectionKey: String = SELECTED_MODEL_KEY,
    private val storageLocation: LocalModelStorageLocation? = null,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val stateStore = LocalModelStateStore(
        models = models,
        preferences = preferences,
        selectionKey = selectionKey,
        defaultModelId = LocalModelCatalog.MODEL_GEMINI_NANO,
    )
    private val lifecycle = LocalModelLifecycle(
        models = models,
        stateStore = stateStore,
        storage = storage,
        transfers = transfers,
        capabilities = capabilities,
        storageDispatcher = storageDispatcher,
    )
    private val mutableState = MutableStateFlow(
        LocalModelManagerState(selectedModelId = lifecycle.selectedModel.id),
    )
    private var monitoringTransfers = false
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val refreshMutex = Mutex()
    private val operationMutex = Mutex()
    private val refreshJob = scope.launch {
        for (ignored in refreshRequests) refreshState()
    }

    val state: StateFlow<LocalModelManagerState>
        get() {
            ensureTransferMonitoring()
            return mutableState.asStateFlow()
        }
    /** Cached model state for UI entry paths that must never scan storage on the calling thread. */
    val cachedState: StateFlow<LocalModelManagerState> = mutableState.asStateFlow()
    val catalog: List<LocalModelDefinition> get() = models
    val selectedModel: LocalModelDefinition get() = lifecycle.selectedModel
    val isIncluded: Boolean get() = runtimeDelivery.included
    val isDownloadActive: Boolean
        get() = state.value.statuses.values.any {
            it.state == LocalModelTransferState.DOWNLOADING ||
                it.state == LocalModelTransferState.WAITING
        }
    val storageDirectoryPath: String? get() = storageLocation?.directoryPath

    init {
        runtimeDelivery.setModelDownloadStarter(::requestModelDownload)
    }

    fun model(id: String?): LocalModelDefinition = lifecycle.model(id)

    fun isSupported(model: LocalModelDefinition): Boolean = lifecycle.isSupported(model)

    fun unsupportedReason(model: LocalModelDefinition): String =
        when (lifecycle.unsupportedReason(model)) {
            LocalModelUnsupportedReason.PLATFORM_VERSION ->
                "Not supported by this operating-system version"
            LocalModelUnsupportedReason.PROCESS_ARCHITECTURE -> "Requires a 64-bit process"
            LocalModelUnsupportedReason.RUNTIME_UNAVAILABLE ->
                "Not supported by this platform's local AI runtime"
            null -> ""
        }

    fun isDownloaded(model: LocalModelDefinition): Boolean =
        cachedState.value.statuses[model.id]?.state == LocalModelTransferState.DOWNLOADED

    fun installedPath(model: LocalModelDefinition = selectedModel): String =
        lifecycle.installedPath(model)

    fun status(model: LocalModelDefinition): LocalModelTransferStatus {
        ensureTransferMonitoring()
        return cachedState.value.statuses[model.id]
            ?: LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED)
    }

    fun runtimeStatus(runtime: LocalModelRuntime): LocalRuntimeInstallStatus =
        runtimeDelivery.status(runtime)

    fun isRuntimeInstalled(runtime: LocalModelRuntime): Boolean =
        runtimeDelivery.isInstalled(runtime)

    fun engineClassName(runtime: LocalModelRuntime): String? =
        runtimeDelivery.engineClassName(runtime)

    fun runtimeLabel(runtime: LocalModelRuntime): String =
        runtimeDelivery.runtimeLabel(runtime)

    suspend fun select(modelId: String?): Boolean = operationMutex.withLock {
        lifecycle.select(modelId).also { selected -> if (selected) refresh() }
    }

    fun clearSelection() {
        lifecycle.clearSelection()
        refresh()
    }

    suspend fun requestRuntimeAndModelDownload(modelId: String?): String? {
        ensureTransferMonitoring()
        val model = model(modelId)
        if (!model.downloadable) return "${model.displayName} is system managed."
        if (!isSupported(model)) return unsupportedReason(model) + "."

        val requestedRuntime = runtimeStatus(model.runtime)
        if (requestedRuntime.active) {
            return if (requestedRuntime.pendingModelId == model.id) null
            else "Wait for the current local AI runtime installation to finish."
        }
        val anotherRuntimeActive = LocalModelRuntime.entries.any { runtime ->
            runtime != model.runtime && runtimeStatus(runtime).active
        }
        if (anotherRuntimeActive) {
            return "Wait for the current local AI runtime installation to finish."
        }

        return if (isRuntimeInstalled(model.runtime)) requestModelDownload(model.id)
        else runtimeDelivery.request(model)
    }

    suspend fun requestModelDownload(modelId: String?): String? = operationMutex.withLock {
        ensureTransferMonitoring()
        val model = model(modelId)
        val message = when (val result = lifecycle.requestDownload(model.id)) {
            LocalModelDownloadResult.Started,
            LocalModelDownloadResult.AlreadyDownloaded,
            LocalModelDownloadResult.AlreadyActive,
            -> null
            LocalModelDownloadResult.BuiltIn -> "${model.displayName} is system managed."
            is LocalModelDownloadResult.Unsupported -> unsupportedReason(model) + "."
            is LocalModelDownloadResult.NotEnoughSpace ->
                "Not enough free space. ${model.displayName} needs " +
                    "${formatDecimalBytes(result.requiredBytes)} available."
            is LocalModelDownloadResult.StorageFailure -> result.message
        }
        if (message == null) runtimeDelivery.clearModelDownloadError(model.id)
        refresh()
        message
    }

    suspend fun cancel(modelId: String?) = operationMutex.withLock {
        ensureTransferMonitoring()
        val model = model(modelId)
        val runtime = runtimeStatus(model.runtime)
        if (runtime.active && runtime.pendingModelId == model.id) {
            runtimeDelivery.cancel(model.runtime)
            refresh()
        } else {
            lifecycle.cancel(model.id, ::refresh)
        }
    }

    suspend fun remove(modelId: String?) = operationMutex.withLock {
        ensureTransferMonitoring()
        lifecycle.remove(modelId, ::refresh)
    }

    fun storedModelBytes(): Long = storage.storedBytes().coerceAtLeast(0L)

    /** Downloaded or partial model files that would be removed by [clearStoredModels]. */
    fun storedModelNames(): List<String> = models
        .asSequence()
        .filter(LocalModelDefinition::downloadable)
        .filter { model ->
            val snapshot = storage.snapshot(model)
            (snapshot.finalFileBytes ?: 0L) > 0L || snapshot.partialFileBytes > 0L
        }
        .map(LocalModelDefinition::displayName)
        .toList()

    suspend fun clearStoredModels(): Boolean = operationMutex.withLock {
        ensureTransferMonitoring()
        models.filter(LocalModelDefinition::downloadable).map(LocalModelDefinition::runtime).distinct().forEach { runtime ->
            // Cancel a pending model handoff too, even if its runtime has just finished installing.
            runtimeDelivery.cancel(runtime)
        }
        models.filter(LocalModelDefinition::downloadable).forEach { model ->
            suspendCancellableCoroutine { continuation ->
                transfers.cancel(model.id) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        val cleared = withContext(storageDispatcher) { storage.clearStoredModels() }
        if (cleared) lifecycle.clearSelection()
        transfers.reset()
        refresh()
        cleared
    }

    suspend fun changeStorageDirectory(path: String): String? = operationMutex.withLock {
        ensureTransferMonitoring()
        if (models.any { transfers.isActive(it.id) }) {
            return@withLock "Wait for the current model download to finish."
        }
        val location = storageLocation
            ?: return@withLock "Choosing a model folder is not supported on this platform."
        val error = withContext(storageDispatcher) { location.changeDirectory(path) }
        if (error == null) {
            transfers.reset()
            firstReadyDownloadableModel()?.let { lifecycle.select(it.id) } ?: lifecycle.clearSelection()
            refresh()
        }
        error
    }

    suspend fun firstReadyDownloadableModel(): LocalModelDefinition? = models.firstOrNull { model ->
        model.downloadable && isSupported(model) && lifecycle.isDownloaded(model) &&
            isRuntimeInstalled(model.runtime)
    }

    suspend fun selectFirstReadyOrClear() {
        firstReadyDownloadableModel()?.let { select(it.id) } ?: clearSelection()
    }

    fun presentation(
        model: LocalModelDefinition,
        nanoAvailabilityResolved: Boolean,
        nanoAvailable: Boolean,
        managerState: LocalModelManagerState = state.value,
    ): LocalModelPresentation {
        val runtimeStatus = managerState.runtimeStatuses[model.runtime]
            ?: LocalRuntimeInstallStatus(
                state = LocalRuntimeInstallState.NOT_INSTALLED,
                runtime = model.runtime,
            )
        return LocalModelPresentationPolicy.present(
            LocalModelPresentationInput(
                model = model,
                supported = isSupported(model),
                unsupportedReason = unsupportedReason(model),
                selected = managerState.selectedModelId == model.id,
                nanoAvailabilityResolved = nanoAvailabilityResolved,
                nanoAvailable = nanoAvailable,
                transferStatus = managerState.statuses[model.id]
                    ?: LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED),
                runtimeStatus = runtimeStatus,
                runtimeInstalled = runtimeStatus.state == LocalRuntimeInstallState.INSTALLED,
            ),
        )
    }

    /** Fills the application-scoped model cache, dispatching file inspection to the storage worker. */
    suspend fun preload() = refreshState()

    /** Registers platform observers after [preload] so screen entry never performs the first scan. */
    fun startMonitoring() = ensureTransferMonitoring()

    fun refresh() {
        mutableState.value = mutableState.value.copy(selectedModelId = selectedModel.id)
        ensureTransferMonitoring()
        refreshRequests.trySend(Unit)
    }

    fun close() {
        refreshRequests.close()
        refreshJob.cancel()
    }

    private fun ensureTransferMonitoring() {
        if (monitoringTransfers) return
        monitoringTransfers = true
        var registeringObservers = true
        val observer = { if (!registeringObservers) refreshRequests.trySend(Unit); Unit }
        transfers.setObserver(observer)
        runtimeDelivery.setObserver(observer)
        registeringObservers = false
        if (mutableState.value.statuses.size < models.size) refreshRequests.trySend(Unit)
    }

    private suspend fun refreshState() = refreshMutex.withLock {
        val statuses = models.associate { it.id to lifecycle.status(it) }
        mutableState.value = LocalModelManagerState(
            selectedModelId = selectedModel.id,
            statuses = statuses,
            runtimeStatuses = models
                .map(LocalModelDefinition::runtime)
                .distinct()
                .associateWith(runtimeDelivery::status),
        )
    }

    companion object {
        const val SELECTED_MODEL_KEY = "pref_ai_local_model"
    }
}
