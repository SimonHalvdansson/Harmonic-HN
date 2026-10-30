package com.simon.harmonichackernews.summary.local

import android.app.Application
import android.content.Context
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalAiRuntimeStatusTest {
    @Test
    fun everyRuntimeCanBeQueriedBeforeStartingAnAndroidModelDownload() = withManager { context, manager, play, _ ->
        // LocalModelService checks every runtime for an active installation before downloading.
        for (runtime in LocalModelRuntime.entries) {
            val status = manager.getStatus(context, runtime)
            assertEquals(runtime, status.runtime)
            assertFalse(status.active)
            val builtIn = runtime == LocalModelRuntime.GEMINI_NANO
            assertEquals(builtIn, manager.isRuntimeInstalled(context, runtime))
            assertEquals(
                if (builtIn) LocalRuntimeInstallState.INSTALLED
                else LocalRuntimeInstallState.NOT_INSTALLED,
                status.state,
            )
        }

        val model = LocalModelCatalog.models.first {
            it.downloadable && it.runtime == LocalModelRuntime.LLAMA_CPP
        }
        assertNull(manager.requestRuntimeAndModelDownload(context, model))
        assertEquals(listOf("local_ai_runtime"), play.requests.single().moduleNames)
        assertEquals(LocalRuntimeInstallState.PENDING, manager.getStatus(context, model.runtime).state)
        assertEquals(1, play.packageQueries)
    }

    @Test
    fun legacyPendingDownloadResumesOnceAfterPreloadAndStarterRegistration() {
        val model = LocalModelCatalog.models.first { it.downloadable && it.runtime == LocalModelRuntime.LLAMA_CPP }
        withManager(installed = true, pendingModel = model.id) { context, manager, play, preferences ->
            val started = mutableListOf<String>()
            val result = CompletableDeferred<String?>()
            manager.setModelDownloadStarter {
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                started += it
                result.await()
            }
            shadowOf(Looper.getMainLooper()).idle()
            play.emitInstalled()
            play.emitInstalled()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(model.id), started)

            result.complete(null)
            shadowOf(Looper.getMainLooper()).idle()
            play.emitInstalled()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(model.id), started)
            assertEquals("", preferences.getString("pending_model_LLAMA_CPP", ""))
            assertTrue(manager.isRuntimeInstalled(context, LocalModelRuntime.LITERT_LM))
            assertEquals(LocalRuntimeInstallState.INSTALLED, manager.getStatus(context, model.runtime).state)
            assertEquals(1, play.packageQueries)
        }
    }

    @Test
    fun installedCallbackEnablesBothRuntimesWithoutMorePackageInspection() = withManager { context, manager, play, _ ->
        val model = LocalModelCatalog.models.first { it.downloadable && it.runtime == LocalModelRuntime.LLAMA_CPP }
        val started = mutableListOf<String>()
        manager.setModelDownloadStarter { started += it; null }
        manager.requestRuntimeAndModelDownload(context, model)
        shadowOf(Looper.getMainLooper()).idle()
        play.emitInstalled()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(model.id), started)
        assertTrue(manager.isRuntimeInstalled(context, LocalModelRuntime.LLAMA_CPP))
        assertTrue(manager.isRuntimeInstalled(context, LocalModelRuntime.LITERT_LM))
        assertEquals(1, play.packageQueries)
    }

    private fun withManager(
        installed: Boolean = false,
        pendingModel: String? = null,
        block: (Context, LocalAiRuntimeManager, PlayTransport, AndroidKeyValueStore) -> Unit,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val preferencesName = "runtime_delivery_test_" + UUID.randomUUID()
        val play = PlayTransport(installed)
        val manager = LocalAiRuntimeManager({
            assertFalse("Play initialization must run off Main", Looper.myLooper() == Looper.getMainLooper())
            play.manager
        }, preferencesName)
        val preferences = runBlocking(Dispatchers.IO) {
            if (pendingModel != null) {
                context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                    .edit().putString("pending_model_LLAMA_CPP", pendingModel).commit()
            }
            manager.preload(context)
            AndroidKeyValueStore.named(context, preferencesName)
        }
        try {
            block(context, manager, play, preferences)
        } finally {
            manager.close()
            runBlocking(Dispatchers.IO) {
                preferences.clear()
                preferences.awaitPersisted()
            }
        }
    }

    private class PlayTransport(private val installed: Boolean) {
        val requests = mutableListOf<SplitInstallRequest>()
        var packageQueries = 0
        private val listeners = mutableSetOf<SplitInstallStateUpdatedListener>()
        val manager = Proxy.newProxyInstance(
            SplitInstallManager::class.java.classLoader,
            arrayOf(SplitInstallManager::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getInstalledModules" -> {
                    assertFalse("Package inspection must run off Main", Looper.myLooper() == Looper.getMainLooper())
                    packageQueries++
                    if (installed) setOf("local_ai_runtime") else emptySet<String>()
                }
                "getSessionStates" -> Tasks.forResult(emptyList<SplitInstallSessionState>())
                "registerListener" -> { listeners += arguments!![0] as SplitInstallStateUpdatedListener; null }
                "unregisterListener" -> { listeners -= arguments!![0] as SplitInstallStateUpdatedListener; null }
                "startInstall" -> {
                    requests += arguments!![0] as SplitInstallRequest
                    Tasks.forResult(1)
                }
                else -> error("Unexpected Play call: ${method.name}")
            }
        } as SplitInstallManager

        fun emitInstalled() {
            val state = SplitInstallSessionState.create(
                1, SplitInstallSessionStatus.INSTALLED, 0, 0L, 100L, listOf("local_ai_runtime"), emptyList(),
            )
            listeners.toList().forEach { it.onStateUpdate(state) }
        }
    }
}
