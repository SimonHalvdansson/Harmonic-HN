package com.simon.harmonichackernews.summary.local

import android.annotation.SuppressLint
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
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.FileLocalModelStorage
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelDeviceCapabilities
import com.simon.harmonichackernews.summary.LocalModelRuntimeDelivery
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.LocalModelTransferScheduler
import com.simon.harmonichackernews.summary.LocalModelWorkSnapshot
import com.simon.harmonichackernews.summary.LocalModelWorkState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.io.files.Path

/** Android storage and WorkManager adapters for the shared local-model service. */
internal fun createAndroidLocalModelService(context: Context): LocalModelService {
    val appContext = context.applicationContext
    val modelsRoot = androidLocalModelsRoot(appContext)
    val files = FileLocalModelStorage(
        root = Path(modelsRoot.absolutePath),
        usableSpaceBytes = { immediatelyUsableSpaceBytes(modelsRoot) },
        inferenceCacheRoot = Path(appContext.cacheDir.absolutePath),
    )
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

/**
 * Reports space writable without evicting caches or reserving the model's entire size up front.
 * Downloads are resumable and surface write failures, so this is intentionally more conservative
 * than StorageManager.getAllocatableBytes().
 */
@SuppressLint("UsableSpace")
private fun immediatelyUsableSpaceBytes(directory: File): Long = directory.usableSpace

internal fun androidLocalModelsRoot(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return File(base, StorageKeyPolicy.LOCAL_MODELS_DIRECTORY)
}

internal fun androidTotalMemoryBytes(context: Context): Long {
    val manager = context.getSystemService(ActivityManager::class.java) ?: return 0L
    return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
}

private class AndroidLocalModelTransferScheduler(
    private val context: Context,
) : LocalModelTransferScheduler {
    private val work = ConcurrentHashMap<String, WorkInfo>()
    private var observer: () -> Unit = {}
    private val manager by lazy { WorkManager.getInstance(context) }
    private var observing = false

    private fun ensureObserving() {
        if (observing) return
        observing = true
        var registering = true
        com.simon.harmonichackernews.summary.LocalModelCatalog.models
            .filter(LocalModelDefinition::downloadable)
            .forEach { model ->
                manager.getWorkInfosForUniqueWorkLiveData(workName(model.id))
                    .observeForever { infos ->
                        val selected = infos.orEmpty().firstOrNull(::isActive)
                            ?: infos.orEmpty().firstOrNull { it.state == WorkInfo.State.FAILED }
                        if (selected == null) work.remove(model.id) else work[model.id] = selected
                        if (!registering) observer()
                    }
            }
        registering = false
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
        ensureObserving()
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
        ensureObserving()
        manager.cancelUniqueWork(workName(modelId)).result.addListener({
            work.remove(modelId)
            onCancelled()
            observer()
        }, ContextCompat.getMainExecutor(context))
    }

    override fun setObserver(observer: () -> Unit) {
        this.observer = observer
        ensureObserving()
        observer()
    }

    private fun isActive(info: WorkInfo): Boolean = when (info.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> true
        else -> false
    }

    private fun workName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"
}

internal interface AndroidLocalRuntimeDelivery : LocalModelRuntimeDelivery

private const val WORK_NAME_PREFIX = "local_ai_model_download_"
