package com.simon.harmonichackernews

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.allowPartialImage
import coil3.svg.SvgDecoder
import com.simon.harmonichackernews.data.BufferedEditorDraftStorage
import com.simon.harmonichackernews.data.FileEditorDraftStorage
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.app.AppBootstrapPolicy
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.summary.local.createAndroidLocalRuntimeDelivery
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.GeminiNanoSummaryMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path

/** Application-level configuration for libraries that require process-wide coordination.  */
class HarmonicApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    private val localAiSupport: LocalAiApplicationSupport = LocalAiApplicationSupportImpl()
    private val draftScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val editorDrafts = mutableMapOf<String, BufferedEditorDraftStorage>()

    internal fun editorDraft(id: String): BufferedEditorDraftStorage =
        editorDrafts.getOrPut(id) {
            BufferedEditorDraftStorage(
                FileEditorDraftStorage(
                    Path(noBackupFilesDir.absolutePath, "editor-drafts", id),
                ),
                draftScope,
                inlineLimit = 8 * 1024,
                dispatcher = Dispatchers.IO,
            )
        }

    internal fun discardEditorDraft(id: String) {
        editorDrafts.remove(id)?.clear()
    }

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var deferredServicesStarted = false
    private lateinit var startup: AndroidAppStartup<HarmonicAppComposition>
    internal val composition: HarmonicAppComposition get() = startup.value
    internal val compositionOrNull: HarmonicAppComposition? get() = startup.valueOrNull

    override fun onCreate() {
        super.onCreate()
        // Register lifecycle callbacks on Main before the first activity can resume. This adapter
        // does not open storage or initialize the runtime; the remaining graph is prepared on IO.
        val runtimeDelivery = createAndroidLocalRuntimeDelivery(this)
        startup = AndroidAppStartup(preloadScope) {
            coroutineScope {
                listOf(
                    AndroidKeyValueStore.defaults(this@HarmonicApplication),
                    AndroidKeyValueStore.global(this@HarmonicApplication),
                    AndroidKeyValueStore.named(this@HarmonicApplication, PreviewCachePolicy.STORE_NAME),
                    AndroidKeyValueStore.named(this@HarmonicApplication, StorageKeyPolicy.FILE_ACCESS_STORE),
                ).map { store -> async { store.awaitLoaded() } }.awaitAll()
            }
            createAndroidAppComposition(this@HarmonicApplication, preloadScope, runtimeDelivery)
        }
        startup.start()
    }

    /** Every entry point awaits the same graph without acquiring a blocking initialization lock. */
    internal suspend fun awaitStartup(): HarmonicAppComposition = startup.await()

    internal fun retryStartup() = startup.retry()

    internal suspend fun awaitWidgetPreferences() {
        awaitStartup()
        withContext(Dispatchers.IO) {
            AndroidKeyValueStore.named(this@HarmonicApplication, AppBootstrapPolicy.WIDGET_CONFIGURATION_STORE).awaitLoaded()
            AndroidKeyValueStore.named(this@HarmonicApplication, AppBootstrapPolicy.WIDGET_RUNTIME_STORE).awaitLoaded()
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        localAiSupport.install(this)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .allowPartialImage(false)
        .components {
            add(IncompleteImageRetryInterceptor())
            add(SvgDecoder.Factory(parser = AndroidCompatibleSvgParser))
            add(KtorNetworkFetcherFactory(concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() }))
        }
        .build()

    /** Called after the initial UI is drawn; background-only launches don't need AI warm-up. */
    @MainThread
    internal fun startDeferredServices() {
        if (deferredServicesStarted) return
        deferredServicesStarted = true
        val appComposition = composition
        preloadScope.launch {
            appComposition.aiSummarySettings.awaitSnapshot()
            appComposition.aiModelDefaults.ensureInitialDefault()
        }
        appComposition.localModels?.takeIf { it.isIncluded }?.let { models ->
            // Resume transfer observation without waiting for Gemini Nano IPC or a cloud request.
            preloadScope.launch {
                models.preload()
                withContext(Dispatchers.Main.immediate) { models.startMonitoring() }
            }
            preloadScope.launch {
                val engine = appComposition.localSummaryEngine ?: return@launch
                val useLora = appComposition.aiSummarySettings.awaitSnapshot().geminiNanoSummaryMode ==
                    GeminiNanoSummaryMode.THREE_BULLETS
                val availability = engine.availability(useLora)
                withContext(Dispatchers.Main.immediate) {
                    appComposition.aiSummarySettings.preferLocalModeByDefault(
                        localAvailable = availability.available &&
                            !availability.downloadableFallbackRequired && engine.isReady(useLora),
                    )
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setJobSchedulerJobIdRange(
                WORK_MANAGER_JOB_ID_MIN,
                WORK_MANAGER_JOB_ID_MAX
            )
            .build()

    companion object {
        private const val WORK_MANAGER_JOB_ID_MIN = 10000
        private const val WORK_MANAGER_JOB_ID_MAX = 20000
    }
}
