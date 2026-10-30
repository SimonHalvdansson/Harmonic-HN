package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSummarySettingsRuntimeTest {
    @Test
    fun resolvingAfterModeChangesChecksTheSelectedCapability() = runTest {
        var lora = false
        val requests = mutableListOf<Boolean>()
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest) = SummaryResult("")
            override suspend fun availability(useGeminiNanoSummarizationLora: Boolean): LocalSummaryAvailability {
                requests += useGeminiNanoSummarizationLora
                return LocalSummaryAvailability(true, false)
            }
            override fun isReady(useGeminiNanoSummarizationLora: Boolean) = useGeminiNanoSummarizationLora
        }
        val runtime = LocalSummarySettingsRuntime(this, engine, null) { lora }
        runtime.resolve()
        advanceUntilIdle()
        assertTrue(runtime.state.value.nanoAvailable)
        assertFalse(runtime.state.value.configurationReady)
        lora = true
        runtime.resolve()
        advanceUntilIdle()
        assertTrue(runtime.state.value.nanoAvailable)
        assertTrue(runtime.state.value.configurationReady)
        assertEquals(listOf(false, true), requests)
    }

    @Test
    fun exposesTheSystemManagedBaseModelName() = runTest {
        val runtime = LocalSummarySettingsRuntime(
            scope = this,
            summary = object : LocalSummaryEngine {
                override suspend fun availability() = LocalSummaryAvailability(
                    available = true,
                    downloadableFallbackRequired = false,
                    baseModelName = "nano-v3",
                )

                override suspend fun isAvailable(): Boolean = true

                override fun isReady(): Boolean = true

                override suspend fun summarize(request: SummaryRequest) = SummaryResult("")
            },
            models = null,
        )

        runtime.resolve()
        advanceUntilIdle()

        assertTrue(runtime.state.value.nanoAvailable)
        assertEquals("nano-v3", runtime.state.value.nanoBaseModelName)
    }
}
