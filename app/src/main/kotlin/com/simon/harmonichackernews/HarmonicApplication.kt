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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.simon.harmonichackernews.data.BufferedEditorDraftStorage
import com.simon.harmonichackernews.data.FileEditorDraftStorage
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
    internal val composition by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createAndroidAppComposition(this)
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
            preloadScope.launch { appComposition.localSummaryEngine?.availability() }
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
