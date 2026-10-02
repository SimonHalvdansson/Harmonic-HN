package com.simon.harmonichackernews.summary

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class DesktopLiteRtInferenceTest {
    /** Opt-in real-model smoke test: avoids downloading gigabytes in routine unit-test runs. */
    @Test
    fun installedJvmRuntimeStreamsANonEmptySummary() = runBlocking {
        val modelPath = System.getenv("HARMONIC_TEST_LITERT_MODEL")
        assumeTrue("Set HARMONIC_TEST_LITERT_MODEL to a Gemma E2B .litertlm file", modelPath != null)
        requireNotNull(modelPath)
        val cache = Files.createTempDirectory("harmonic-litert-test")
        try {
            val progress = mutableListOf<String>()
            var loaded = false
            val result = DesktopLiteRtInference(cache.toString()).summarize(
                model = LocalModelCatalog.models.first { it.id == LocalModelCatalog.MODEL_E2B },
                modelPath = modelPath,
                systemInstruction = "Summarize the following article in one short sentence.",
                text = "A public library has extended its opening hours after a six-month trial. " +
                    "The library will now open on Sundays, with volunteers helping visitors find books. " +
                    "The council approved funding following a survey in which most residents requested " +
                    "weekend access. The building also provides study rooms, free internet access, " +
                    "and a weekly reading group. Staff said the new hours would help people who work " +
                    "during the week and students who need a quiet place to study.",
                onProgress = { progress += it },
                onLoaded = { loaded = true },
            )
            assertTrue(loaded)
            assertTrue(progress.isNotEmpty())
            assertTrue(result.isNotBlank())
            println("LiteRT-LM desktop summary: $result")
        } finally {
            Files.walk(cache).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
