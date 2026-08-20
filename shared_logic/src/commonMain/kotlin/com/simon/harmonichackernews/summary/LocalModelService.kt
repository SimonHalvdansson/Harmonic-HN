package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Platform delivery boundary for optional local-inference runtimes. */
interface LocalModelRuntimeDelivery {
    val included: Boolean
    fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus
    fun isInstalled(runtime: LocalModelRuntime): Boolean
    fun request(model: LocalModelDefinition): String?
    fun cancel(runtime: LocalModelRuntime)
    fun setObserver(observer: () -> Unit)
    fun setModelDownloadStarter(starter: (String) -> String?)
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
    storage: LocalModelStorage,
    private val transfers: LocalModelTransferScheduler,
    private val runtimeDelivery: LocalModelRuntimeDelivery,
    capabilities: LocalModelDeviceCapabilities,
    private val models: List<LocalModelDefinition> = LocalModelCatalog.models,
    selectionKey: String = SELECTED_MODEL_KEY,
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
    )
    private val mutableState = MutableStateFlow(
        LocalModelManagerState(selectedModelId = lifecycle.selectedModel.id),
    )
    private var monitoringTransfers = false

    val state: StateFlow<LocalModelManagerState>
        get() {
            ensureTransferMonitoring()
            return mutableState.asStateFlow()
        }
    val catalog: List<LocalModelDefinition> get() = models
    val selectedModel: LocalModelDefinition get() = lifecycle.selectedModel
    val isIncluded: Boolean get() = runtimeDelivery.included
    val isDownloadActive: Boolean
        get() = state.value.statuses.values.any {
            it.state == LocalModelTransferState.DOWNLOADING ||
                it.state == LocalModelTransferState.WAITING
        }

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
            null -> ""
        }

    fun isDownloaded(model: LocalModelDefinition): Boolean = lifecycle.isDownloaded(model)

    fun installedPath(model: LocalModelDefinition = selectedModel): String =
        lifecycle.installedPath(model)

    fun status(model: LocalModelDefinition): LocalModelTransferStatus {
        ensureTransferMonitoring()
        return lifecycle.status(model)
    }

    fun runtimeStatus(runtime: LocalModelRuntime): LocalRuntimeInstallStatus =
        runtimeDelivery.status(runtime)

    fun isRuntimeInstalled(runtime: LocalModelRuntime): Boolean =
        runtimeDelivery.isInstalled(runtime)

    fun engineClassName(runtime: LocalModelRuntime): String? =
        runtimeDelivery.engineClassName(runtime)

    fun runtimeLabel(runtime: LocalModelRuntime): String =
        runtimeDelivery.runtimeLabel(runtime)

    fun select(modelId: String?): Boolean = lifecycle.select(modelId).also { selected ->
        if (selected) refresh()
    }

    fun clearSelection() {
        lifecycle.clearSelection()
        refresh()
    }

    fun requestRuntimeAndModelDownload(modelId: String?): String? {
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

    fun requestModelDownload(modelId: String?): String? {
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
        refresh()
        return message
    }

    fun cancel(modelId: String?) {
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

    fun remove(modelId: String?) {
        ensureTransferMonitoring()
        lifecycle.remove(modelId, ::refresh)
    }

    fun firstReadyDownloadableModel(): LocalModelDefinition? = models.firstOrNull { model ->
        model.downloadable && isSupported(model) && isDownloaded(model) &&
            isRuntimeInstalled(model.runtime)
    }

    fun selectFirstReadyOrClear() {
        firstReadyDownloadableModel()?.let { select(it.id) } ?: clearSelection()
    }

    fun presentation(
        model: LocalModelDefinition,
        nanoAvailabilityResolved: Boolean,
        nanoAvailable: Boolean,
    ): LocalModelPresentation = LocalModelPresentationPolicy.present(
        LocalModelPresentationInput(
            model = model,
            supported = isSupported(model),
            unsupportedReason = unsupportedReason(model),
            selected = state.value.selectedModelId == model.id,
            nanoAvailabilityResolved = nanoAvailabilityResolved,
            nanoAvailable = nanoAvailable,
            transferStatus = state.value.statuses[model.id] ?: status(model),
            runtimeStatus = runtimeStatus(model.runtime),
            runtimeInstalled = isRuntimeInstalled(model.runtime),
        ),
    )

    fun refresh() {
        ensureTransferMonitoring()
        refreshState()
    }

    private fun ensureTransferMonitoring() {
        if (monitoringTransfers) return
        monitoringTransfers = true
        transfers.setObserver(::refreshState)
        runtimeDelivery.setObserver(::refreshState)
        refreshState()
    }

    private fun refreshState() {
        mutableState.value = LocalModelManagerState(
            selectedModelId = selectedModel.id,
            statuses = models.associate { it.id to lifecycle.status(it) },
        )
    }

    companion object {
        const val SELECTED_MODEL_KEY = "pref_ai_local_model"
    }
}
