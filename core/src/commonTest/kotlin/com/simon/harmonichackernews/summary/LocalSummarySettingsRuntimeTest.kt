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

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSummarySettingsRuntimeTest {
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
