package com.simon.harmonichackernews.summary.local

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import android.content.SharedPreferences
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.HashSet
import java.util.concurrent.CopyOnWriteArraySet
import com.simon.harmonichackernews.summary.local.LocalModelManager.ModelInfo

/** Installs Play-delivered local-AI runtimes before their model download starts.  */
object LocalAiRuntimeManager {
    private val MODULE_RUNTIME = "local_ai_runtime"
    private val ENGINE_LLAMA = "com.simon.harmonichackernews.localai.llama.LlamaInferenceEngine"
    private val ENGINE_LITERT = "com.simon.harmonichackernews.localai.litert.LiteRtInferenceEngine"
    private val DELIVERY_PREFS = "local_ai_runtime_delivery"
    private val KEY_PENDING_MODEL_PREFIX = "pending_model_"
    private const val CONFIRMATION_REQUEST_CODE = 0x4c41

    private val LOCK: Any = Any()
    private val LISTENERS: MutableSet<StatusListener> = CopyOnWriteArraySet()
    private val STATUSES: MutableMap<LocalModelManager.Runtime, Status> =
        EnumMap(LocalModelManager.Runtime::class.java)
    private val CONFIRMATION_REQUESTED: MutableSet<Int> = HashSet()

    private var appContext: Context? = null
    private var installManager: SplitInstallManager? = null
    private var confirmationActivity: WeakReference<Activity> = WeakReference(null)
    private var initialized = false

    private val INSTALL_LISTENER: SplitInstallStateUpdatedListener =
        SplitInstallStateUpdatedListener { installState: SplitInstallSessionState ->
            handleInstallState(installState)
        }

    fun isLocalAiIncluded(): Boolean = true

    fun addStatusListener(context: Context, listener: StatusListener) {
        initialize(context)
        LISTENERS.add(listener)
        listener.onRuntimeStatusChanged()
    }

    fun removeStatusListener(listener: StatusListener) {
        LISTENERS.remove(listener)
    }

    fun getStatus(context: Context, runtime: LocalModelManager.Runtime): Status {
        initialize(context)
        if (runtime === LocalModelManager.Runtime.GEMINI_NANO) {
            return status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        }
        kotlin.synchronized(LOCK) {
            val tracked = STATUSES.get(runtime)
            if (tracked != null && tracked.state != com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLED) {
                return tracked
            }
        }
        return if (isRuntimeInstalled(context, runtime))
            status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        else
            status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.NOT_INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
    }

    fun isRuntimeInstalled(
        context: Context,
        runtime: LocalModelManager.Runtime
    ): Boolean {
        if (runtime === LocalModelManager.Runtime.GEMINI_NANO) {
            return true
        }
        initialize(context)
        return requireNotNull(installManager).installedModules.contains(getModuleName(runtime))
    }

    @Nullable
    fun requestRuntimeAndModelDownload(context: Context, modelId: String?): String? {
        initialize(context)
        val model: ModelInfo = LocalModelManager.getModel(modelId)
        if (!model.downloadable) {
            return model.displayName + " is built into supported devices."
        }
        if (!LocalModelManager.isModelSupported(model)) {
            return LocalModelManager.getModelUnsupportedReason(model) + "."
        }

        val activity: Activity? = findActivity(context)
        if (activity != null) {
            confirmationActivity = WeakReference(activity)
        }

        val current = getStatus(context, model.runtime)
        if (current.isActive) {
            if (model.id.equals(current.pendingModelId)) {
                return null
            }
            return ("Wait for the current " + getRuntimeLabel(model.runtime)
                    + " installation to finish.")
        }
        val otherRuntime: LocalModelManager.Runtime = if (model.runtime
            === LocalModelManager.Runtime.LLAMA_CPP
        )
            LocalModelManager.Runtime.LITERT_LM
        else
            LocalModelManager.Runtime.LLAMA_CPP
        if (getStatus(context, otherRuntime).isActive) {
            return "Wait for the current local AI runtime installation to finish."
        }

        setPendingModel(model.runtime, model.id)
        if (isRuntimeInstalled(context, model.runtime)) {
            startPendingModelDownload(model.runtime)
            return null
        }

        setStatus(
            status(
                model.runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING,
                0L,
                0L,
                "",
                model.id,
                0
            )
        )
        val request: SplitInstallRequest = SplitInstallRequest.newBuilder()
            .addModule(getModuleName(model.runtime))
            .build()
        requireNotNull(installManager).startInstall(request)
            .addOnSuccessListener({ sessionId ->
                if (sessionId == 0 || isRuntimeInstalled(requireNotNull(appContext), model.runtime)) {
                    onRuntimeInstalled(model.runtime)
                    return@addOnSuccessListener
                }
                val latest = getTrackedStatus(model.runtime)
                setStatus(
                    status(
                        model.runtime,
                        com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING,
                        latest.bytesDownloaded,
                        latest.totalBytes,
                        "",
                        model.id,
                        sessionId
                    )
                )
            })
            .addOnFailureListener({ failure ->
                failInstall(
                    model.runtime, getInstallFailureMessage(failure), 0
                )
            })
        return null
    }

    fun cancelRuntimeInstall(
        context: Context,
        runtime: LocalModelManager.Runtime
    ) {
        initialize(context)
        val current = getStatus(context, runtime)
        clearPendingModel(runtime)
        if (current.sessionId > 0) {
            requireNotNull(installManager).cancelInstall(current.sessionId)
        }
        setStatus(
            status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.CANCELED,
                current.bytesDownloaded,
                current.totalBytes,
                "",
                current.pendingModelId,
                current.sessionId
            )
        )
    }

    fun getRuntimeLabel(runtime: LocalModelManager.Runtime): String {
        if (runtime === LocalModelManager.Runtime.LLAMA_CPP
            || runtime === LocalModelManager.Runtime.LITERT_LM
        ) {
            return "local AI runtime"
        }
        return "Gemini Nano"
    }

    fun getEngineClassName(runtime: LocalModelManager.Runtime): String {
        if (runtime === LocalModelManager.Runtime.LLAMA_CPP) {
            return ENGINE_LLAMA
        }
        if (runtime === LocalModelManager.Runtime.LITERT_LM) {
            return ENGINE_LITERT
        }
        throw IllegalArgumentException("Gemini Nano does not use a feature runtime")
    }

    private fun initialize(context: Context) {
        kotlin.synchronized(LOCK) {
            if (initialized) {
                return
            }
            val applicationContext = context.applicationContext
            appContext = applicationContext
            installManager = SplitInstallManagerFactory.create(applicationContext).also {
                it.registerListener(INSTALL_LISTENER)
            }
            initialized = true
        }

        requireNotNull(installManager).sessionStates.addOnSuccessListener({ states ->
            for (state in states) {
                handleInstallState(state)
            }
            resumeInstalledPendingDownloads()
        })
        resumeInstalledPendingDownloads()
    }

    private fun resumeInstalledPendingDownloads() {
        for (runtime in arrayOf<LocalModelManager.Runtime>(
            LocalModelManager.Runtime.LLAMA_CPP,
            LocalModelManager.Runtime.LITERT_LM
        )) {
            if (isRuntimeInstalledWithoutInitialization(runtime)
                && !getPendingModel(runtime).isEmpty()
            ) {
                ContextCompat.getMainExecutor(requireNotNull(appContext)).execute(
                    { onRuntimeInstalled(runtime) })
            }
        }
    }

    private fun handleInstallState(installState: SplitInstallSessionState) {
        val runtime: LocalModelManager.Runtime? = getRuntimeForModules(installState.moduleNames())
        if (runtime == null) {
            return
        }
        val pendingModel = getPendingModel(runtime)
        when (installState.status()) {
            SplitInstallSessionStatus.PENDING -> setStatus(
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING,
                    installState.bytesDownloaded(),
                    installState.totalBytesToDownload(),
                    "",
                    pendingModel,
                    installState.sessionId()
                )
            )

            SplitInstallSessionStatus.DOWNLOADING -> setStatus(
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.DOWNLOADING,
                    installState.bytesDownloaded(),
                    installState.totalBytesToDownload(),
                    "",
                    pendingModel,
                    installState.sessionId()
                )
            )

            SplitInstallSessionStatus.DOWNLOADED, SplitInstallSessionStatus.INSTALLING -> setStatus(
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLING,
                    installState.bytesDownloaded(),
                    installState.totalBytesToDownload(),
                    "",
                    pendingModel,
                    installState.sessionId()
                )
            )

            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                setStatus(
                    status(
                        runtime,
                        com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING,
                        installState.bytesDownloaded(),
                        installState.totalBytesToDownload(),
                        "",
                        pendingModel,
                        installState.sessionId()
                    )
                )
                requestConfirmation(runtime, installState)
            }

            SplitInstallSessionStatus.INSTALLED -> {
                kotlin.synchronized(LOCK) {
                    CONFIRMATION_REQUESTED.remove(installState.sessionId())
                }
                onRuntimeInstalled(runtime)
            }

            SplitInstallSessionStatus.FAILED -> failInstall(
                runtime, getInstallErrorMessage(installState.errorCode()),
                installState.sessionId()
            )

            SplitInstallSessionStatus.CANCELING -> setStatus(
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING,
                    installState.bytesDownloaded(),
                    installState.totalBytesToDownload(),
                    "",
                    pendingModel,
                    installState.sessionId()
                )
            )

            SplitInstallSessionStatus.CANCELED -> {
                clearPendingModel(runtime)
                setStatus(
                    status(
                        runtime,
                        com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.CANCELED,
                        installState.bytesDownloaded(),
                        installState.totalBytesToDownload(),
                        "",
                        pendingModel,
                        installState.sessionId()
                    )
                )
            }

            else -> {}
        }
    }

    private fun requestConfirmation(
        runtime: LocalModelManager.Runtime,
        state: SplitInstallSessionState
    ) {
        kotlin.synchronized(LOCK) {
            if (!CONFIRMATION_REQUESTED.add(state.sessionId())) {
                return
            }
        }
        val activity: Activity? = confirmationActivity.get()
        if (activity == null || activity.isFinishing()) {
            failInstall(
                runtime,
                "Keep the settings screen open to confirm the runtime download.",
                state.sessionId()
            )
            return
        }
        try {
            if (!requireNotNull(installManager).startConfirmationDialogForResult(
                    state, activity, CONFIRMATION_REQUEST_CODE
                )
            ) {
                failInstall(
                    runtime, "Could not show the runtime download confirmation.",
                    state.sessionId()
                )
            }
        } catch (exception: IntentSender.SendIntentException) {
            failInstall(
                runtime, "Could not show the runtime download confirmation.",
                state.sessionId()
            )
        }
    }

    private fun onRuntimeInstalled(runtime: LocalModelManager.Runtime) {
        SplitCompat.install(requireNotNull(appContext))
        setStatus(
            status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        )
        val otherRuntime: LocalModelManager.Runtime = if (runtime
            === LocalModelManager.Runtime.LLAMA_CPP
        )
            LocalModelManager.Runtime.LITERT_LM
        else
            LocalModelManager.Runtime.LLAMA_CPP
        setStatus(
            status(
                otherRuntime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        )
        startPendingModelDownload(runtime)
        startPendingModelDownload(otherRuntime)
    }

    private fun startPendingModelDownload(runtime: LocalModelManager.Runtime) {
        val modelId = getPendingModel(runtime)
        if (modelId.isEmpty()) {
            return
        }
        clearPendingModel(runtime)
        val error = LocalModelManager.downloadModel(requireNotNull(appContext), modelId)
        if (!error.isNullOrEmpty()) {
            setStatus(
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.FAILED,
                    0L,
                    0L,
                    error,
                    modelId,
                    0
                )
            )
        }
    }

    private fun failInstall(
        runtime: LocalModelManager.Runtime,
        error: String, sessionId: Int
    ) {
        kotlin.synchronized(LOCK) {
            CONFIRMATION_REQUESTED.remove(sessionId)
        }
        setStatus(
            status(
                runtime,
                com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.FAILED,
                0L,
                0L,
                error,
                getPendingModel(runtime),
                sessionId
            )
        )
    }

    private fun getTrackedStatus(runtime: LocalModelManager.Runtime): Status {
        kotlin.synchronized(LOCK) {
            val status = STATUSES.get(runtime)
            return if (status == null)
                status(
                    runtime,
                    com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.NOT_INSTALLED,
                    0L,
                    0L,
                    "",
                    "",
                    0
                )
            else
                status
        }
    }

    private fun setStatus(status: Status) {
        kotlin.synchronized(LOCK) {
            STATUSES.put(status.runtime, status)
        }
        notifyListeners()
    }

    private fun status(
        runtime: LocalModelManager.Runtime, state: State,
        bytesDownloaded: Long, totalBytes: Long, error: String,
        pendingModelId: String, sessionId: Int
    ): Status {
        return Status(
            runtime, state, bytesDownloaded, totalBytes,
            if (error == null) "" else error,
            if (pendingModelId == null) "" else pendingModelId, sessionId
        )
    }

    private fun notifyListeners() {
        if (appContext == null) {
            return
        }
        ContextCompat.getMainExecutor(requireNotNull(appContext)).execute({
            for (listener in LISTENERS) {
                listener.onRuntimeStatusChanged()
            }
        })
    }

    private fun getModuleName(runtime: LocalModelManager.Runtime): String {
        if (runtime === LocalModelManager.Runtime.LLAMA_CPP
            || runtime === LocalModelManager.Runtime.LITERT_LM
        ) {
            return MODULE_RUNTIME
        }
        throw IllegalArgumentException("Gemini Nano has no feature module")
    }

    @Nullable
    private fun getRuntimeForModules(
        modules: List<String>
    ): LocalModelManager.Runtime? {
        if (!modules.contains(MODULE_RUNTIME)) {
            return null
        }
        kotlin.synchronized(LOCK) {
            val llama = STATUSES.get(LocalModelManager.Runtime.LLAMA_CPP)
            if (llama != null && llama.isActive) {
                return LocalModelManager.Runtime.LLAMA_CPP
            }
            val litert = STATUSES.get(LocalModelManager.Runtime.LITERT_LM)
            if (litert != null && litert.isActive) {
                return LocalModelManager.Runtime.LITERT_LM
            }
        }
        if (!getPendingModel(LocalModelManager.Runtime.LLAMA_CPP).isEmpty()) {
            return LocalModelManager.Runtime.LLAMA_CPP
        }
        if (!getPendingModel(LocalModelManager.Runtime.LITERT_LM).isEmpty()) {
            return LocalModelManager.Runtime.LITERT_LM
        }
        return null
    }

    private val deliveryPreferences: SharedPreferences
        get() = requireNotNull(appContext).getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)

    private fun setPendingModel(runtime: LocalModelManager.Runtime, modelId: String) {
        deliveryPreferences.edit()
            .putString(KEY_PENDING_MODEL_PREFIX + runtime.name, modelId)
            .apply()
    }

    private fun getPendingModel(runtime: LocalModelManager.Runtime): String {
        return deliveryPreferences.getString(
            KEY_PENDING_MODEL_PREFIX + runtime.name, ""
        ).orEmpty()
    }

    private fun clearPendingModel(runtime: LocalModelManager.Runtime) {
        deliveryPreferences.edit()
            .remove(KEY_PENDING_MODEL_PREFIX + runtime.name)
            .apply()
    }

    private fun isRuntimeInstalledWithoutInitialization(
        runtime: LocalModelManager.Runtime
    ): Boolean {
        val manager = installManager ?: return false
        return manager.installedModules.contains(getModuleName(runtime))
    }

    @Nullable
    private fun findActivity(context: Context): Activity? {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current as Activity
            }
            val base: Context = (current as ContextWrapper).getBaseContext()
            if (base === current) {
                break
            }
            current = base
        }
        return if (current is Activity) current as Activity else null
    }

    private fun getInstallFailureMessage(failure: Exception): String {
        if (failure is SplitInstallException) {
            return getInstallErrorMessage((failure as SplitInstallException).getErrorCode())
        }
        val message = failure.message
        return if (message.isNullOrEmpty())
            "Could not install the local AI runtime."
        else
            "Could not install the local AI runtime: " + message
    }

    private fun getInstallErrorMessage(errorCode: Int): String {
        when (errorCode) {
            SplitInstallErrorCode.NETWORK_ERROR -> return "The runtime download failed because of a network error."
            SplitInstallErrorCode.INSUFFICIENT_STORAGE -> return "Not enough free space to install the local AI runtime."
            SplitInstallErrorCode.PLAY_STORE_NOT_FOUND -> return "Google Play is required to download the local AI runtime."
            SplitInstallErrorCode.API_NOT_AVAILABLE -> return "On-demand runtime delivery is unavailable on this device."
            SplitInstallErrorCode.MODULE_UNAVAILABLE -> return "This local AI runtime is unavailable for the installed app version."
            SplitInstallErrorCode.APP_NOT_OWNED -> return "Install Harmonic from Google Play to download this runtime."
            SplitInstallErrorCode.ACCESS_DENIED -> return "Keep Harmonic in the foreground while starting the runtime download."
            SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> return "Another app feature is currently being installed. Try again shortly."
            else -> return "Could not install the local AI runtime (error " + errorCode + ")."
        }
    }

    fun interface StatusListener {
        fun onRuntimeStatusChanged()
    }

    enum class State {
        NOT_INSTALLED,
        PENDING,
        DOWNLOADING,
        INSTALLING,
        INSTALLED,
        FAILED,
        CANCELED
    }

    class Status internal constructor(
        runtime: LocalModelManager.Runtime, state: State,
        bytesDownloaded: Long, totalBytes: Long, error: String,
        pendingModelId: String, sessionId: Int
    ) {
        val runtime: LocalModelManager.Runtime
        val state: State
        val bytesDownloaded: Long
        val totalBytes: Long
        val error: String
        val pendingModelId: String
        val sessionId: Int

        init {
            this.runtime = runtime
            this.state = state
            this.bytesDownloaded = bytesDownloaded
            this.totalBytes = totalBytes
            this.error = error
            this.pendingModelId = pendingModelId
            this.sessionId = sessionId
        }

        val isActive: Boolean
            get() = state == com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.PENDING || state == com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.DOWNLOADING || state == com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager.State.INSTALLING

        val progressPercent: Int
            get() {
                if (totalBytes <= 0L) {
                    return 0
                }
                return Math.min(100L, bytesDownloaded * 100L / totalBytes).toInt()
            }
    }
}
