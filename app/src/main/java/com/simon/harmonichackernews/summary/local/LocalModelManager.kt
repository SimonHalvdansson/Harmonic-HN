package com.simon.harmonichackernews.summary.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager.Companion.getInstance
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.LocalModelBrand
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalModelStateStore
import com.simon.harmonichackernews.summary.LocalModelTransferState
import com.simon.harmonichackernews.summary.LocalModelWorkSnapshot
import com.simon.harmonichackernews.summary.LocalModelWorkState
import com.simon.harmonichackernews.summary.formatDecimalBytes
import com.simon.harmonichackernews.summary.localModelProgressPercent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max

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
    private const val STORAGE_BUFFER_BYTES = 256L * 1024L * 1024L

    val models: List<ModelInfo> = LocalModelCatalog.models.map { it.toModelInfo() }
    private val GEMINI_NANO: ModelInfo = models.first()

    private val listeners = CopyOnWriteArraySet<StatusListener>()
    private val currentWork = ConcurrentHashMap<String, WorkInfo>()
    private var appContext: Context? = null

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isModelSupported(model: ModelInfo): Boolean {
        if (!model.downloadable) {
            return true
        }
        return isSupported && (model.runtime != Runtime.LITERT_LM || Process.is64Bit())
    }

    fun getModelUnsupportedReason(model: ModelInfo): String {
        if (!model.downloadable || isModelSupported(model)) {
            return ""
        }
        if (!isSupported) {
            return "Requires Android 12 or newer"
        }
        return "Requires a 64-bit Android device"
    }

    fun getSelectedModel(context: Context): ModelInfo {
        return getModel(modelState(context).selectedModelId)
    }

    fun getModel(id: String?): ModelInfo = models.firstOrNull { it.id == id } ?: GEMINI_NANO

    fun selectModel(context: Context, modelId: String?) {
        val model = getModel(modelId)
        if (modelState(context).select(
                modelId = model.id,
                supported = isModelSupported(model),
                downloaded = isModelDownloaded(context, model),
            )
        ) notifyListeners()
    }

    fun clearSelectedModel(context: Context) {
        modelState(context).clearSelection()
        notifyListeners()
    }

    fun isSelectedModelDownloaded(context: Context): Boolean =
        isModelDownloaded(context, getSelectedModel(context))

    fun isModelDownloaded(context: Context, model: ModelInfo): Boolean {
        if (!model.downloadable) {
            return false
        }
        val file = getModelFile(context, model.id, model.fileName)
        return file.isFile && file.length() == model.sizeBytes
    }

    fun getSelectedModelPath(context: Context): String {
        val model = getSelectedModel(context)
        return getModelFile(context, model.id, model.fileName).absolutePath
    }

    fun getSelectedStatus(context: Context): Status {
        initialize(context)
        return getStatus(context, getSelectedModel(context))
    }

    fun getStatus(context: Context, model: ModelInfo): Status {
        initialize(context)
        val finalFile = getModelFile(context, model.id, model.fileName)
        val info = currentWork[model.id]
        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        val resolved = modelState(context).resolveStatus(
            modelId = model.id,
            finalFileBytes = finalFile.takeIf(File::isFile)?.length(),
            partialFileBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L,
            work = info?.toSharedSnapshot(),
        )
        return Status(
            model = model,
            state = resolved.state.toAndroidState(),
            receivedBytes = resolved.receivedBytes,
            error = resolved.error,
        )
    }

    fun downloadSelectedModel(context: Context): String? =
        downloadModel(context, getSelectedModel(context).id)

    fun downloadModel(context: Context, modelId: String?): String? {
        initialize(context)
        val model = getModel(modelId)
        if (!model.downloadable) {
            return model.displayName + " is built into supported devices."
        }
        if (!isModelSupported(model)) {
            return getModelUnsupportedReason(model) + "."
        }
        val finalFile = getModelFile(context, model.id, model.fileName)
        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        if (finalFile.isFile && finalFile.length() == model.sizeBytes) {
            return null
        }
        if (isDownloadForModelActive(model.id)) {
            return null
        }
        deleteObsoleteModelFiles(context, model)
        deleteInferenceCacheFiles(context, model)
        if (finalFile.exists()) {
            finalFile.delete()
        }

        val remainingBytes = max(0L, model.sizeBytes - partialFile.length())
        val root = getModelsRoot(context)
        if (!root.exists() && !root.mkdirs()) {
            return "Could not create local model storage."
        }
        if (root.usableSpace < remainingBytes + STORAGE_BUFFER_BYTES) {
            val requiredSpace = formatBytes(remainingBytes + STORAGE_BUFFER_BYTES)
            return "Not enough free space. ${model.displayName} needs $requiredSpace available."
        }

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
        return null
    }

    fun cancelDownload(context: Context, modelId: String?) {
        initialize(context)
        val model = getModel(modelId)
        getInstance(context).cancelUniqueWork(getWorkName(model.id))
            .result.addListener({
                deleteKnownModelFiles(context, model, false)
                currentWork.remove(model.id)
                notifyListeners()
            }, ContextCompat.getMainExecutor(context))
    }

    fun removeSelectedModel(context: Context) {
        removeModel(context, getSelectedModel(context).id)
    }

    fun removeModel(context: Context, modelId: String?) {
        initialize(context)
        val model = getModel(modelId)
        if (!model.downloadable) {
            return
        }
        if (isDownloadForModelActive(model.id)) {
            cancelDownload(context, model.id)
            return
        }
        deleteKnownModelFiles(context, model, true)
        if (model.id == getSelectedModel(context).id) {
            clearSelectedModel(context)
            return
        }
        notifyListeners()
    }

    fun addStatusListener(context: Context, listener: StatusListener) {
        initialize(context)
        listeners.add(listener)
        listener.onStatusChanged(getSelectedStatus(context))
    }

    fun removeStatusListener(listener: StatusListener) {
        listeners.remove(listener)
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
        notifyListeners()
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

    private fun LocalModelDefinition.toModelInfo(): ModelInfo = ModelInfo(
        id = id,
        displayName = displayName,
        parameterSize = parameterSize,
        quantization = quantization,
        iconResId = when (brand) {
            LocalModelBrand.GOOGLE -> R.drawable.model_logo_google
            LocalModelBrand.PRISM -> R.drawable.model_logo_prism
            LocalModelBrand.QWEN -> R.drawable.model_logo_qwen
            LocalModelBrand.NVIDIA -> R.drawable.model_logo_nvidia
            LocalModelBrand.MISTRAL -> R.drawable.model_logo_mistral
            LocalModelBrand.LIQUID -> R.drawable.model_logo_liquid
        },
        fileName = fileName,
        url = url,
        sizeBytes = sizeBytes,
        downloadable = downloadable,
        runtime = when (runtime) {
            LocalModelRuntime.GEMINI_NANO -> Runtime.GEMINI_NANO
            LocalModelRuntime.LITERT_LM -> Runtime.LITERT_LM
            LocalModelRuntime.LLAMA_CPP -> Runtime.LLAMA_CPP
        },
        contextTokens = contextTokens,
    )

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

    private fun LocalModelTransferState.toAndroidState(): State = when (this) {
        LocalModelTransferState.NOT_DOWNLOADED -> State.NOT_DOWNLOADED
        LocalModelTransferState.PARTIALLY_DOWNLOADED -> State.PARTIALLY_DOWNLOADED
        LocalModelTransferState.WAITING -> State.WAITING
        LocalModelTransferState.DOWNLOADING -> State.DOWNLOADING
        LocalModelTransferState.DOWNLOADED -> State.DOWNLOADED
        LocalModelTransferState.FAILED -> State.FAILED
    }

    private fun getWorkName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"

    private fun deleteKnownModelFiles(
        context: Context,
        model: ModelInfo,
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

    private fun deleteObsoleteModelFiles(context: Context, model: ModelInfo) {
        val finalFile = getModelFile(context, model.id, model.fileName)
        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        val modelDir = finalFile.parentFile ?: return
        modelDir.listFiles()?.forEach { file ->
            if (file.isFile && file != finalFile && file != partialFile) {
                file.delete()
            }
        }
    }

    private fun deleteInferenceCacheFiles(context: Context, model: ModelInfo) {
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

    private fun notifyListeners() {
        val context = appContext ?: return
        val status = getStatus(context, getSelectedModel(context))
        listeners.forEach { it.onStatusChanged(status) }
    }

    fun interface StatusListener {
        fun onStatusChanged(status: Status)
    }

    enum class State {
        NOT_DOWNLOADED,
        PARTIALLY_DOWNLOADED,
        WAITING,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }

    enum class Runtime {
        GEMINI_NANO,
        LITERT_LM,
        LLAMA_CPP
    }

    class ModelInfo(
        val id: String,
        val displayName: String,
        val parameterSize: String,
        val quantization: String,
        @DrawableRes val iconResId: Int,
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val downloadable: Boolean,
        val runtime: Runtime,
        val contextTokens: Int,
    )

    class Status(
        val model: ModelInfo,
        val state: State,
        val receivedBytes: Long,
        val error: String,
    ) {
        val progressPercent: Int
            get() = localModelProgressPercent(receivedBytes, model.sizeBytes)
    }
}
