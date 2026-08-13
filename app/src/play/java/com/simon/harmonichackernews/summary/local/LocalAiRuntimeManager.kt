package com.simon.harmonichackernews.summary.local

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import android.content.SharedPreferences
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
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallStatus
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.HashSet
import java.util.concurrent.CopyOnWriteArraySet

/** Installs Play-delivered local-AI runtimes before their model download starts.  */
object LocalAiRuntimeManager {
    private const val MODULE_RUNTIME = "local_ai_runtime"
    private const val ENGINE_LLAMA =
        "com.simon.harmonichackernews.localai.llama.LlamaInferenceEngine"
    private const val ENGINE_LITERT =
        "com.simon.harmonichackernews.localai.litert.LiteRtInferenceEngine"
    private const val DELIVERY_PREFS = "local_ai_runtime_delivery"
    private const val KEY_PENDING_MODEL_PREFIX = "pending_model_"
    private const val CONFIRMATION_REQUEST_CODE = 0x4c41

    private val LOCK = Any()
    private val LISTENERS: MutableSet<StatusListener> = CopyOnWriteArraySet()
    private val STATUSES: MutableMap<LocalModelRuntime, LocalRuntimeInstallStatus> =
        EnumMap(LocalModelRuntime::class.java)
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

    fun getStatus(context: Context, runtime: LocalModelRuntime): LocalRuntimeInstallStatus {
        initialize(context)
        if (runtime == LocalModelRuntime.GEMINI_NANO) {
            return status(
                runtime,
                LocalRuntimeInstallState.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        }
        synchronized(LOCK) {
            val tracked = STATUSES[runtime]
            if (tracked != null && tracked.state != LocalRuntimeInstallState.INSTALLED) {
                return tracked
            }
        }
        return if (isRuntimeInstalled(context, runtime))
            status(
                runtime,
                LocalRuntimeInstallState.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        else
            status(
                runtime,
                LocalRuntimeInstallState.NOT_INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
    }

    fun isRuntimeInstalled(
        context: Context,
        runtime: LocalModelRuntime
    ): Boolean {
        if (runtime == LocalModelRuntime.GEMINI_NANO) {
            return true
        }
        initialize(context)
        return requireNotNull(installManager).installedModules.contains(getModuleName(runtime))
    }

    fun requestRuntimeAndModelDownload(context: Context, modelId: String?): String? {
        initialize(context)
        val model = LocalModelManager.getModel(modelId)
        if (!model.downloadable) {
            return "${model.displayName} is built into supported devices."
        }
        if (!LocalModelManager.isModelSupported(model)) {
            return "${LocalModelManager.getModelUnsupportedReason(model)}."
        }

        findActivity(context)?.let { confirmationActivity = WeakReference(it) }

        val current = getStatus(context, model.runtime)
        if (current.isActive) {
            if (model.id == current.pendingModelId) {
                return null
            }
            return "Wait for the current ${getRuntimeLabel(model.runtime)} installation to finish."
        }
        val otherRuntime = if (model.runtime == LocalModelRuntime.LLAMA_CPP) {
            LocalModelRuntime.LITERT_LM
        } else {
            LocalModelRuntime.LLAMA_CPP
        }
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
                LocalRuntimeInstallState.PENDING,
                0L,
                0L,
                "",
                model.id,
                0
            )
        )
        val request = SplitInstallRequest.newBuilder()
            .addModule(getModuleName(model.runtime))
            .build()
        requireNotNull(installManager).startInstall(request)
            .addOnSuccessListener { sessionId ->
                if (sessionId == 0 || isRuntimeInstalled(requireNotNull(appContext), model.runtime)) {
                    onRuntimeInstalled(model.runtime)
                    return@addOnSuccessListener
                }
                val latest = getTrackedStatus(model.runtime)
                setStatus(
                    status(
                        model.runtime,
                        LocalRuntimeInstallState.PENDING,
                        latest.bytesDownloaded,
                        latest.totalBytes,
                        "",
                        model.id,
                        sessionId
                    )
                )
            }
            .addOnFailureListener { failure ->
                failInstall(
                    model.runtime, getInstallFailureMessage(failure), 0
                )
            }
        return null
    }

    fun cancelRuntimeInstall(
        context: Context,
        runtime: LocalModelRuntime
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
                LocalRuntimeInstallState.CANCELED,
                current.bytesDownloaded,
                current.totalBytes,
                "",
                current.pendingModelId,
                current.sessionId
            )
        )
    }

    fun getRuntimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.LLAMA_CPP,
        LocalModelRuntime.LITERT_LM,
        -> "local AI runtime"
        LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
    }

    fun getEngineClassName(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.LLAMA_CPP -> ENGINE_LLAMA
        LocalModelRuntime.LITERT_LM -> ENGINE_LITERT
        LocalModelRuntime.GEMINI_NANO ->
            throw IllegalArgumentException("Gemini Nano does not use a feature runtime")
    }

    private fun initialize(context: Context) {
        synchronized(LOCK) {
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

        requireNotNull(installManager).sessionStates.addOnSuccessListener { states ->
            states.forEach(::handleInstallState)
            resumeInstalledPendingDownloads()
        }
        resumeInstalledPendingDownloads()
    }

    private fun resumeInstalledPendingDownloads() {
        for (runtime in arrayOf(
            LocalModelRuntime.LLAMA_CPP,
            LocalModelRuntime.LITERT_LM,
        )) {
            if (isRuntimeInstalledWithoutInitialization(runtime)
                && getPendingModel(runtime).isNotEmpty()
            ) {
                ContextCompat.getMainExecutor(requireNotNull(appContext)).execute {
                    onRuntimeInstalled(runtime)
                }
            }
        }
    }

    private fun handleInstallState(installState: SplitInstallSessionState) {
        val runtime: LocalModelRuntime? = getRuntimeForModules(installState.moduleNames())
        if (runtime == null) {
            return
        }
        val pendingModel = getPendingModel(runtime)
        when (installState.status()) {
            SplitInstallSessionStatus.PENDING -> setStatus(
                status(
                    runtime,
                    LocalRuntimeInstallState.PENDING,
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
                    LocalRuntimeInstallState.DOWNLOADING,
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
                    LocalRuntimeInstallState.INSTALLING,
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
                        LocalRuntimeInstallState.PENDING,
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
                synchronized(LOCK) {
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
                    LocalRuntimeInstallState.PENDING,
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
                        LocalRuntimeInstallState.CANCELED,
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
        runtime: LocalModelRuntime,
        state: SplitInstallSessionState
    ) {
        synchronized(LOCK) {
            if (!CONFIRMATION_REQUESTED.add(state.sessionId())) {
                return
            }
        }
        val activity = confirmationActivity.get()
        if (activity == null || activity.isFinishing) {
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
        } catch (_: IntentSender.SendIntentException) {
            failInstall(
                runtime, "Could not show the runtime download confirmation.",
                state.sessionId()
            )
        }
    }

    private fun onRuntimeInstalled(runtime: LocalModelRuntime) {
        SplitCompat.install(requireNotNull(appContext))
        setStatus(
            status(
                runtime,
                LocalRuntimeInstallState.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        )
        val otherRuntime = if (runtime == LocalModelRuntime.LLAMA_CPP) {
            LocalModelRuntime.LITERT_LM
        } else {
            LocalModelRuntime.LLAMA_CPP
        }
        setStatus(
            status(
                otherRuntime,
                LocalRuntimeInstallState.INSTALLED,
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

    private fun startPendingModelDownload(runtime: LocalModelRuntime) {
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
                    LocalRuntimeInstallState.FAILED,
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
        runtime: LocalModelRuntime,
        error: String, sessionId: Int
    ) {
        synchronized(LOCK) {
            CONFIRMATION_REQUESTED.remove(sessionId)
        }
        setStatus(
            status(
                runtime,
                LocalRuntimeInstallState.FAILED,
                0L,
                0L,
                error,
                getPendingModel(runtime),
                sessionId
            )
        )
    }

    private fun getTrackedStatus(runtime: LocalModelRuntime): LocalRuntimeInstallStatus {
        synchronized(LOCK) {
            return STATUSES[runtime]
                ?: status(
                    runtime,
                    LocalRuntimeInstallState.NOT_INSTALLED,
                    0L,
                    0L,
                    "",
                    "",
                    0,
                )
        }
    }

    private fun setStatus(status: LocalRuntimeInstallStatus) {
        synchronized(LOCK) {
            STATUSES[requireNotNull(status.runtime)] = status
        }
        notifyListeners()
    }

    private fun status(
        runtime: LocalModelRuntime, state: LocalRuntimeInstallState,
        bytesDownloaded: Long, totalBytes: Long, error: String,
        pendingModelId: String, sessionId: Int
    ): LocalRuntimeInstallStatus {
        return LocalRuntimeInstallStatus(
            state = state,
            pendingModelId = pendingModelId,
            downloadedBytes = bytesDownloaded,
            totalBytes = totalBytes,
            runtime = runtime,
            error = error,
            sessionId = sessionId,
        )
    }

    private fun notifyListeners() {
        if (appContext == null) {
            return
        }
        ContextCompat.getMainExecutor(requireNotNull(appContext)).execute {
            for (listener in LISTENERS) {
                listener.onRuntimeStatusChanged()
            }
        }
    }

    private fun getModuleName(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.LLAMA_CPP,
        LocalModelRuntime.LITERT_LM,
        -> MODULE_RUNTIME
        LocalModelRuntime.GEMINI_NANO ->
            throw IllegalArgumentException("Gemini Nano has no feature module")
    }

    private fun getRuntimeForModules(
        modules: List<String>,
    ): LocalModelRuntime? {
        if (!modules.contains(MODULE_RUNTIME)) {
            return null
        }
        synchronized(LOCK) {
            val llama = STATUSES[LocalModelRuntime.LLAMA_CPP]
            if (llama != null && llama.isActive) {
                return LocalModelRuntime.LLAMA_CPP
            }
            val litert = STATUSES[LocalModelRuntime.LITERT_LM]
            if (litert != null && litert.isActive) {
                return LocalModelRuntime.LITERT_LM
            }
        }
        if (getPendingModel(LocalModelRuntime.LLAMA_CPP).isNotEmpty()) {
            return LocalModelRuntime.LLAMA_CPP
        }
        if (getPendingModel(LocalModelRuntime.LITERT_LM).isNotEmpty()) {
            return LocalModelRuntime.LITERT_LM
        }
        return null
    }

    private val deliveryPreferences: SharedPreferences
        get() = requireNotNull(appContext).getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)

    private fun setPendingModel(runtime: LocalModelRuntime, modelId: String) {
        deliveryPreferences.edit()
            .putString(KEY_PENDING_MODEL_PREFIX + runtime.name, modelId)
            .apply()
    }

    private fun getPendingModel(runtime: LocalModelRuntime): String {
        return deliveryPreferences.getString(
            KEY_PENDING_MODEL_PREFIX + runtime.name, ""
        ).orEmpty()
    }

    private fun clearPendingModel(runtime: LocalModelRuntime) {
        deliveryPreferences.edit()
            .remove(KEY_PENDING_MODEL_PREFIX + runtime.name)
            .apply()
    }

    private fun isRuntimeInstalledWithoutInitialization(
        runtime: LocalModelRuntime
    ): Boolean {
        val manager = installManager ?: return false
        return manager.installedModules.contains(getModuleName(runtime))
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context = context
        while (true) {
            if (current is Activity) {
                return current
            }
            val base = (current as? ContextWrapper)?.baseContext ?: return null
            if (base === current) {
                return null
            }
            current = base
        }
    }

    private fun getInstallFailureMessage(failure: Exception): String =
        if (failure is SplitInstallException) {
            getInstallErrorMessage(failure.errorCode)
        } else {
            failure.message?.takeIf(String::isNotEmpty)?.let {
                "Could not install the local AI runtime: $it"
            } ?: "Could not install the local AI runtime."
        }

    private fun getInstallErrorMessage(errorCode: Int): String = when (errorCode) {
        SplitInstallErrorCode.NETWORK_ERROR ->
            "The runtime download failed because of a network error."
        SplitInstallErrorCode.INSUFFICIENT_STORAGE ->
            "Not enough free space to install the local AI runtime."
        SplitInstallErrorCode.PLAY_STORE_NOT_FOUND ->
            "Google Play is required to download the local AI runtime."
        SplitInstallErrorCode.API_NOT_AVAILABLE ->
            "On-demand runtime delivery is unavailable on this device."
        SplitInstallErrorCode.MODULE_UNAVAILABLE ->
            "This local AI runtime is unavailable for the installed app version."
        SplitInstallErrorCode.APP_NOT_OWNED ->
            "Install Harmonic from Google Play to download this runtime."
        SplitInstallErrorCode.ACCESS_DENIED ->
            "Keep Harmonic in the foreground while starting the runtime download."
        SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED ->
            "Another app feature is currently being installed. Try again shortly."
        else -> "Could not install the local AI runtime (error $errorCode)."
    }

    fun interface StatusListener {
        fun onRuntimeStatusChanged()
    }

}
