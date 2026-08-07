package com.simon.harmonichackernews.summary.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager.Companion.getInstance
import com.simon.harmonichackernews.R
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.min

/** Catalog and lifecycle for Gemini Nano and downloadable local LLMs.  */
object LocalModelManager {
    const val PREF_SELECTED_MODEL = "pref_ai_local_model"
    const val MODEL_GEMINI_NANO = "gemini-nano"
    const val MODEL_E2B = "gemma-4-e2b"
    const val MODEL_E4B = "gemma-4-e4b"
    const val MODEL_BONSAI_17B = "bonsai-1.7b"
    const val MODEL_BONSAI_4B = "bonsai-4b"
    const val MODEL_BONSAI_8B = "bonsai-8b"
    const val MODEL_QWEN_08B = "qwen-3.5-0.8b"
    const val MODEL_NEMOTRON_4B = "nemotron-3-nano-4b"
    const val MODEL_MINISTRAL_3B = "ministral-3-3b"
    const val MODEL_LFM_12B = "lfm-2.5-1.2b"

    private const val WORK_NAME_PREFIX = "local_ai_model_download_"
    private const val MODELS_DIR = "local_ai_models"
    private const val LEGACY_E2B_FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val LEGACY_QWEN_EXACT_FILE_NAME = "Qwen3.5-0.8B-hybrid-exact-c2048.litertlm"
    private const val STORAGE_BUFFER_BYTES = 256L * 1024L * 1024L

    private val GEMINI_NANO = ModelInfo(
        MODEL_GEMINI_NANO, "Gemini Nano (experimental)", "System managed", "",
        R.drawable.model_logo_google, "", "", 0L, false,
        Runtime.GEMINI_NANO, 0
    )
    private val E2B = ModelInfo(
        MODEL_E2B,
        "Gemma 4 E2B",
        "2B effective",
        "QAT 2/4/8-bit",
        R.drawable.model_logo_google,
        "gemma-4-E2B-it-qat-mobile.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/"
                + "361a4010ad6d88fc5c86e148e333c0342b99763d/gemma-4-E2B-it.litertlm?download=true",
        2588147712L,
        true,
        Runtime.LITERT_LM,
        4096
    )
    private val E4B = ModelInfo(
        MODEL_E4B,
        "Gemma 4 E4B",
        "4B effective",
        "4-bit per-channel",
        R.drawable.model_logo_google,
        "gemma-4-E4B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/"
                + "9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
        3654467584L,
        true,
        Runtime.LITERT_LM,
        4096
    )
    private val BONSAI_17B = ModelInfo(
        MODEL_BONSAI_17B,
        "Bonsai 1.7B",
        "1.7B",
        "Q1_0",
        R.drawable.model_logo_prism,
        "Bonsai-1.7B-Q1_0.gguf",
        ("https://huggingface.co/prism-ml/Bonsai-1.7B-gguf/resolve/"
                + "210a9e99f79cb184909d49595906526eb2b3dd9a/"
                + "Bonsai-1.7B-Q1_0.gguf?download=true"),
        248302272L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    private val BONSAI_4B = ModelInfo(
        MODEL_BONSAI_4B,
        "Bonsai 4B",
        "4B",
        "Q1_0",
        R.drawable.model_logo_prism,
        "Bonsai-4B-Q1_0.gguf",
        ("https://huggingface.co/prism-ml/Bonsai-4B-gguf/resolve/"
                + "78f2c2bacd0904ffaba24b4873ed975e5818354a/"
                + "Bonsai-4B-Q1_0.gguf?download=true"),
        572270624L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    private val BONSAI_8B = ModelInfo(
        MODEL_BONSAI_8B,
        "Bonsai 8B",
        "8B",
        "Q1_0",
        R.drawable.model_logo_prism,
        "Bonsai-8B-Q1_0.gguf",
        ("https://huggingface.co/prism-ml/Bonsai-8B-gguf/resolve/"
                + "48516770dd04643643e9f9019a2a349cf26c5dbd/"
                + "Bonsai-8B-Q1_0.gguf?download=true"),
        1158654496L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    private val QWEN_08B = ModelInfo(
        MODEL_QWEN_08B,
        "Qwen 3.5 0.8B",
        "0.8B",
        "Q4_K_M",
        R.drawable.model_logo_qwen,
        "Qwen3.5-0.8B-Q4_K_M.gguf",
        ("https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/"
                + "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/"
                + "Qwen3.5-0.8B-Q4_K_M.gguf?download=true"),
        532517120L,
        true,
        Runtime.LLAMA_CPP,
        2048
    )
    private val NEMOTRON_4B = ModelInfo(
        MODEL_NEMOTRON_4B,
        "Nemotron 3 Nano 4B",
        "4B",
        "Q4_K_M",
        R.drawable.model_logo_nvidia,
        "NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf",
        ("https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF/resolve/"
                + "18d83da545bdfde657afff71123d7ffc8965edfa/"
                + "NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf?download=true"),
        2837072864L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    private val MINISTRAL_3B = ModelInfo(
        MODEL_MINISTRAL_3B,
        "Ministral 3 3B",
        "3.4B",
        "Q4_K_M",
        R.drawable.model_logo_mistral,
        "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
        ("https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/"
                + "eb599d408350ea2bb60452cb86be7c7b2fc28227/"
                + "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf?download=true"),
        2147023008L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    private val LFM_12B = ModelInfo(
        MODEL_LFM_12B,
        "LFM2.5 1.2B",
        "1.2B",
        "Q4_K_M",
        R.drawable.model_logo_liquid,
        "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        ("https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/"
                + "047e06635fbe71469926b35ea414537245218200/"
                + "LFM2.5-1.2B-Instruct-Q4_K_M.gguf?download=true"),
        730895168L,
        true,
        Runtime.LLAMA_CPP,
        4096
    )
    val models: List<ModelInfo> = listOf(
        GEMINI_NANO,
        E2B,
        E4B,
        BONSAI_17B,
        BONSAI_4B,
        BONSAI_8B,
        QWEN_08B,
        NEMOTRON_4B,
        MINISTRAL_3B,
        LFM_12B,
    )

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
        val id = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_SELECTED_MODEL, MODEL_GEMINI_NANO)
            ?: MODEL_GEMINI_NANO
        return getModel(id)
    }

    fun getModel(id: String?): ModelInfo = models.firstOrNull { it.id == id } ?: GEMINI_NANO

    fun selectModel(context: Context, modelId: String?) {
        val model = getModel(modelId)
        if (!isModelSupported(model)
            || (model.downloadable && !isModelDownloaded(context, model))
        ) {
            return
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_SELECTED_MODEL, model.id)
            .apply()
        notifyListeners()
    }

    fun clearSelectedModel(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_SELECTED_MODEL)
            .apply()
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
        if (!model.downloadable) {
            return Status(model, State.NOT_DOWNLOADED, 0L, "")
        }

        val finalFile = getModelFile(context, model.id, model.fileName)
        if (finalFile.isFile && finalFile.length() == model.sizeBytes) {
            return Status(model, State.DOWNLOADED, model.sizeBytes, "")
        }

        val info = currentWork[model.id]
        if (info != null) {
            val received = info.progress
                .getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L)
            if (info.state == WorkInfo.State.RUNNING) {
                return Status(model, State.DOWNLOADING, received, "")
            }
            if (info.state == WorkInfo.State.ENQUEUED
                || info.state == WorkInfo.State.BLOCKED
            ) {
                return Status(model, State.WAITING, received, "")
            }
            if (info.state == WorkInfo.State.FAILED) {
                val error = info.outputData.getString(LocalModelDownloadWorker.KEY_ERROR)
                return Status(
                    model,
                    State.FAILED,
                    received,
                    error ?: "Model download failed",
                )
            }
        }

        val partialFile = getPartialModelFile(context, model.id, model.fileName)
        if (partialFile.isFile && partialFile.length() > 0L) {
            return Status(
                model,
                State.PARTIALLY_DOWNLOADED,
                partialFile.length(),
                "",
            )
        }
        return Status(model, State.NOT_DOWNLOADED, 0L, "")
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

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(Locale.US, "%.1f kB", bytes / 1_000.0)
    }

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
            get() {
                if (model.sizeBytes <= 0L) {
                    return 0
                }
                return min(100L, receivedBytes * 100L / model.sizeBytes).toInt()
            }
    }
}
