@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.network.FileResumableDownloadDestination
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.network.KtorTransferClient
import com.simon.harmonichackernews.network.ResumableDownloadService
import com.simon.harmonichackernews.network.SummaryFormatting
import com.simon.harmonichackernews.network.createHarmonicHttpClient
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult
import com.simon.harmonichackernews.settings.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import kotlin.time.TimeSource

/** iOS owns the native runtime and foreground, resumable model transfers. */
class IosLocalAiEnvironment private constructor(
    val models: LocalModelService,
    val summary: LocalSummaryEngine,
    private val scope: CoroutineScope,
    private val transferClient: HttpClient,
) {
    fun close() {
        models.close()
        scope.cancel()
        transferClient.close()
    }

    companion object {
        val appleModel = LocalModelDefinition(
            "apple-intelligence", "Apple Intelligence", "System managed", "",
            LocalModelBrand.SYSTEM, "", "", 0L, false,
            LocalModelRuntime.APPLE_FOUNDATION_MODELS, 4096,
        )

        fun create(
            preferences: KeyValueStore,
            modelsDirectory: String,
            cacheDirectory: String,
            userAgent: String,
            apple: LocalSummaryEngine?,
            liteRt: IosLiteRtSummaryBridge,
        ): IosLocalAiEnvironment {
            val root = Path(modelsDirectory)
            val cache = Path(cacheDirectory)
            SystemFileSystem.createDirectories(root)
            SystemFileSystem.createDirectories(cache)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val client = createHarmonicHttpClient(Darwin.create(), userAgent)
            return try {
                // GGUF models and their llama.cpp runtime are deliberately absent on iOS.
                val catalog = listOf(appleModel) + LocalModelCatalog.models.filter {
                    it.runtime == LocalModelRuntime.LITERT_LM
                }
                val models = LocalModelService(
                    preferences = preferences,
                    storage = FileLocalModelStorage(
                        root = root,
                        usableSpaceBytes = {
                            (NSFileManager.defaultManager.attributesOfFileSystemForPath(
                                modelsDirectory, error = null,
                            )?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
                        },
                        inferenceCacheRoot = cache,
                        models = catalog,
                    ),
                    transfers = IosLocalModelTransfers(scope, root, ResumableDownloadService(
                        KtorTransferClient(KtorHttpClient(client)),
                    )),
                    runtimeDelivery = IosLocalRuntimeDelivery(),
                    capabilities = LocalModelDeviceCapabilities(true, true),
                    models = catalog,
                    scope = scope,
                )
                IosLocalAiEnvironment(
                    models, IosLocalSummaryEngine(models, apple, IosLiteRtInference(
                        liteRt, cacheDirectory, { NSProcessInfo.processInfo.physicalMemory.toLong() },
                    )), scope, client,
                )
            } catch (error: Throwable) {
                scope.cancel()
                client.close()
                throw error
            }
        }
    }
}

/** State and jobs are confined to Main; disk/network work runs on the shared worker. */
private class IosLocalModelTransfers(
    private val scope: CoroutineScope,
    private val root: Path,
    private val downloads: ResumableDownloadService,
) : LocalModelTransferScheduler {
    private val jobs = mutableMapOf<String, Job>()
    private val snapshots = mutableMapOf<String, LocalModelWorkSnapshot>()
    private var observer: () -> Unit = {}

    override fun work(modelId: String): LocalModelWorkSnapshot? = snapshots[modelId]
    override fun isActive(modelId: String): Boolean = jobs[modelId]?.let { !it.isCompleted } == true

    override fun enqueue(model: LocalModelDefinition) {
        if (isActive(model.id)) return
        update(model.id, LocalModelWorkSnapshot(LocalModelWorkState.WAITING))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                update(model.id, LocalModelWorkSnapshot(LocalModelWorkState.RUNNING))
                var lastProgress = TimeSource.Monotonic.markNow()
                var lastPercent = -1
                withContext(Dispatchers.Default) {
                    downloads.download(
                        url = model.url,
                        expectedBytes = model.sizeBytes,
                        destination = FileResumableDownloadDestination(
                            LocalModelFilePolicy.completedPath(root, model),
                            LocalModelFilePolicy.partialPath(root, model),
                        ),
                        onProgress = { progress ->
                            val percent = localModelProgressPercent(progress.bytesWritten, model.sizeBytes)
                            if (lastProgress.elapsedNow().inWholeMilliseconds >= 500 ||
                                percent != lastPercent || progress.bytesWritten == model.sizeBytes
                            ) {
                                withContext(Dispatchers.Main.immediate) {
                                    update(model.id, LocalModelWorkSnapshot(
                                        LocalModelWorkState.RUNNING, progress.bytesWritten,
                                    ))
                                }
                                lastProgress = TimeSource.Monotonic.markNow()
                                lastPercent = percent
                            }
                        },
                    )
                }
                update(model.id, LocalModelWorkSnapshot(LocalModelWorkState.FINISHED, model.sizeBytes))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                update(model.id, LocalModelWorkSnapshot(
                    LocalModelWorkState.FAILED,
                    snapshots[model.id]?.receivedBytes ?: 0L,
                    error.message?.takeIf(String::isNotBlank) ?: "Model download failed",
                ))
            }
        }
        jobs[model.id] = job
        job.invokeOnCompletion { jobs.remove(model.id) }
        job.start()
    }

    override fun cancel(modelId: String, onCancelled: () -> Unit) {
        scope.launch {
            jobs[modelId]?.cancelAndJoin()
            snapshots.remove(modelId)
            onCancelled()
            observer()
        }
    }

    override fun setObserver(observer: () -> Unit) {
        this.observer = observer
        observer()
    }

    override fun reset() {
        if (jobs.isNotEmpty()) return
        snapshots.clear()
        observer()
    }

    private fun update(id: String, snapshot: LocalModelWorkSnapshot) {
        snapshots[id] = snapshot
        observer()
    }
}

private class IosLocalRuntimeDelivery : LocalModelRuntimeDelivery {
    override val included = true
    override fun isInstalled(runtime: LocalModelRuntime): Boolean = runtime == LocalModelRuntime.LITERT_LM
    override fun status(runtime: LocalModelRuntime) = LocalRuntimeInstallStatus(
        if (isInstalled(runtime)) LocalRuntimeInstallState.INSTALLED else LocalRuntimeInstallState.NOT_INSTALLED,
        runtime = runtime,
    )
    override suspend fun request(model: LocalModelDefinition): String? = "This runtime is unavailable on iOS."
    override fun cancel(runtime: LocalModelRuntime) = Unit
    override fun setObserver(observer: () -> Unit) { observer() }
    override fun setModelDownloadStarter(starter: suspend (String) -> String?) = Unit
    override fun engineClassName(runtime: LocalModelRuntime): String? = null
    override fun runtimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.APPLE_FOUNDATION_MODELS -> "Apple Intelligence"
        LocalModelRuntime.LITERT_LM -> "LiteRT-LM"
        else -> "Unavailable"
    }
}

private class IosLocalSummaryEngine(
    private val models: LocalModelService,
    private val apple: LocalSummaryEngine?,
    private val liteRt: IosLiteRtInference,
) : LocalSummaryEngine {
    override suspend fun availability(): LocalSummaryAvailability {
        models.preload()
        val managed = apple?.availability()
        return LocalSummaryAvailability(
            available = true,
            downloadableFallbackRequired = managed?.available != true,
            baseModelName = "Apple Intelligence".takeIf { managed?.available == true },
            statusMessage = managed?.statusMessage,
        )
    }

    override suspend fun isAvailable() = true
    override fun isReady(): Boolean = models.selectedModel.let {
        if (!it.downloadable) apple?.isReady() == true
        else models.isSupported(it) && models.isDownloaded(it) && models.isRuntimeInstalled(it.runtime)
    }

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        var result: String? = null
        var debugInfo: String? = null
        summarizeEvents(request).collect { event ->
            when (event) {
                is StorySummaryEvent.DebugInfo -> debugInfo = event.value
                is StorySummaryEvent.SourceInput -> Unit
                is StorySummaryEvent.Progress -> Unit
                is StorySummaryEvent.Success -> result = event.text
                is StorySummaryEvent.Failure -> error(event.message)
            }
        }
        return SummaryResult(checkNotNull(result), debugInfo)
    }

    override fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> = channelFlow {
        try {
            require(request.preserveInput || LocalSummaryPreparation.isLongEnough(request.text.trim())) {
                "Article is too short for local summarization"
            }
            val model = models.selectedModel
            if (!model.downloadable) {
                checkNotNull(apple) { "Apple Intelligence is unavailable" }.summarizeEvents(request).collect { send(it) }
            } else {
                check(model.runtime == LocalModelRuntime.LITERT_LM && models.isSupported(model)) {
                    "This model is unavailable on iOS"
                }
                check(models.isDownloaded(model)) { "Download the selected local model before using it" }
                val summary = liteRt.summarize(
                    model, models.installedPath(model),
                    request.prompt?.takeIf(String::isNotBlank) ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION,
                    request.text,
                    preserveInput = request.preserveInput,
                    onProgress = { trySend(StorySummaryEvent.Progress(it)) },
                    onLoaded = { millis -> trySend(StorySummaryEvent.DebugInfo(
                        SummaryFormatting.formatLoadInfo(model.displayName, millis), modelLoadMillis = millis,
                    )) },
                )
                send(StorySummaryEvent.Success(summary))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            send(StorySummaryEvent.Failure("Local summarization failed: ${error.message ?: "Unknown error"}"))
        }
    }
}
