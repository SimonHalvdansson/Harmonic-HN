package com.simon.harmonichackernews

import android.app.*
import android.content.*
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.platform.ReadAloudEngine
import com.simon.harmonichackernews.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

/** Process-wide handoff avoids sending article bodies through Binder or retaining a WebView. */
internal object AndroidReadAloud {
    val state = MutableStateFlow(ReadAloudState())
    var pending: JsonObject? = null
    var service: ReadAloudService? = null
    fun command(context: Context, command: JsonObject) {
        if (service != null) { service?.command(command); return }
        if (command["action"]?.jsonPrimitive?.content != "start") return
        pending = command
        try {
            ContextCompat.startForegroundService(context, Intent(context, ReadAloudService::class.java))
        } catch (_: RuntimeException) {
            pending = null
            state.value = ReadAloudState(error = "Could not start audio playback. Try again with Harmonic open.")
        }
    }
}

class ReadAloudService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: ReadAloudController
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var pauseTimeout: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) { hasFocus = false; controller.pause() }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { controller.pause() }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        controller = ReadAloudController(createReadAloudEngine(AndroidSpeechEngine(this)) {
            (application as HarmonicApplication).composition.userSettings.reading.preferNaturalVoice
        })
        scope.launch {
            (application as HarmonicApplication).composition.settings.updates.collect { settings ->
                if (!settings.reading.readAloudEnabled && controller.state.value.count > 0) controller.stop()
            }
        }
        session = MediaSession(this, "Read aloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { command(action("play")) }
                override fun onPause() { this@ReadAloudService.controller.pause() }
                override fun onStop() { this@ReadAloudService.controller.stop() }
                override fun onSkipToNext() { command(action("next")) }
                override fun onSkipToPrevious() { command(action("previous")) }
                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == "stop") this@ReadAloudService.controller.stop()
                }
            })
            setPlaybackToLocal(attributes())
            isActive = true
        }
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Read aloud", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification(ReadAloudState(title = "Preparing audio…")))
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:readAloud")
        AndroidReadAloud.service = this
        scope.launch {
            controller.state.collect { state ->
                AndroidReadAloud.state.value = state
                session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, state.title).build())
                session.setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .addCustomAction("stop", "Stop", R.drawable.ic_stop)
                    .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state.rate).build())
                getSystemService(NotificationManager::class.java).notify(ID, notification(state))
                if (state.playing) {
                    pauseTimeout?.cancel()
                    if (wakeLock?.isHeld != true) wakeLock?.acquire(2 * 60 * 60 * 1000L)
                } else {
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                    abandonFocus()
                    if (state.count > 0) {
                        pauseTimeout?.cancel()
                        pauseTimeout = scope.launch { delay(5 * 60 * 1000L); controller.stop() }
                    }
                }
                if (state.count == 0 && AndroidReadAloud.pending == null) stopSelf()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pending = AndroidReadAloud.pending
        AndroidReadAloud.pending = null
        if (pending != null) command(pending)
        intent?.action?.let { command(action(it)) }
        return START_NOT_STICKY
    }
    internal fun command(command: JsonObject) {
        if (!(application as HarmonicApplication).composition.userSettings.reading.readAloudEnabled) {
            controller.stop()
            stopSelf()
            return
        }
        val name = command["action"]?.jsonPrimitive?.content
        if (name == "start" || name == "play") {
            if (!requestFocus()) {
                AndroidReadAloud.state.value = controller.state.value.copy(error = "Audio is in use by another app. Try again shortly.")
                if (controller.state.value.count == 0) stopSelf()
                return
            }
        }
        controller.command(command)
    }
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val granted = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(focusListener).build()
            focusRequest = request
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audio::abandonAudioFocusRequest)
        else { @Suppress("DEPRECATION") audio.abandonAudioFocus(focusListener) }
        focusRequest = null; hasFocus = false
    }
    private fun notification(state: ReadAloudState): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        fun pending(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, ReadAloudService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return builder.setSmallIcon(R.drawable.ic_volume_up)
            .setContentTitle(state.title.ifBlank { "Read aloud" })
            .setContentText(state.error ?: if (state.count > 0) "${state.index + 1} / ${state.count}" else "Preparing audio…")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setOngoing(state.playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_skip_previous), "Previous", pending("previous")).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow), if (state.playing) "Pause" else "Resume", pending(if (state.playing) "pause" else "play")).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stop), "Stop", pending("stop")).build())
            .setDeleteIntent(pending("stop"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        AndroidReadAloud.service = null
        scope.cancel(); controller.close(); session.release(); abandonFocus()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        unregisterReceiver(noisy)
        AndroidReadAloud.state.value = ReadAloudState(error = AndroidReadAloud.state.value.error)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "read_aloud"
        private const val ID = 733
        private fun action(name: String) = buildJsonObject { put("action", name) }
        internal fun attributes(): AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
}

private class AndroidSpeechEngine(context: Context) : ReadAloudEngine {
    private val handler = Handler(Looper.getMainLooper())
    private var initialized: Boolean? = null
    private var closed = false
    private var callback: ((String?) -> Unit)? = null
    private var pending: (() -> Unit)? = null
    private var sequence = 0
    private var utterance = ""
    private val tts = TextToSpeech(context.applicationContext) { status ->
        handler.post { if (!closed) { initialized = status == TextToSpeech.SUCCESS; pending?.also { pending = null; it() } } }
    }
    init {
        tts.setAudioAttributes(ReadAloudService.attributes())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { finish(id, null) }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { finish(id, "Speech unavailable. Check your system speech engine and downloaded voices.") }
            override fun onError(id: String?, errorCode: Int) { onError(id) }
        })
    }
    private fun finish(id: String?, error: String?) { handler.post {
        if (!closed && id == utterance) callback?.also { callback = null; it(error) }
    } }
    override fun speak(text: String, rate: Float, completed: (String?) -> Unit) {
        callback = completed
        utterance = (++sequence).toString()
        val run = {
            if (initialized != true) finish(utterance, "No speech engine is available. Install or enable a system text-to-speech engine.")
            else {
                tts.setSpeechRate(rate)
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR)
                    finish(utterance, "Could not speak this article. Check your system voice settings.")
            }
        }
        if (initialized == null) pending = run else run()
    }
    override fun stop() { utterance = (++sequence).toString(); pending = null; callback = null; tts.stop() }
    override fun close() { closed = true; stop(); tts.shutdown() }
}
