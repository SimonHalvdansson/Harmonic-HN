package com.simon.harmonichackernews.summary

import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLlamaCancellationTest {
    @Test fun cancelsModelLoadingAndCanRunAgain() = verifyCancellation("load")
    @Test fun cancelsPromptProcessingAndCanRunAgain() = verifyCancellation("start")
    @Test fun cancelsTokenDecodingAndCanRunAgain() = verifyCancellation("next")

    private fun verifyCancellation(stage: String) = runBlocking {
        val api = BlockingApi(stage)
        val inference = DesktopLlamaInference({ api }) { 8L * 1024 * 1024 * 1024 }
        val model = LocalModelCatalog.models.first { it.id == LocalModelCatalog.MODEL_BONSAI_17B }
        suspend fun summarize() = inference.summarize(model, "unused.gguf", "Summarize", "Test input", {}, {}, true)
        val job = launch { summarize() }
        assertTrue(withContext(Dispatchers.IO) { api.entered.await(5, TimeUnit.SECONDS) }, "Native stage was not reached")
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertEquals(1, api.destroyed.get())
        assertEquals(0, api.cancelledAfterDestroy.get(), "Cancellation must finish before native destruction")
        assertEquals("- Completed", withTimeout(2_000) { summarize() })
        assertEquals(2, api.destroyed.get())
        assertEquals(0, api.cancelledAfterDestroy.get())
    }

    private class BlockingApi(private val stage: String) : HarmonicLlamaApi {
        val entered = CountDownLatch(1)
        private val cancelled = CountDownLatch(1)
        val destroyed = AtomicInteger()
        val cancelledAfterDestroy = AtomicInteger()
        @Volatile private var alive = false
        private var request = 0
        private var token = 0

        private fun block(at: String) {
            if (request == 1 && stage == at) {
                entered.countDown()
                check(cancelled.await(5, TimeUnit.SECONDS)) { "Native cancellation was never signalled" }
            }
        }
        override fun harmonic_llama_backend_initialize(callback: Pointer?, userData: Pointer?) = Unit
        override fun harmonic_llama_create(): Pointer {
            request++
            token = 0
            alive = true
            return Pointer(request.toLong())
        }
        override fun harmonic_llama_load(engine: Pointer, modelPath: String, contextTokens: Int): Int { block("load"); return 1 }
        override fun harmonic_llama_start(engine: Pointer, systemPrompt: String, userPrompt: String, responsePrefix: String, outputTokens: Int): Int { block("start"); return 1 }
        override fun harmonic_llama_next_utf8(engine: Pointer, buffer: ByteArray, capacity: Int): Int {
            block("next")
            if (token++ > 0) return 0
            val bytes = "Completed".encodeToByteArray()
            bytes.copyInto(buffer)
            return bytes.size
        }
        override fun harmonic_llama_cancel(engine: Pointer) {
            if (!alive) cancelledAfterDestroy.incrementAndGet()
            cancelled.countDown()
        }
        override fun harmonic_llama_last_error(engine: Pointer): Pointer? = null
        override fun harmonic_llama_close(engine: Pointer) = Unit
        override fun harmonic_llama_destroy(engine: Pointer) { alive = false; destroyed.incrementAndGet() }
    }
}
