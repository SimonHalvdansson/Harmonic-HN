package com.simon.harmonichackernews.summary.local

import android.app.Application
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalAiRuntimeStatusTest {
    @Test
    fun everyRuntimeCanBeQueriedBeforeStartingAnAndroidModelDownload() {
        val context = RuntimeEnvironment.getApplication()
        val requests = mutableListOf<SplitInstallRequest>()
        val play = Proxy.newProxyInstance(
            SplitInstallManager::class.java.classLoader,
            arrayOf(SplitInstallManager::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getInstalledModules" -> emptySet<String>()
                "getSessionStates" -> Tasks.forResult(emptyList<SplitInstallSessionState>())
                "registerListener", "unregisterListener" -> null
                "startInstall" -> {
                    requests += arguments!![0] as SplitInstallRequest
                    Tasks.forResult(1)
                }
                else -> error("Unexpected Play call: ${method.name}")
            }
        } as SplitInstallManager
        val manager = LocalAiRuntimeManager({ play }, {})
        try {
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
            assertEquals(listOf("local_ai_runtime"), requests.single().moduleNames)
            assertEquals(LocalRuntimeInstallState.PENDING, manager.getStatus(context, model.runtime).state)
        } finally {
            manager.close()
        }
    }
}
