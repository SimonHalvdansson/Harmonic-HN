package com.simon.harmonichackernews.network

import android.app.Application
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.schema.guided.GenerableProvider
import java.util.ServiceLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NanoPromptIntegrationTest {
    @Test
    fun readinessTracksPromptAndLoraSeparatelyWithoutHidingTheOtherMode() = runBlocking {
        val requested = mutableListOf<Boolean>()
        val status = PlayLocalSummaryStatus { lora ->
            requested += lora
            NanoFeatureSnapshot(if (lora) FeatureStatus.AVAILABLE else FeatureStatus.UNAVAILABLE, "nano-test")
        }
        val availability = status.checkLocalSummaryAvailability(false)
        assertEquals(listOf(false, true), requested)
        assertTrue(availability.available)
        assertFalse(availability.downloadableFallbackRequired)
        assertFalse(status.isNanoFeatureUsable(false))
        assertTrue(status.isNanoFeatureUsable(true))
        assertTrue(availability.statusMessage.orEmpty().contains("3-bullet"))
        assertEquals("nano-test", availability.baseModelName)
    }

    @Test
    fun unavailableFeaturesAndTransientFailuresAreCheckedAgain() = runBlocking {
        var attempt = 0
        val status = PlayLocalSummaryStatus {
            when (attempt) {
                0 -> throw IllegalStateException("AICore is initializing")
                1 -> NanoFeatureSnapshot(FeatureStatus.UNAVAILABLE)
                else -> NanoFeatureSnapshot(FeatureStatus.AVAILABLE, "nano-ready")
            }
        }
        for (next in 0..2) {
            attempt = next
            val availability = status.checkLocalSummaryAvailability(false)
            assertEquals(next < 2, availability.downloadableFallbackRequired)
            assertEquals(next == 2, status.isNanoFeatureUsable(false))
        }
    }

    @Test
    fun availabilityCancellationIsNotConvertedIntoAnUnavailableFeature() = runBlocking {
        val status = PlayLocalSummaryStatus { throw CancellationException("Screen closed") }
        try {
            status.checkLocalSummaryAvailability(false)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(status.isNanoFeatureUsable(false))
        }
    }

    @Test
    fun terminalReasonsPreserveTruncatedTextAndRejectOtherFailures() {
        val truncated = nanoSummaryResult("An unfinished sentence", Candidate.FinishReason.MAX_TOKENS)
        assertEquals("An unfinished sentence", truncated.text)
        assertTrue(truncated.truncated)
        assertFalse(nanoSummaryResult("Complete answer", Candidate.FinishReason.STOP).truncated)
        // Older streaming implementations may omit a terminal reason.
        assertFalse(nanoSummaryResult("Complete answer", null).truncated)
        assertThrows(IllegalStateException::class.java) {
            nanoSummaryResult("Partial answer", Candidate.FinishReason.OTHER)
        }
    }

    @Test
    fun generatedSuggestionSchemaIsDiscoverableAndConstrainsTheListToTwoStrings() {
        val provider = ServiceLoader.load(GenerableProvider::class.java).single {
            it.targetClass == NanoQuestionSuggestions::class
        }
        val field = provider.getGenerableDetail().guideDetails.single()
        assertEquals("questions", field.name)
        assertEquals(2, field.minItems)
        assertEquals(2, field.maxItems)
        assertEquals(String::class, field.listItemType)
    }
}
