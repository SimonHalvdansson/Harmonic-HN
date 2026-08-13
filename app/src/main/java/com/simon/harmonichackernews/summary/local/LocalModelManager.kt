package com.simon.harmonichackernews.summary.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager.Companion.getInstance
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelDeviceCapabilities
import com.simon.harmonichackernews.summary.LocalModelDownloadResult
import com.simon.harmonichackernews.summary.LocalModelLifecycle
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalModelStorage
import com.simon.harmonichackernews.summary.LocalModelStoragePreparation
import com.simon.harmonichackernews.summary.LocalModelStorageSnapshot
import com.simon.harmonichackernews.summary.LocalModelTransferScheduler
import com.simon.harmonichackernews.summary.LocalModelUnsupportedReason
import com.simon.harmonichackernews.summary.LocalModelManagerState
import com.simon.harmonichackernews.summary.LocalModelStateStore
import com.simon.harmonichackernews.summary.LocalModelTransferStatus
import com.simon.harmonichackernews.summary.LocalModelWorkSnapshot
import com.simon.harmonichackernews.summary.LocalModelWorkState
import com.simon.harmonichackernews.summary.formatDecimalBytes
import com.simon.harmonichackernews.summary.unsupportedReason
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Catalog and lifecycle for Gemini Nano and downloadable local LLMs.  */
object LocalModelManager {
    const val PREF_SELECTED_MODEL = "pref_ai_local_model"
    const val MODEL_GEMINI_NANO = LocalModelCatalog.MODEL_GEMINI_NANO
    const val MODEL_E2B = LocalModelCatalog.MODEL_E2B
    const val MODEL_E4B = LocalModelCatalog.MODEL_E4B
    const val MODEL_BONSAI_17B = LocalModelCatalog.MODEL_BONSAI_17B
    const val MODEL_BONSAI_4B = LocalModelCatalog.MODEL_BONSAI_4B
    const val MODEL_BONSAI_8B = LocalModelCatalog.MODEL_BONSAI_8B
    const val MODEL_QWEN_08B = LocalModelCatalog.MODEL_QWEN_08B
    const val MODEL_NEMOTRON_4B = LocalModelCatalog.MODEL_NEMOTRON_4B
    const val MODEL_MINISTRAL_3B = LocalModelCatalog.MODEL_MINISTRAL_3B
    const val MODEL_LFM_12B = LocalModelCatalog.MODEL_LFM_12B

    private const val WORK_NAME_PREFIX = "local_ai_model_download_"
    private const val MODELS_DIR = "local_ai_models"
    private const val LEGACY_E2B_FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val LEGACY_QWEN_EXACT_FILE_NAME = "Qwen3.5-0.8B-hybrid-exact-c2048.litertlm"

    val models: List<LocalModelDefinition> = LocalModelCatalog.models
    private val GEMINI_NANO: LocalModelDefinition = models.first()

    private val currentWork = ConcurrentHashMap<String, WorkInfo>()
    private val mutableState = MutableStateFlow(LocalModelManagerState())
    val state: StateFlow<LocalModelManagerState> = mutableState.asStateFlow()
    private var appContext: Context? = null

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isModelSupported(model: LocalModelDefinition): Boolean {
        return deviceCapabilities().unsupportedReason(model) == null
    }

    fun getModelUnsupportedReason(model: LocalModelDefinition): String {
        if (!model.downloadable || isModelSupported(model)) {
            return ""
        }
        return when (deviceCapabilities().unsupportedReason(model)) {
            LocalModelUnsupportedReason.PLATFORM_VERSION -> "Requires Android 12 or newer"
            LocalModelUnsupportedReason.PROCESS_ARCHITECTURE -> "Requires a 64-bit Android device"
            null -> ""
        }
    }

    fun getSelectedModel(context: Context): LocalModelDefinition {
        return lifecycle(context).selectedModel
    }

    fun getModel(id: String?): LocalModelDefinition =
        models.firstOrNull { it.id == id } ?: GEMINI_NANO

    fun selectModel(context: Context, modelId: String?) {
        if (lifecycle(context).select(modelId)) publishStatuses(context)
    }

    fun clearSelectedModel(context: Context) {
        lifecycle(context).clearSelection()
        publishStatuses(context)
    }

    fun isSelectedModelDownloaded(context: Context): Boolean =
        isModelDownloaded(context, getSelectedModel(context))

    fun isModelDownloaded(context: Context, model: LocalModelDefinition): Boolean {
        return lifecycle(context).isDownloaded(model)
    }

    fun getSelectedModelPath(context: Context): String {
        return lifecycle(context).installedPath()
    }

    fun getSelectedStatus(context: Context): LocalModelTransferStatus {
        initialize(context)
        return getStatus(context, getSelectedModel(context))
    }

    fun getStatus(context: Context, model: LocalModelDefinition): LocalModelTransferStatus {
        initialize(context)
        return lifecycle(context).status(model)
    }

    fun downloadSelectedModel(context: Context): String? =
        downloadModel(context, getSelectedModel(context).id)

    fun downloadModel(context: Context, modelId: String?): String? {
        initialize(context)
        val model = getModel(modelId)
        return when (val result = lifecycle(context).requestDownload(model.id)) {
            LocalModelDownloadResult.Started,
            LocalModelDownloadResult.AlreadyDownloaded,
            LocalModelDownloadResult.AlreadyActive,
            -> null
            LocalModelDownloadResult.BuiltIn ->
                model.displayName + " is built into supported devices."
            is LocalModelDownloadResult.Unsupported -> getModelUnsupportedReason(model) + "."
            is LocalModelDownloadResult.NotEnoughSpace ->
                "Not enough free space. ${model.displayName} needs " +
                    formatBytes(result.requiredBytes) + " available."
            is LocalModelDownloadResult.StorageFailure -> result.message
        }
    }

    fun cancelDownload(context: Context, modelId: String?) {
        initialize(context)
        lifecycle(context).cancel(modelId) { publishStatuses(context) }
    }

    fun removeSelectedModel(context: Context) {
        removeModel(context, getSelectedModel(context).id)
    }

    fun removeModel(context: Context, modelId: String?) {
        initialize(context)
        lifecycle(context).remove(modelId) { publishStatuses(context) }
    }

    fun states(context: Context): StateFlow<LocalModelManagerState> {
        initialize(context)
        return state
    }

    val isDownloadActive: Boolean
        get() = currentWork.values.any(::isActive)

    fun getTotalMemoryBytes(context: Context): Long {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0L
        val memoryInfo = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }

    fun formatBytes(bytes: Long): String = formatDecimalBytes(bytes)

    fun getModelFile(context: Context, modelId: String, fileName: String): File =
        File(File(getModelsRoot(context), modelId), fileName)

    fun getPartialModelFile(context: Context, modelId: String, fileName: String): File =
        File(File(getModelsRoot(context), modelId), "$fileName.download")

    private fun getModelsRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, MODELS_DIR)
    }

    @Synchronized
    private fun initialize(context: Context) {
        if (appContext != null) return

        val initializedContext = context.applicationContext
        appContext = initializedContext
        val workManager = getInstance(initializedContext)
        for (model in models) {
            if (model.downloadable) {
                workManager.getWorkInfosForUniqueWorkLiveData(getWorkName(model.id))
                    .observeForever { infos -> onWorkInfosChanged(model.id, infos) }
            }
        }
        publishStatuses(initializedContext)
    }

    private fun onWorkInfosChanged(modelId: String, infos: List<WorkInfo>?) {
        val workInfos = infos.orEmpty()
        val selected = workInfos.firstOrNull(::isActive)
            ?: workInfos.firstOrNull { it.state == WorkInfo.State.FAILED }
        if (selected == null) {
            currentWork.remove(modelId)
        } else {
            currentWork[modelId] = selected
        }
        appContext?.let(::publishStatuses)
    }

    private fun isDownloadForModelActive(modelId: String): Boolean =
        currentWork[modelId]?.let(::isActive) == true

    private fun isActive(info: WorkInfo): Boolean = when (info.state) {
        WorkInfo.State.RUNNING,
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED,
        -> true
        else -> false
    }

    private fun modelState(context: Context): LocalModelStateStore = LocalModelStateStore(
        models = LocalModelCatalog.models,
        preferences = AndroidKeyValueStore.defaults(context),
        selectionKey = PREF_SELECTED_MODEL,
        defaultModelId = MODEL_GEMINI_NANO,
    )

    private fun deviceCapabilities(): LocalModelDeviceCapabilities = LocalModelDeviceCapabilities(
        supportsDownloadableModels = isSupported,
        supportsLiteRtModels = Process.is64Bit(),
    )

    private fun lifecycle(context: Context): LocalModelLifecycle {
        val appContext = context.applicationContext
        return LocalModelLifecycle(
            models = models,
            stateStore = modelState(appContext),
            storage = object : LocalModelStorage {
                override fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot {
                    val finalFile = getModelFile(appContext, model.id, model.fileName)
                    val partialFile = getPartialModelFile(appContext, model.id, model.fileName)
                    return LocalModelStorageSnapshot(
                        finalFileBytes = finalFile.takeIf(File::isFile)?.length(),
                        partialFileBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L,
                        usableSpaceBytes = getModelsRoot(appContext).usableSpace,
                    )
                }

                override fun prepareDownload(
                    model: LocalModelDefinition,
                ): LocalModelStoragePreparation {
                    deleteObsoleteModelFiles(appContext, model)
                    deleteInferenceCacheFiles(appContext, model)
                    val finalFile = getModelFile(appContext, model.id, model.fileName)
                    if (finalFile.exists() && !finalFile.delete()) {
                        return LocalModelStoragePreparation.Failed(
                            "Could not replace the incomplete local model.",
                        )
                    }
                    val root = getModelsRoot(appContext)
                    if (!root.exists() && !root.mkdirs()) {
                        return LocalModelStoragePreparation.Failed(
                            "Could not create local model storage.",
                        )
                    }
                    return LocalModelStoragePreparation.Ready(snapshot(model))
                }

                override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) =
                    deleteKnownModelFiles(appContext, model, includeFinalFile)

                override fun installedPath(model: LocalModelDefinition): String =
                    getModelFile(appContext, model.id, model.fileName).absolutePath
            },
            transfers = object : LocalModelTransferScheduler {
                override fun work(modelId: String): LocalModelWorkSnapshot? =
                    currentWork[modelId]?.toSharedSnapshot()

                override fun isActive(modelId: String): Boolean =
                    isDownloadForModelActive(modelId)

                override fun enqueue(model: LocalModelDefinition) =
                    enqueueModelDownload(appContext, model)

                override fun cancel(modelId: String, onCancelled: () -> Unit) {
                    getInstance(appContext).cancelUniqueWork(getWorkName(modelId))
                        .result.addListener({
                            currentWork.remove(modelId)
                            onCancelled()
                        }, ContextCompat.getMainExecutor(appContext))
                }
            },
            capabilities = deviceCapabilities(),
        )
    }

    private fun enqueueModelDownload(context: Context, model: LocalModelDefinition) {
        val inputData = Data.Builder()
            .putString(LocalModelDownloadWorker.KEY_MODEL_ID, model.id)
            .putString(LocalModelDownloadWorker.KEY_MODEL_NAME, model.displayName)
            .putString(LocalModelDownloadWorker.KEY_MODEL_URL, model.url)
            .putString(LocalModelDownloadWorker.KEY_FILE_NAME, model.fileName)
            .putLong(LocalModelDownloadWorker.KEY_EXPECTED_BYTES, model.sizeBytes)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(LocalModelDownloadWorker::class.java)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(model.id)
            .build()
        getInstance(context).enqueueUniqueWork(
            getWorkName(model.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun WorkInfo.toSharedSnapshot(): LocalModelWorkSnapshot = LocalModelWorkSnapshot(
        state = when (state) {
            WorkInfo.State.RUNNING -> LocalModelWorkState.RUNNING
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            -> LocalModelWorkState.WAITING
            WorkInfo.State.FAILED -> LocalModelWorkState.FAILED
            else -> LocalModelWorkState.FINISHED
        },
        receivedBytes = progress.getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L),
        error = outputData.getString(LocalModelDownloadWorker.KEY_ERROR).orEmpty(),
    )

    private fun getWorkName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"

    private fun deleteKnownModelFiles(
        context: Context,
        model: LocalModelDefinition,
        includeFinalFile: Boolean,
    ) {
        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        if (partialFile.exists()) {
            partialFile.delete()
        }
        if (includeFinalFile) {
            val finalFile = getModelFile(context, model.id, model.fileName)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            deleteInferenceCacheFiles(context, model)
        }
        partialFile.parentFile?.let { modelDir ->
            if (includeFinalFile) {
                modelDir.listFiles().orEmpty().forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
            if (modelDir.list()?.isEmpty() == true) {
                modelDir.delete()
            }
        }
    }

    private fun deleteObsoleteModelFiles(context: Context, model: LocalModelDefinition) {
        val finalFile = getModelFile(context, model.id, model.fileName)
        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        val modelDir = finalFile.parentFile ?: return
        modelDir.listFiles()?.forEach { file ->
            if (file.isFile && file != finalFile && file != partialFile) {
                file.delete()
            }
        }
    }

    private fun deleteInferenceCacheFiles(context: Context, model: LocalModelDefinition) {
        val currentPrefix = "${model.fileName}.xnnpack_cache_"
        val legacyPrefix = when (model.id) {
            MODEL_E2B -> "$LEGACY_E2B_FILE_NAME.xnnpack_cache_"
            MODEL_QWEN_08B -> "$LEGACY_QWEN_EXACT_FILE_NAME.xnnpack_cache_"
            else -> ""
        }
        context.cacheDir.listFiles()?.forEach { cacheFile ->
            val name = cacheFile.name
            if (
                cacheFile.isFile &&
                (name.startsWith(currentPrefix) ||
                    (legacyPrefix.isNotEmpty() && name.startsWith(legacyPrefix)))
            ) {
                cacheFile.delete()
            }
        }
    }

    private fun publishStatuses(context: Context) {
        if (appContext == null) return
        mutableState.value = LocalModelManagerState(
            selectedModelId = getSelectedModel(context).id,
            statuses = models.associate { model -> model.id to getStatus(context, model) },
        )
    }
}
