package com.simon.harmonichackernews

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.summary.StorySummaryEvent
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in native coverage: pass localModelId for a model already installed on the test device. */
@RunWith(AndroidJUnit4::class)
class LocalAiCancellationTest {
    @Test fun cancellationReleasesNativeInferenceAndAllowsAnotherRequest() = runBlocking {
        val modelId = InstrumentationRegistry.getArguments().getString("localModelId")
        assumeNotNull(modelId)
        val app = ApplicationProvider.getApplicationContext<Context>().awaitHarmonicAppComposition()
        val models = checkNotNull(app.localModels)
        val previousModel = models.selectedModel.id
        val engine = checkNotNull(app.localSummaryEngine)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            withContext(Dispatchers.Main) {
                models.preload()
                assertTrue(models.select(checkNotNull(modelId)))
                models.preload()
            }
            assertTrue(engine.isReady())
            // Repeat streamed generation, then stop a non-streaming request during prompt work.
            for (stream in listOf(true, true, false)) {
                val started = CompletableDeferred<Unit>()
                val generation = scope.launch {
                    engine.summarizeEvents(SummaryRequest(
                        text = "Describe the history of libraries, how they serve communities, and their future. "
                            .repeat(if (stream) 1 else 40),
                        prompt = "Write a long, detailed essay of at least 500 words.",
                        preserveInput = true,
                        streamResponses = stream,
                    )).collect { event ->
                        when (event) {
                            is StorySummaryEvent.Progress -> if (stream) started.complete(Unit)
                            is StorySummaryEvent.DebugInfo -> if (!stream) started.complete(Unit)
                            is StorySummaryEvent.Failure -> started.completeExceptionally(AssertionError(event.message))
                            else -> Unit
                        }
                    }
                }
                withTimeout(180_000) { started.await() }
                if (!stream) delay(50)
                val before = System.nanoTime()
                generation.cancel()
                val stopped = withTimeoutOrNull(2_000) { generation.join(); true } == true
                println("Native cancellation: stream=$stream elapsedMs=${(System.nanoTime() - before) / 1_000_000}")
                assertTrue("Cancelled native generation must release the engine within two seconds", stopped)
            }
        } finally {
            scope.cancel()
            withContext(Dispatchers.Main) { models.select(previousModel) }
        }
    }
}
