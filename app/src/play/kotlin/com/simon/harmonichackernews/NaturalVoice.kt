package com.simon.harmonichackernews

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.texttospeech.SpeechSynthesis
import com.google.mlkit.genai.texttospeech.SpeechSynthesizer
import com.google.mlkit.genai.texttospeech.SpeechSynthesizerRequest
import com.google.mlkit.genai.texttospeech.SpeechSynthesizerResponse
import com.google.mlkit.genai.texttospeech.VoiceInfo
import com.google.mlkit.genai.texttospeech.speechSynthesizerOptions
import com.simon.harmonichackernews.platform.ReadAloudEngine
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PROBE_TIMEOUT_MS = 5_000L

private fun newSynthesizer() = SpeechSynthesis.getClient(speechSynthesizerOptions(Locale.getDefault()) {
    setVoiceFallbackEnabled(false)
})

private data class VoiceCandidate(val voice: VoiceInfo, val status: Int)

private suspend fun SpeechSynthesizer.findVoice(): VoiceCandidate? = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
    val locale = Locale.getDefault()
    val voices = getSupportedVoices(locale).ifEmpty { getSupportedVoices(Locale.forLanguageTag(locale.language)) }
    var downloadable: VoiceCandidate? = null
    for (voice in voices) {
        val status = checkStatus(voice)
        when (status) {
            FeatureStatus.AVAILABLE -> return@withTimeoutOrNull VoiceCandidate(voice, status)
            FeatureStatus.DOWNLOADING, FeatureStatus.DOWNLOADABLE ->
                if (downloadable == null) downloadable = VoiceCandidate(voice, status)
        }
    }
    downloadable
}

private suspend fun <T> withSpeechFallback(action: suspend () -> T): T? = try {
    action()
} catch (_: CancellationException) {
    // Playback cancellation belongs to the controller. A cancelled SDK operation can fall back.
    currentCoroutineContext().ensureActive()
    null
} catch (failure: Exception) {
    Log.w("NaturalVoice", "Google speech service unavailable", failure)
    null
}

internal suspend fun naturalVoiceAvailability(): Boolean = withContext(Dispatchers.IO) {
    withSpeechFallback { newSynthesizer().use { it.findVoice() != null } } == true
}

internal fun createReadAloudEngine(system: ReadAloudEngine, preferNaturalVoice: () -> Boolean): ReadAloudEngine =
    NaturalVoiceEngine(system, preferNaturalVoice, MlKitVoice(), CoroutineScope(SupervisorJob() + Dispatchers.Main))

internal interface NaturalSpeech {
    suspend fun prepare(): Boolean
    suspend fun speak(text: String): Boolean
    fun stop()
    fun close()
}

private class MlKitVoice : NaturalSpeech {
    private var client: SpeechSynthesizer? = null
    private var voice: VoiceInfo? = null

    override suspend fun prepare(): Boolean {
        val synthesizer = newSynthesizer()
        client = synthesizer
        val candidate = synthesizer.findVoice() ?: return false
        if (candidate.status != FeatureStatus.AVAILABLE) {
            val result = synthesizer.download(candidate.voice).firstOrNull {
                it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
            }
            if (result !is DownloadStatus.DownloadCompleted) return false
        }
        voice = candidate.voice
        return true
    }

    override suspend fun speak(text: String): Boolean {
        val request = SpeechSynthesizerRequest(
            input = SpeechSynthesizerRequest.Input.Text(text, checkNotNull(voice)),
            audioAttributes = ReadAloudService.attributes(),
        )
        val terminal = checkNotNull(client).synthesize(request).firstOrNull {
            it is SpeechSynthesizerResponse.Error ||
                (it is SpeechSynthesizerResponse.StatusUpdate && it.status != SpeechSynthesizerResponse.Status.START)
        }
        return terminal is SpeechSynthesizerResponse.StatusUpdate && terminal.status == SpeechSynthesizerResponse.Status.DONE
    }

    override fun stop() { client?.stop() }
    override fun close() { client?.close() }
}

internal class NaturalVoiceEngine(
    private val system: ReadAloudEngine,
    private val preferNaturalVoice: () -> Boolean,
    private val natural: NaturalSpeech,
    private val scope: CoroutineScope,
) : ReadAloudEngine {
    // A slow first-use download can finish while the system voice reads the first sentences.
    private val ready = scope.async(start = CoroutineStart.LAZY) { withSpeechFallback { natural.prepare() } == true }
    private var playback: Job? = null
    private var failed = false

    override fun speak(text: String, rate: Float, completed: (String?) -> Unit) {
        if (failed || rate != 1f || !preferNaturalVoice()) {
            system.speak(text, rate, completed)
            return
        }
        playback = scope.launch {
            val firstAttempt = ready.start()
            val available = if (firstAttempt) {
                withTimeoutOrNull(1_500) { ready.await() } == true
            } else {
                ready.isCompleted && ready.await()
            }
            if (available) {
                // Bound a missing DONE callback, including the longest reader sentence (1,800 chars).
                val spoken = withTimeoutOrNull(180_000) { withSpeechFallback { natural.speak(text) } }
                if (spoken == true) {
                    completed(null)
                    return@launch
                }
                failed = true
                natural.stop()
            }
            system.speak(text, rate, completed)
        }
    }

    override fun stop() {
        playback?.cancel()
        playback = null
        natural.stop()
        system.stop()
    }

    override fun close() {
        scope.cancel()
        natural.close()
        system.close()
    }
}
