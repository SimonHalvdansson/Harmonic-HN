package com.simon.harmonichackernews.summary.local

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import java.lang.reflect.Proxy
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real delivery owner with a fake Play transport: no feature/model downloads or account changes. */
@RunWith(AndroidJUnit4::class)
class LocalAiRuntimeManagerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.applicationContext
    private val model = LocalModelCatalog.models.first {
        it.downloadable && it.runtime == LocalModelRuntime.LLAMA_CPP
    }

    @Test
    fun successfulNullHandoffKeepsInstalledRuntimeSelectable() = withManager { manager, play ->
        val started = mutableListOf<String>()
        onMain {
            manager.setModelDownloadStarter { started += it; null }
            assertNull(manager.requestRuntimeAndModelDownload(context, model))
        }
        onMain { play.emit(SplitInstallSessionStatus.INSTALLED) }
        onMain {
            assertEquals(listOf(model.id), started)
            assertEquals(LocalRuntimeInstallState.INSTALLED, manager.getStatus(context, model.runtime).state)
            assertEquals("", manager.getStatus(context, model.runtime).error)
        }
    }

    @Test
    fun pendingModelWaitsForStarterRegistration() = withManager { manager, play ->
        val started = mutableListOf<String>()
        onMain { manager.requestRuntimeAndModelDownload(context, model) }
        onMain { play.emit(SplitInstallSessionStatus.INSTALLED) }
        onMain { manager.setModelDownloadStarter { started += it; null } }
        onMain {
            assertEquals(listOf(model.id), started)
            assertEquals(LocalRuntimeInstallState.INSTALLED, manager.getStatus(context, model.runtime).state)
        }
    }

    @Test
    fun realHandoffFailureRemainsActionableUntilSuccessfulRetry() = withManager { manager, play ->
        val started = mutableListOf<String>()
        var hasEnoughStorage = false
        onMain {
            manager.setModelDownloadStarter {
                started += it
                if (hasEnoughStorage) null else "Not enough storage"
            }
            manager.requestRuntimeAndModelDownload(context, model)
        }
        onMain { play.emit(SplitInstallSessionStatus.INSTALLED) }
        onMain {
            val failedModel = manager.getStatus(context, model.runtime)
            assertTrue(manager.isRuntimeInstalled(context, model.runtime))
            assertEquals(LocalRuntimeInstallState.INSTALLED, failedModel.state)
            assertEquals(model.id, failedModel.pendingModelId)
            assertEquals("Not enough storage", failedModel.modelDownloadError)
            assertEquals("", failedModel.error)
            // Re-observing the installed split must not silently hide the real model error.
            play.emit(SplitInstallSessionStatus.INSTALLED)
            assertEquals("Not enough storage", manager.getStatus(context, model.runtime).modelDownloadError)
            hasEnoughStorage = true
            assertNull(manager.requestRuntimeAndModelDownload(context, model))
            val recovered = manager.getStatus(context, model.runtime)
            assertEquals(LocalRuntimeInstallState.INSTALLED, recovered.state)
            assertEquals("", recovered.modelDownloadError)
            assertEquals("", recovered.pendingModelId)
            assertEquals(listOf(model.id, model.id), started)
            assertEquals(1, play.installRequests)
        }
    }

    @Test
    fun genuineRuntimeInstallFailureKeepsItsErrorAndCanRetry() = withManager { manager, play ->
        val started = mutableListOf<String>()
        onMain {
            manager.setModelDownloadStarter { started += it; null }
            manager.requestRuntimeAndModelDownload(context, model)
        }
        onMain {
            play.emit(SplitInstallSessionStatus.FAILED, errorCode = SplitInstallErrorCode.INSUFFICIENT_STORAGE)
            val failed = manager.getStatus(context, model.runtime)
            assertEquals(LocalRuntimeInstallState.FAILED, failed.state)
            assertEquals("Not enough free space to install the local AI runtime.", failed.error)
            assertEquals("", failed.modelDownloadError)
            assertFalse(manager.isRuntimeInstalled(context, model.runtime))
            assertTrue(started.isEmpty())
            play.nextSessionId = 72
            assertNull(manager.requestRuntimeAndModelDownload(context, model))
        }
        onMain { play.emit(SplitInstallSessionStatus.INSTALLED) }
        onMain {
            assertEquals(LocalRuntimeInstallState.INSTALLED, manager.getStatus(context, model.runtime).state)
            assertEquals("", manager.getStatus(context, model.runtime).error)
            assertEquals(listOf(model.id), started)
        }
    }

    @Test
    fun applicationContextRequestUsesResumedActivityForConfirmation() = withManager { manager, play ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onMain { manager.requestRuntimeAndModelDownload(context, model) }
            onMain { play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) }
            scenario.onActivity { assertSame(it, play.confirmationActivities.single()) }
        }
    }

    @Test
    fun confirmationWaitsWhileBackgroundedAndResumesOnlyOnce() = withManager { manager, play ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onMain { manager.requestRuntimeAndModelDownload(context, model) }
            scenario.moveToState(Lifecycle.State.CREATED)
            onMain { play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) }
            onMain {
                assertEquals(0, play.confirmationActivities.size)
                assertEquals(LocalRuntimeInstallState.PENDING, manager.getStatus(context, model.runtime).state)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertSame(it, play.confirmationActivities.single()) }
        }
    }

    @Test
    fun canceledConfirmationCannotReappearOrAttachToTheNextRequest() = withManager { manager, play ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onMain { manager.requestRuntimeAndModelDownload(context, model) }
            scenario.moveToState(Lifecycle.State.CREATED)
            onMain {
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
                manager.cancelRuntimeInstall(context, model.runtime)
                assertEquals(listOf(71), play.cancellationRequests)
                play.nextSessionId = 72
                assertNull(manager.requestRuntimeAndModelDownload(context, model))
            }
            // The session query on resume still contains Play's old confirmation snapshot.
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(play.confirmationActivities.isEmpty())
                assertEquals(72, manager.getStatus(context, model.runtime).sessionId)
                play.emit(SplitInstallSessionStatus.CANCELED, sessionId = 71)
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION, sessionId = 71)
                assertEquals(72, manager.getStatus(context, model.runtime).sessionId)
                assertTrue(play.confirmationActivities.isEmpty())
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
                assertEquals(listOf(72), play.confirmationSessionIds)
            }
            scenario.onActivity { assertSame(it, play.confirmationActivities.single()) }
        }
    }

    @Test
    fun cancellationBeforePlayReturnsTheSessionCancelsTheLateSession() = withManager { manager, play ->
        val start = TaskCompletionSource<Int>()
        onMain {
            play.deferredStart = start
            manager.requestRuntimeAndModelDownload(context, model)
            manager.cancelRuntimeInstall(context, model.runtime)
            start.setResult(71)
        }
        onMain {
            assertEquals(listOf(71), play.cancellationRequests)
            play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            onMain {
                assertEquals(LocalRuntimeInstallState.CANCELED, manager.getStatus(context, model.runtime).state)
                assertTrue(play.confirmationActivities.isEmpty())
            }
        }
    }

    @Test
    fun listenerEventsWaitForTheMatchingSessionAfterCancelAndRetry() = withManager { manager, play ->
        ActivityScenario.launch(MainActivity::class.java).use {
            val canceledStart = TaskCompletionSource<Int>()
            val retryStart = TaskCompletionSource<Int>()
            onMain {
                play.deferredStart = canceledStart
                manager.requestRuntimeAndModelDownload(context, model)
                manager.cancelRuntimeInstall(context, model.runtime)
                play.deferredStart = retryStart
                manager.requestRuntimeAndModelDownload(context, model)
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION, sessionId = 71)
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION, sessionId = 72)
                assertTrue(play.confirmationActivities.isEmpty())
                canceledStart.setResult(71)
                retryStart.setResult(72)
            }
            onMain {
                assertEquals(listOf(71), play.cancellationRequests)
                assertEquals(listOf(72), play.confirmationSessionIds)
                assertEquals(72, manager.getStatus(context, model.runtime).sessionId)
            }
        }
    }

    @Test
    fun recreationReopensPendingConfirmationOnTheNewActivityOnlyOnce() = withManager { manager, play ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onMain { manager.requestRuntimeAndModelDownload(context, model) }
            onMain { play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) }
            val original = play.confirmationActivities.single()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(2, play.confirmationActivities.size)
                assertNotSame(original, it)
                assertSame(it, play.confirmationActivities.last())
            }
            onMain {
                play.emit(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
                assertEquals(listOf(71, 71), play.confirmationSessionIds)
            }
        }
    }

    private fun withManager(block: (LocalAiRuntimeManager, PlayTransport) -> Unit) {
        val play = PlayTransport()
        val preferencesName = "runtime_delivery_test_" + UUID.randomUUID()
        val manager = LocalAiRuntimeManager({ play.manager }, {}, preferencesName)
        onMain { manager.bindActivityLifecycle(context) }
        try {
            block(manager, play)
        } finally {
            onMain {
                manager.close()
                context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        instrumentation.waitForIdleSync()
    }

    private class PlayTransport {
        private val listeners = mutableSetOf<SplitInstallStateUpdatedListener>()
        private val installed = mutableSetOf<String>()
        private val sessions = mutableMapOf<Int, SplitInstallSessionState>()
        var nextSessionId = 71
        var installRequests = 0
        var deferredStart: TaskCompletionSource<Int>? = null
        val confirmationActivities = mutableListOf<Activity>()
        val confirmationSessionIds = mutableListOf<Int>()
        val cancellationRequests = mutableListOf<Int>()
        val manager = Proxy.newProxyInstance(
            SplitInstallManager::class.java.classLoader,
            arrayOf(SplitInstallManager::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getInstalledModules" -> installed
                "getSessionStates" -> Tasks.forResult(sessions.values.toList())
                "startInstall" -> {
                    installRequests++
                    deferredStart?.task ?: Tasks.forResult(nextSessionId)
                }
                "cancelInstall" -> {
                    cancellationRequests += arguments!![0] as Int
                    Tasks.forResult<Void>(null)
                }
                "registerListener" -> { listeners += arguments!![0] as SplitInstallStateUpdatedListener; null }
                "unregisterListener" -> { listeners -= arguments!![0] as SplitInstallStateUpdatedListener; null }
                "startConfirmationDialogForResult" -> {
                    confirmationActivities += arguments!![1] as Activity
                    confirmationSessionIds += (arguments[0] as SplitInstallSessionState).sessionId()
                    true
                }
                else -> error("Unexpected Play call: ${method.name}")
            }
        } as SplitInstallManager

        fun emit(status: Int, sessionId: Int = nextSessionId, errorCode: Int = 0) {
            if (status == SplitInstallSessionStatus.INSTALLED) installed += "local_ai_runtime"
            val state = if (status == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
                // Play's public create() explicitly excludes confirmation states. Use its
                // Bundle decoder to model the same state delivered by the install listener.
                val decoder = SplitInstallSessionState::class.java.declaredMethods.single {
                    it.returnType == SplitInstallSessionState::class.java &&
                        it.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                }
                decoder.invoke(null, Bundle().apply {
                    putInt("session_id", sessionId)
                    putInt("status", status)
                    putStringArrayList("module_names", arrayListOf("local_ai_runtime"))
                }) as SplitInstallSessionState
            } else {
                SplitInstallSessionState.create(
                    sessionId, status, errorCode, 0L, 100L, listOf("local_ai_runtime"), emptyList(),
                )
            }
            sessions[sessionId] = state
            listeners.toList().forEach { it.onStateUpdate(state) }
        }
    }
}
