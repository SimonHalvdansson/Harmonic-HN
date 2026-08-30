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
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Owns the desktop model downloader, llama.cpp bridge, and their process lifetime. */
class DesktopLocalAiEnvironment private constructor(
    val models: LocalModelService,
    val summary: LocalSummaryEngine,
    private val scope: CoroutineScope,
    private val transferClient: HttpClient,
) : AutoCloseable {
    override fun close() {
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
            val transferClient = createHarmonicHttpClient(CIO.create(), userAgent)
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
                val nativeLibrary = DesktopLlamaNativeLibrary(cacheRoot.resolve("local-ai-runtime"))
                val runtimeDelivery = DesktopLocalRuntimeDelivery(nativeLibrary)
                val models = LocalModelService(
                    preferences = preferences,
                    storage = DesktopSwitchableLocalModelStorage(
                        location = storageLocation,
                        inferenceCacheRoot = KotlinPath(cacheRoot.toString()),
                    ),
                    transfers = transfers,
                    runtimeDelivery = runtimeDelivery,
                    capabilities = LocalModelDeviceCapabilities(
                        supportsDownloadableModels = DesktopNativePlatform.supported,
                        supportsLiteRtModels = false,
                        liteRtUnsupportedReason = LocalModelUnsupportedReason.RUNTIME_UNAVAILABLE,
                    ),
                    storageLocation = storageLocation,
                )
                DesktopLocalAiEnvironment(
                    models = models,
                    summary = DesktopLocalSummaryEngine(
                        models = models,
                        nativeLibrary = nativeLibrary,
                        totalMemoryBytes = ::desktopTotalMemoryBytes,
                    ),
                    scope = scope,
                    transferClient = transferClient,
                )
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

private class DesktopLocalRuntimeDelivery(
    private val nativeLibrary: DesktopLlamaNativeLibrary,
) : LocalModelRuntimeDelivery {
    private var observer: () -> Unit = {}
    private var downloadStarter: (String) -> String? = { "Model downloads are not ready." }

    override val included: Boolean = true

    override fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus = when (runtime) {
        LocalModelRuntime.LLAMA_CPP -> nativeLibrary.availability().fold(
            onSuccess = {
                LocalRuntimeInstallStatus(
                    state = LocalRuntimeInstallState.INSTALLED,
                    runtime = runtime,
                )
            },
            onFailure = { error ->
                LocalRuntimeInstallStatus(
                    state = LocalRuntimeInstallState.FAILED,
                    runtime = runtime,
                    error = error.message.orEmpty(),
                )
            },
        )
        LocalModelRuntime.GEMINI_NANO,
        LocalModelRuntime.LITERT_LM,
        -> LocalRuntimeInstallStatus(
            state = LocalRuntimeInstallState.NOT_INSTALLED,
            runtime = runtime,
        )
    }

    override fun isInstalled(runtime: LocalModelRuntime): Boolean =
        runtime == LocalModelRuntime.LLAMA_CPP && nativeLibrary.availability().isSuccess

    override fun request(model: LocalModelDefinition): String? =
        if (model.runtime == LocalModelRuntime.LLAMA_CPP && isInstalled(model.runtime)) {
            downloadStarter(model.id)
        } else {
            "${runtimeLabel(model.runtime)} is not available on desktop."
        }

    override fun cancel(runtime: LocalModelRuntime) = Unit

    override fun setObserver(observer: () -> Unit) {
        this.observer = observer
        observer()
    }

    override fun setModelDownloadStarter(starter: (String) -> String?) {
        downloadStarter = starter
    }

    override fun engineClassName(runtime: LocalModelRuntime): String? = null

    override fun runtimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
        LocalModelRuntime.LITERT_LM -> "LiteRT-LM"
        LocalModelRuntime.LLAMA_CPP -> "llama.cpp"
    }
}

private class DesktopLocalSummaryEngine(
    private val models: LocalModelService,
    nativeLibrary: DesktopLlamaNativeLibrary,
    totalMemoryBytes: () -> Long,
) : LocalSummaryEngine {
    private val inference = DesktopLlamaInference(nativeLibrary, totalMemoryBytes)

    override fun canAttempt(): Boolean = DesktopNativePlatform.supported

    override suspend fun availability(): LocalSummaryAvailability {
        if (!DesktopNativePlatform.supported) {
            return LocalSummaryAvailability(
                available = false,
                downloadableFallbackRequired = false,
                statusMessage = "Local AI requires a 64-bit Windows, macOS, or Linux build",
            )
        }
        return inference.availability().fold(
            onSuccess = {
                LocalSummaryAvailability(
                    available = true,
                    downloadableFallbackRequired = true,
                    statusMessage = "Choose and download a llama.cpp model",
                )
            },
            onFailure = { error ->
                LocalSummaryAvailability(
                    available = false,
                    downloadableFallbackRequired = false,
                    statusMessage = error.message ?: "The desktop local AI runtime is unavailable",
                )
            },
        )
    }

    override suspend fun isAvailable(): Boolean = availability().available

    override fun isReady(): Boolean {
        val selected = models.selectedModel
        return selected.runtime == LocalModelRuntime.LLAMA_CPP &&
            models.isSupported(selected) && models.isDownloaded(selected) &&
            models.isRuntimeInstalled(selected.runtime)
    }

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        var result: SummaryResult? = null
        var debugInfo: String? = null
        summarizeEvents(request).collect { event ->
            when (event) {
                is StorySummaryEvent.DebugInfo -> debugInfo = event.value
                is StorySummaryEvent.Progress -> Unit
                is StorySummaryEvent.Success -> result = SummaryResult(event.text, debugInfo)
                is StorySummaryEvent.Failure -> error(event.message)
            }
        }
        return result ?: error("Local summary provider completed without a result")
    }

    override fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> = channelFlow {
        val content = LocalSummaryPreparation.prepareManagedText(request.text)
        if (!LocalSummaryPreparation.isLongEnough(content)) {
            send(StorySummaryEvent.Failure("Article is too short for local summarization"))
            return@channelFlow
        }
        try {
            val selected = models.selectedModel
            check(selected.runtime == LocalModelRuntime.LLAMA_CPP) {
                "Select a downloaded llama.cpp model in AI summarization settings"
            }
            check(models.isSupported(selected)) { models.unsupportedReason(selected) }
            check(models.isDownloaded(selected)) {
                "Download the selected local model before using it"
            }
            val summary = inference.summarize(
                model = selected,
                modelPath = models.installedPath(selected),
                systemInstruction = request.prompt
                    ?.takeIf(String::isNotBlank)
                    ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION,
                text = content,
                onProgress = { trySend(StorySummaryEvent.Progress(it)) },
                onLoaded = { loadMillis ->
                    trySend(
                        StorySummaryEvent.DebugInfo(
                            SummaryFormatting.formatLoadInfo(selected.displayName, loadMillis),
                            modelLoadMillis = loadMillis,
                        ),
                    )
                },
            )
            send(StorySummaryEvent.Success(summary))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val detail = error.message?.takeIf(String::isNotBlank) ?: "Unknown error"
            send(StorySummaryEvent.Failure("Local summarization failed: $detail"))
        }
    }
}

private class DesktopLlamaInference(
    private val nativeLibrary: DesktopLlamaNativeLibrary,
    private val totalMemoryBytes: () -> Long,
) {
    private val inferenceMutex = Mutex()

    fun availability(): Result<Unit> = nativeLibrary.availability()

    suspend fun summarize(
        model: LocalModelDefinition,
        modelPath: String,
        systemInstruction: String,
        text: String,
        onProgress: (String) -> Unit,
        onLoaded: (Long) -> Unit,
    ): String = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val api = nativeLibrary.requireApi()
            val engine = checkNotNull(api.harmonic_llama_create()) {
                "Could not create the llama.cpp inference engine"
            }
            try {
                val prepared = LocalSummaryPreparation.prepare(
                    text = text,
                    modelContextTokens = model.contextTokens,
                    totalMemoryBytes = totalMemoryBytes(),
                )
                val loadStartedAt = System.nanoTime()
                check(api.harmonic_llama_load(engine, modelPath, prepared.contextTokens) != 0) {
                    nativeError(api, engine, "Could not load the local model")
                }
                onLoaded((System.nanoTime() - loadStartedAt) / 1_000_000L)

                val generation = LocalSummaryGenerationPolicy.configuration(model.id)
                check(
                    api.harmonic_llama_start(
                        engine,
                        systemInstruction,
                        prepared.text,
                        generation.responsePrefix,
                        generation.maxOutputTokens,
                    ) != 0,
                ) { nativeError(api, engine, "Could not process the summary input") }

                val response = StringBuilder(generation.responsePrefix)
                val pieceBuffer = ByteArray(OUTPUT_PIECE_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val length = api.harmonic_llama_next_utf8(
                        engine,
                        pieceBuffer,
                        pieceBuffer.size,
                    )
                    if (length == 0) break
                    check(length > 0) { nativeError(api, engine, "Local model inference failed") }
                    response.append(pieceBuffer.decodeToString(0, length))
                    LocalSummaryGenerationPolicy.visibleOutput(response.toString())
                        ?.takeIf(String::isNotEmpty)
                        ?.let(onProgress)
                }
                nativeError(api, engine, "").takeIf(String::isNotBlank)?.let(::error)
                LocalSummaryGenerationPolicy.visibleOutput(response.toString())
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: error("The local model returned an empty summary")
            } finally {
                api.harmonic_llama_close(engine)
                api.harmonic_llama_destroy(engine)
            }
        }
    }

    private fun nativeError(
        api: HarmonicLlamaApi,
        engine: Pointer,
        fallback: String,
    ): String = api.harmonic_llama_last_error(engine)
        ?.getString(0, StandardCharsets.UTF_8.name())
        ?.takeIf(String::isNotBlank)
        ?: fallback

    private companion object {
        const val OUTPUT_PIECE_BUFFER_BYTES = 64 * 1024
    }
}

internal interface HarmonicLlamaApi : Library {
    fun harmonic_llama_backend_initialize(callback: Pointer?, userData: Pointer?)
    fun harmonic_llama_create(): Pointer?
    fun harmonic_llama_destroy(engine: Pointer)
    fun harmonic_llama_load(engine: Pointer, modelPath: String, contextTokens: Int): Int
    fun harmonic_llama_start(
        engine: Pointer,
        systemPrompt: String,
        userPrompt: String,
        responsePrefix: String,
        outputTokens: Int,
    ): Int
    fun harmonic_llama_next_utf8(engine: Pointer, buffer: ByteArray, capacity: Int): Int
    fun harmonic_llama_last_error(engine: Pointer): Pointer?
    fun harmonic_llama_close(engine: Pointer)
}

private class DesktopLlamaNativeLibrary(
    private val extractionRoot: Path,
) {
    @Volatile
    private var loaded: Result<HarmonicLlamaApi>? = null

    fun availability(): Result<Unit> = load().map { }

    fun requireApi(): HarmonicLlamaApi = load().getOrThrow()

    private fun load(): Result<HarmonicLlamaApi> {
        loaded?.let { return it }
        return synchronized(this) {
            loaded ?: runCatching {
                check(DesktopNativePlatform.supported) {
                    "Local AI requires a 64-bit Windows, macOS, or Linux build"
                }
                val libraryName = System.mapLibraryName(LIBRARY_BASE_NAME)
                val resourceName = "native/$libraryName"
                val classLoader = Thread.currentThread().contextClassLoader
                    ?: DesktopLlamaNativeLibrary::class.java.classLoader
                val bytes = classLoader.getResourceAsStream(resourceName)?.use { it.readBytes() }
                    ?: error("Desktop local AI runtime is missing from this app build")
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                val directory = extractionRoot.resolve(digest.take(16))
                val libraryPath = directory.resolve(libraryName)
                Files.createDirectories(directory)
                if (!Files.isRegularFile(libraryPath) || Files.size(libraryPath) != bytes.size.toLong()) {
                    val temporary = Files.createTempFile(directory, libraryName, ".tmp")
                    try {
                        Files.write(temporary, bytes)
                        try {
                            Files.move(
                                temporary,
                                libraryPath,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(
                                temporary,
                                libraryPath,
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        }
                    } finally {
                        Files.deleteIfExists(temporary)
                    }
                }
                Native.load(
                    libraryPath.toAbsolutePath().toString(),
                    HarmonicLlamaApi::class.java,
                    mapOf(Library.OPTION_STRING_ENCODING to StandardCharsets.UTF_8.name()),
                ).also { it.harmonic_llama_backend_initialize(null, null) }
            }.also { loaded = it }
        }
    }

    private companion object {
        const val LIBRARY_BASE_NAME = "harmonic-local-ai"
    }
}

private object DesktopNativePlatform {
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val architecture = System.getProperty("os.arch").orEmpty().lowercase()
    private val supportedOs = osName.contains("win") || osName.contains("mac") ||
        osName.contains("linux")
    private val supportedArchitecture = architecture == "amd64" || architecture == "x86_64" ||
        architecture == "aarch64" || architecture == "arm64"
    val supported: Boolean = supportedOs && supportedArchitecture
}

private fun desktopTotalMemoryBytes(): Long = runCatching {
    (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
        ?.totalMemorySize
        ?: 0L
}.getOrDefault(0L)
