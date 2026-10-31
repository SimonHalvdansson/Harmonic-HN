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
import io.ktor.client.engine.okhttp.OkHttp
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path as KotlinPath

internal class DesktopLocalModelStorageLocation(
    private val preferences: KeyValueStore,
    defaultRoot: Path,
    private val preferenceKey: String = STORAGE_DIRECTORY_KEY,
) : LocalModelStorageLocation {
    @Volatile
    private var root: Path = initialRoot(defaultRoot)

    override val directoryPath: String get() = root.toString()

    val javaPath: Path get() = root
    val kotlinPath: KotlinPath get() = KotlinPath(root.toString())

    override fun changeDirectory(path: String): String? {
        val candidate = runCatching {
            check(path.isNotBlank()) { "Choose a folder for local model files." }
            Path.of(path).toAbsolutePath().normalize().also(::prepare)
        }.getOrElse { error ->
            return error.message?.takeIf(String::isNotBlank)
                ?: "Could not use that folder for local model files."
        }
        root = candidate
        preferences.putString(preferenceKey, candidate.toString())
        return null
    }

    private fun initialRoot(defaultRoot: Path): Path {
        preferences.getString(preferenceKey)
            ?.takeIf(String::isNotBlank)
            ?.let { stored ->
                runCatching {
                    Path.of(stored).toAbsolutePath().normalize().also(::prepare)
                }.getOrNull()?.let { return it }
                preferences.remove(preferenceKey)
            }
        return defaultRoot.toAbsolutePath().normalize().also(::prepare)
    }

    private fun prepare(directory: Path) {
        Files.createDirectories(directory)
        check(Files.isDirectory(directory)) { "The selected model location is not a folder." }
        check(Files.isWritable(directory)) { "The selected model folder is not writable." }
    }

    companion object {
        const val STORAGE_DIRECTORY_KEY = "pref_ai_local_model_directory"
    }
}

private class DesktopSwitchableLocalModelStorage(
    private val location: DesktopLocalModelStorageLocation,
    private val inferenceCacheRoot: KotlinPath,
) : LocalModelStorage {
    override fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot =
        delegate().snapshot(model)

    override fun prepareDownload(model: LocalModelDefinition): LocalModelStoragePreparation =
        delegate().prepareDownload(model)

    override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) =
        delegate().remove(model, includeFinalFile)

    override fun installedPath(model: LocalModelDefinition): String =
        delegate().installedPath(model)

    override fun storedBytes(): Long = delegate().storedBytes()

    override fun clearStoredModels(): Boolean = delegate().clearStoredModels()

    private fun delegate(): FileLocalModelStorage {
        val root = location.javaPath
        return FileLocalModelStorage(
            root = KotlinPath(root.toString()),
            usableSpaceBytes = {
                runCatching { Files.getFileStore(root).usableSpace }.getOrDefault(0L)
            },
            inferenceCacheRoot = inferenceCacheRoot,
        )
    }
}

/** Owns the desktop model downloader, LiteRT-LM runtime, and their process lifetime. */
class DesktopLocalAiEnvironment private constructor(
    val models: LocalModelService,
    val summary: LocalSummaryEngine,
    private val scope: CoroutineScope,
    private val transferClient: HttpClient,
) : AutoCloseable {
    override fun close() {
        models.close()
        scope.cancel()
        transferClient.close()
    }

    companion object {
        fun create(
            preferences: KeyValueStore,
            modelsRoot: Path,
            cacheRoot: Path,
            userAgent: String,
        ): DesktopLocalAiEnvironment {
            Files.createDirectories(modelsRoot)
            Files.createDirectories(cacheRoot)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transferClient = createHarmonicHttpClient(OkHttp.create(), userAgent)
            return try {
                val storageLocation = DesktopLocalModelStorageLocation(
                    preferences = preferences,
                    defaultRoot = modelsRoot,
                )
                val transfers = DesktopLocalModelTransferScheduler(
                    scope = scope,
                    storageLocation = storageLocation,
                    downloads = ResumableDownloadService(
                        KtorTransferClient(KtorHttpClient(transferClient)),
                    ),
                )
                val apple = DesktopAppleIntelligence(cacheRoot)
                val runtimeDelivery = DesktopLocalRuntimeDelivery()
                val models = LocalModelService(
                    preferences = preferences,
                    storage = DesktopSwitchableLocalModelStorage(
                        location = storageLocation,
                        inferenceCacheRoot = KotlinPath(cacheRoot.toString()),
                    ),
                    transfers = transfers,
                    runtimeDelivery = runtimeDelivery,
                    capabilities = LocalModelDeviceCapabilities(
                        supportsDownloadableModels = true,
                        supportsLiteRtModels = DesktopLiteRtInference.supported,
                        liteRtUnsupportedReason = LocalModelUnsupportedReason.RUNTIME_UNAVAILABLE,
                    ),
                    models = buildList {
                        if (DesktopAppleIntelligence.isMac) add(DesktopAppleIntelligence.model)
                        addAll(LocalModelCatalog.models.filter { it.downloadable })
                    },
                    storageLocation = storageLocation,
                ).also { it.refresh() }
                DesktopLocalAiEnvironment(
                    models = models,
                    summary = DesktopLocalSummaryEngine(
                        models = models,
                        apple = apple,
                        liteRt = DesktopLiteRtInference(cacheRoot.toString()),
                    ),
                    scope = scope,
                    transferClient = transferClient,
                ).also { environment ->
                    scope.launch { environment.summary.availability() }
                }
            } catch (error: Throwable) {
                scope.cancel()
                transferClient.close()
                throw error
            }
        }
    }
}

private class DesktopLocalModelTransferScheduler(
    private val scope: CoroutineScope,
    private val storageLocation: DesktopLocalModelStorageLocation,
    private val downloads: ResumableDownloadService,
) : LocalModelTransferScheduler {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val snapshots = ConcurrentHashMap<String, LocalModelWorkSnapshot>()

    @Volatile
    private var observer: () -> Unit = {}

    override fun work(modelId: String): LocalModelWorkSnapshot? = snapshots[modelId]

    override fun isActive(modelId: String): Boolean = jobs[modelId]?.isActive == true

    override fun enqueue(model: LocalModelDefinition) {
        if (isActive(model.id)) return
        val modelsRoot = storageLocation.kotlinPath
        update(model.id, LocalModelWorkSnapshot(LocalModelWorkState.WAITING))
        val job = scope.launch {
            var lastPublishedAt = 0L
            var lastPublishedPercent = -1
            try {
                update(model.id, LocalModelWorkSnapshot(LocalModelWorkState.RUNNING))
                downloads.download(
                    url = model.url,
                    expectedBytes = model.sizeBytes,
                    destination = FileResumableDownloadDestination(
                        completed = LocalModelFilePolicy.completedPath(modelsRoot, model),
                        partial = LocalModelFilePolicy.partialPath(modelsRoot, model),
                    ),
                    onProgress = { progress ->
                        val now = System.currentTimeMillis()
                        val percent = localModelProgressPercent(
                            progress.bytesWritten,
                            model.sizeBytes,
                        )
                        if (now - lastPublishedAt >= PROGRESS_INTERVAL_MILLIS ||
                            percent != lastPublishedPercent ||
                            progress.bytesWritten == model.sizeBytes
                        ) {
                            update(
                                model.id,
                                LocalModelWorkSnapshot(
                                    LocalModelWorkState.RUNNING,
                                    receivedBytes = progress.bytesWritten,
                                ),
                            )
                            lastPublishedAt = now
                            lastPublishedPercent = percent
                        }
                    },
                )
                update(
                    model.id,
                    LocalModelWorkSnapshot(
                        LocalModelWorkState.FINISHED,
                        receivedBytes = model.sizeBytes,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                update(
                    model.id,
                    LocalModelWorkSnapshot(
                        LocalModelWorkState.FAILED,
                        receivedBytes = snapshots[model.id]?.receivedBytes ?: 0L,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "Model download failed",
                    ),
                )
            }
        }
        jobs[model.id] = job
        job.invokeOnCompletion { jobs.remove(model.id, job) }
    }

    override fun cancel(modelId: String, onCancelled: () -> Unit) {
        val job = jobs.remove(modelId)
        if (job == null) {
            snapshots.remove(modelId)
            onCancelled()
            observer()
            return
        }
        scope.launch {
            job.cancelAndJoin()
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
        if (jobs.values.any { it.isActive }) return
        snapshots.clear()
        observer()
    }

    private fun update(modelId: String, snapshot: LocalModelWorkSnapshot) {
        snapshots[modelId] = snapshot
        observer()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MILLIS = 500L
    }
}

private class DesktopLocalRuntimeDelivery : LocalModelRuntimeDelivery {
    private var observer: () -> Unit = {}
    private var downloadStarter: suspend (String) -> String? = { "Model downloads are not ready." }

    override val included: Boolean = true

    override fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus = when (runtime) {
        LocalModelRuntime.LITERT_LM -> LocalRuntimeInstallStatus(
            state = if (DesktopLiteRtInference.supported) LocalRuntimeInstallState.INSTALLED
                else LocalRuntimeInstallState.NOT_INSTALLED,
            runtime = runtime,
        )
        LocalModelRuntime.APPLE_FOUNDATION_MODELS,
        LocalModelRuntime.GEMINI_NANO,
        -> LocalRuntimeInstallStatus(
            state = LocalRuntimeInstallState.NOT_INSTALLED,
            runtime = runtime,
        )
    }

    override fun isInstalled(runtime: LocalModelRuntime): Boolean =
        when (runtime) {
            LocalModelRuntime.LITERT_LM -> DesktopLiteRtInference.supported
            LocalModelRuntime.APPLE_FOUNDATION_MODELS, LocalModelRuntime.GEMINI_NANO -> false
        }

    override suspend fun request(model: LocalModelDefinition): String? =
        if (model.downloadable && isInstalled(model.runtime)) {
            downloadStarter(model.id)
        } else {
            "${runtimeLabel(model.runtime)} is not available on desktop."
        }

    override fun cancel(runtime: LocalModelRuntime) = Unit

    override fun setObserver(observer: () -> Unit) {
        this.observer = observer
        observer()
    }

    override fun setModelDownloadStarter(starter: suspend (String) -> String?) {
        downloadStarter = starter
    }

    override fun engineClassName(runtime: LocalModelRuntime): String? = null

    override fun runtimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.APPLE_FOUNDATION_MODELS -> "Apple Intelligence"
        LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
        LocalModelRuntime.LITERT_LM -> "LiteRT-LM"
    }
}

private class DesktopLocalSummaryEngine(
    private val models: LocalModelService,
    private val apple: DesktopAppleIntelligence,
    private val liteRt: DesktopLiteRtInference,
) : LocalSummaryEngine {
    override fun canAttempt(): Boolean = DesktopLiteRtInference.supported || DesktopAppleIntelligence.isMac

    override suspend fun availability(): LocalSummaryAvailability = withContext(Dispatchers.IO) {
        if (!canAttempt()) {
            return@withContext LocalSummaryAvailability(
                available = false,
                downloadableFallbackRequired = false,
                statusMessage = "Local AI is unavailable on this desktop platform",
            )
        }
        models.preload()
        apple.refresh()
        LocalSummaryAvailability(
            available = true,
            downloadableFallbackRequired = !apple.available,
            baseModelName = "Apple Intelligence".takeIf { apple.available },
            statusMessage = if (DesktopAppleIntelligence.isMac) apple.status else "Choose and download a local model",
        )
    }

    override suspend fun isAvailable(): Boolean = availability().available

    override fun isReady(): Boolean {
        val selected = models.selectedModel
        return if (selected.runtime == LocalModelRuntime.APPLE_FOUNDATION_MODELS) apple.available
        else selected.downloadable && models.isSupported(selected) && models.isDownloaded(selected) &&
            models.isRuntimeInstalled(selected.runtime)
    }

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        var result: SummaryResult? = null
        var debugInfo: String? = null
        summarizeEvents(request).collect { event ->
            when (event) {
                is StorySummaryEvent.DebugInfo -> debugInfo = event.value
                is StorySummaryEvent.SourceInput -> Unit
                is StorySummaryEvent.Progress -> Unit
                is StorySummaryEvent.Success -> result = SummaryResult(event.text, debugInfo)
                is StorySummaryEvent.Failure -> error(event.message)
            }
        }
        return result ?: error("Local summary provider completed without a result")
    }

    override fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> = channelFlow {
        val content = if (request.preserveInput) request.text.trim()
            else LocalSummaryPreparation.prepareManagedText(request.text)
        if (!request.preserveInput && !LocalSummaryPreparation.isLongEnough(content)) {
            send(StorySummaryEvent.Failure("Article is too short for local summarization"))
            return@channelFlow
        }
        try {
            val selected = models.selectedModel
            val instruction = request.prompt?.takeIf(String::isNotBlank)
                ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION
            if (selected.runtime == LocalModelRuntime.APPLE_FOUNDATION_MODELS) {
                apple.refresh()
                check(apple.available) { apple.status }
                send(StorySummaryEvent.DebugInfo("Apple Intelligence"))
                val result = apple.summarize(instruction, content, preserveInput = request.preserveInput) {
                    trySend(StorySummaryEvent.Progress(it))
                }
                send(StorySummaryEvent.Success(result))
                return@channelFlow
            }
            check(models.isSupported(selected)) { models.unsupportedReason(selected) }
            check(models.isDownloaded(selected)) {
                "Download the selected local model before using it"
            }
            val onProgress: (String) -> Unit = { trySend(StorySummaryEvent.Progress(it)) }
            val onLoaded: (Long) -> Unit = { loadMillis ->
                trySend(StorySummaryEvent.DebugInfo(
                    SummaryFormatting.formatLoadInfo(selected.displayName, loadMillis),
                    modelLoadMillis = loadMillis,
                ))
            }
            val summary = liteRt.summarize(selected, models.installedPath(selected), instruction, content, onProgress, onLoaded, preserveInput = request.preserveInput)
            send(StorySummaryEvent.Success(summary))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val detail = error.message?.takeIf(String::isNotBlank) ?: "Unknown error"
            send(StorySummaryEvent.Failure("Local summarization failed: $detail"))
        }
    }
}
