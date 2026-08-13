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
import androidx.work.WorkManager
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelDeviceCapabilities
import com.simon.harmonichackernews.summary.LocalModelRuntimeDelivery
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.LocalModelStorage
import com.simon.harmonichackernews.summary.LocalModelStoragePreparation
import com.simon.harmonichackernews.summary.LocalModelStorageSnapshot
import com.simon.harmonichackernews.summary.LocalModelTransferScheduler
import com.simon.harmonichackernews.summary.LocalModelWorkSnapshot
import com.simon.harmonichackernews.summary.LocalModelWorkState
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Android storage and WorkManager adapters for the shared local-model service. */
internal fun createAndroidLocalModelService(context: Context): LocalModelService {
    val appContext = context.applicationContext
    val files = AndroidLocalModelStorage(appContext)
    val transfers = AndroidLocalModelTransferScheduler(appContext)
    return LocalModelService(
        preferences = AndroidKeyValueStore.defaults(appContext),
        storage = files,
        transfers = transfers,
        runtimeDelivery = createAndroidLocalRuntimeDelivery(appContext),
        capabilities = LocalModelDeviceCapabilities(
            supportsDownloadableModels = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            supportsLiteRtModels = Process.is64Bit(),
        ),
    )
}

internal fun modelFile(context: Context, modelId: String, fileName: String): File =
    File(modelsRoot(context), modelId).resolve(fileName)

internal fun partialModelFile(context: Context, modelId: String, fileName: String): File =
    File(modelsRoot(context), modelId).resolve("$fileName.download")

internal fun androidTotalMemoryBytes(context: Context): Long {
    val manager = context.getSystemService(ActivityManager::class.java) ?: return 0L
    return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
}

private fun modelsRoot(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return File(base, MODELS_DIRECTORY)
}

private class AndroidLocalModelStorage(
    private val context: Context,
) : LocalModelStorage {
    override fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot {
        val finalFile = modelFile(context, model.id, model.fileName)
        val partialFile = partialModelFile(context, model.id, model.fileName)
        return LocalModelStorageSnapshot(
            finalFileBytes = finalFile.takeIf(File::isFile)?.length(),
            partialFileBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L,
            usableSpaceBytes = modelsRoot(context).usableSpace,
        )
    }

    override fun prepareDownload(model: LocalModelDefinition): LocalModelStoragePreparation {
        deleteObsoleteModelFiles(model)
        deleteInferenceCacheFiles(model)
        val finalFile = modelFile(context, model.id, model.fileName)
        if (finalFile.exists() && !finalFile.delete()) {
            return LocalModelStoragePreparation.Failed(
                "Could not replace the incomplete local model.",
            )
        }
        val root = modelsRoot(context)
        if (!root.exists() && !root.mkdirs()) {
            return LocalModelStoragePreparation.Failed("Could not create local model storage.")
        }
        return LocalModelStoragePreparation.Ready(snapshot(model))
    }

    override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) {
        val partial = partialModelFile(context, model.id, model.fileName)
        if (partial.exists()) partial.delete()
        if (includeFinalFile) {
            val final = modelFile(context, model.id, model.fileName)
            if (final.exists()) final.delete()
            deleteInferenceCacheFiles(model)
        }
        partial.parentFile?.let { directory ->
            if (includeFinalFile) directory.listFiles().orEmpty()
                .filter(File::isFile)
                .forEach(File::delete)
            if (directory.list()?.isEmpty() == true) directory.delete()
        }
    }

    override fun installedPath(model: LocalModelDefinition): String =
        modelFile(context, model.id, model.fileName).absolutePath

    private fun deleteObsoleteModelFiles(model: LocalModelDefinition) {
        val final = modelFile(context, model.id, model.fileName)
        val partial = partialModelFile(context, model.id, model.fileName)
        final.parentFile?.listFiles()?.filter { it.isFile && it != final && it != partial }
            ?.forEach(File::delete)
    }

    private fun deleteInferenceCacheFiles(model: LocalModelDefinition) {
        val prefixes = buildList {
            add("${model.fileName}.xnnpack_cache_")
            when (model.id) {
                com.simon.harmonichackernews.summary.LocalModelCatalog.MODEL_E2B ->
                    add("gemma-4-E2B-it.litertlm.xnnpack_cache_")
                com.simon.harmonichackernews.summary.LocalModelCatalog.MODEL_QWEN_08B ->
                    add("Qwen3.5-0.8B-hybrid-exact-c2048.litertlm.xnnpack_cache_")
            }
        }
        context.cacheDir.listFiles()?.filter { file ->
            file.isFile && prefixes.any(file.name::startsWith)
        }?.forEach(File::delete)
    }
}

private class AndroidLocalModelTransferScheduler(
    private val context: Context,
) : LocalModelTransferScheduler {
    private val work = ConcurrentHashMap<String, WorkInfo>()
    private var observer: () -> Unit = {}
    private val manager = WorkManager.getInstance(context)

    init {
        com.simon.harmonichackernews.summary.LocalModelCatalog.models
            .filter(LocalModelDefinition::downloadable)
            .forEach { model ->
                manager.getWorkInfosForUniqueWorkLiveData(workName(model.id))
                    .observeForever { infos ->
                        val selected = infos.orEmpty().firstOrNull(::isActive)
                            ?: infos.orEmpty().firstOrNull { it.state == WorkInfo.State.FAILED }
                        if (selected == null) work.remove(model.id) else work[model.id] = selected
                        observer()
                    }
            }
    }

    override fun work(modelId: String): LocalModelWorkSnapshot? = work[modelId]?.let { info ->
        LocalModelWorkSnapshot(
            state = when (info.state) {
                WorkInfo.State.RUNNING -> LocalModelWorkState.RUNNING
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LocalModelWorkState.WAITING
                WorkInfo.State.FAILED -> LocalModelWorkState.FAILED
                else -> LocalModelWorkState.FINISHED
            },
            receivedBytes = info.progress.getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L),
            error = info.outputData.getString(LocalModelDownloadWorker.KEY_ERROR).orEmpty(),
        )
    }

    override fun isActive(modelId: String): Boolean = work[modelId]?.let(::isActive) == true

    override fun enqueue(model: LocalModelDefinition) {
        val input = Data.Builder()
            .putString(LocalModelDownloadWorker.KEY_MODEL_ID, model.id)
            .putString(LocalModelDownloadWorker.KEY_MODEL_NAME, model.displayName)
            .putString(LocalModelDownloadWorker.KEY_MODEL_URL, model.url)
            .putString(LocalModelDownloadWorker.KEY_FILE_NAME, model.fileName)
            .putLong(LocalModelDownloadWorker.KEY_EXPECTED_BYTES, model.sizeBytes)
            .build()
        val request = OneTimeWorkRequest.Builder(LocalModelDownloadWorker::class.java)
            .setInputData(input)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .addTag(model.id)
            .build()
        manager.enqueueUniqueWork(workName(model.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(modelId: String, onCancelled: () -> Unit) {
        manager.cancelUniqueWork(workName(modelId)).result.addListener({
            work.remove(modelId)
            onCancelled()
            observer()
        }, ContextCompat.getMainExecutor(context))
    }

    override fun setObserver(observer: () -> Unit) {
        this.observer = observer
        observer()
    }

    private fun isActive(info: WorkInfo): Boolean = when (info.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> true
        else -> false
    }

    private fun workName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"
}

internal interface AndroidLocalRuntimeDelivery : LocalModelRuntimeDelivery

private const val MODELS_DIRECTORY = "local_ai_models"
private const val WORK_NAME_PREFIX = "local_ai_model_download_"
