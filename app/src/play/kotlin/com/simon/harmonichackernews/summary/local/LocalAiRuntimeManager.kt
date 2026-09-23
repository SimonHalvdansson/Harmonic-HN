package com.simon.harmonichackernews.summary.local

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallStatus
import com.simon.harmonichackernews.MainActivity
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.HashSet
import java.util.concurrent.CopyOnWriteArraySet

/** Installs Play-delivered local-AI runtimes before their model download starts.  */
internal class LocalAiRuntimeManager(
    private val installManagerFactory: (Context) -> SplitInstallManager = SplitInstallManagerFactory::create,
    private val installSplitCompat: (Context) -> Unit = { SplitCompat.install(it) },
    private val preferencesName: String = "local_ai_runtime_delivery",
) : Application.ActivityLifecycleCallbacks {
    private val MODULE_RUNTIME = "local_ai_runtime"
    private val ENGINE_LLAMA =
        "com.simon.harmonichackernews.localai.llama.LlamaInferenceEngine"
    private val ENGINE_LITERT =
        "com.simon.harmonichackernews.localai.litert.LiteRtInferenceEngine"
    private val KEY_PENDING_MODEL_PREFIX = "pending_model_"
    private val CONFIRMATION_REQUEST_CODE = 0x4c41

    private val LOCK = Any()
    private val LISTENERS: MutableSet<StatusListener> = CopyOnWriteArraySet()
    private val STATUSES: MutableMap<LocalModelRuntime, LocalRuntimeInstallStatus> =
        EnumMap(LocalModelRuntime::class.java)
    private val CONFIRMATION_REQUESTED: MutableSet<Int> = HashSet()

    private var appContext: Context? = null
    private var installManager: SplitInstallManager? = null
    private var confirmationActivity: WeakReference<Activity> = WeakReference(null)
    private var confirmationOwner: WeakReference<Activity> = WeakReference(null)
    private var application: Application? = null
    private val pendingConfirmations = mutableMapOf<Int, SplitInstallSessionState>()
    private val canceledSessions = mutableSetOf<Int>()
    private val requestGenerations = mutableMapOf<LocalModelRuntime, Int>()
    private val deferredInstallStates = mutableMapOf<Int, SplitInstallSessionState>()
    private var initialized = false
    private var modelDownloadStarter: ((String) -> String?)? = null

    private val INSTALL_LISTENER: SplitInstallStateUpdatedListener =
        SplitInstallStateUpdatedListener { installState: SplitInstallSessionState ->
            handleInstallState(installState)
        }

    // Bind when the application graph is created, before MainActivity resumes. Runtime
    // initialization itself can remain lazy and continue using applicationContext.
    fun bindActivityLifecycle(context: Context) {
        if (application != null) return
        application = context.applicationContext as? Application
        application?.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return
        confirmationActivity = WeakReference(activity)
        pendingConfirmations.values.toList().forEach { state ->
            getRuntimeForModules(state.moduleNames())?.let { requestConfirmation(it, state) }
        }
        // A confirmation destroyed without a choice can still be waiting in Play.
        installManager?.sessionStates?.addOnSuccessListener { states ->
            states.forEach(::handleInstallState)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (confirmationActivity.get() === activity) confirmationActivity.clear()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (confirmationActivity.get() === activity) confirmationActivity.clear()
        if (confirmationOwner.get() === activity) {
            confirmationOwner.clear()
            synchronized(LOCK) { CONFIRMATION_REQUESTED.clear() }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    internal fun close() {
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
        installManager?.unregisterListener(INSTALL_LISTENER)
        confirmationActivity.clear()
        confirmationOwner.clear()
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
        val tracked = synchronized(LOCK) { STATUSES[runtime] }
        return if (isRuntimeInstalled(context, runtime)) {
            // The split can finish despite an earlier install/cancel failure. Keep genuine
            // model-start errors separately while reporting the runtime's actual availability.
            tracked?.takeIf { it.state == LocalRuntimeInstallState.INSTALLED } ?: status(
                runtime,
                LocalRuntimeInstallState.INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        } else {
            tracked?.takeUnless { it.state == LocalRuntimeInstallState.INSTALLED } ?: status(
                runtime,
                LocalRuntimeInstallState.NOT_INSTALLED,
                0L,
                0L,
                "",
                "",
                0
            )
        }
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

    fun requestRuntimeAndModelDownload(context: Context, model: LocalModelDefinition): String? {
        initialize(context)
        if (!model.downloadable) {
            return "${model.displayName} is built into supported devices."
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

        val generation = synchronized(LOCK) {
            ((requestGenerations[model.runtime] ?: 0) + 1).also {
                requestGenerations[model.runtime] = it
            }
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
                if (synchronized(LOCK) { requestGenerations[model.runtime] != generation }) {
                    cancelSession(sessionId)
                    return@addOnSuccessListener
                }
                if (synchronized(LOCK) { sessionId in canceledSessions }) {
                    failInstall(model.runtime, "The previous runtime download is still canceling. Try again.", sessionId)
                    return@addOnSuccessListener
                }
                if (sessionId == 0 || isRuntimeInstalled(requireNotNull(appContext), model.runtime)) {
                    deferredInstallStates.clear()
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
                // A listener can beat startInstall's task. Apply only the state belonging
                // to the session Play returned for this request.
                val deferred = deferredInstallStates.remove(sessionId)
                deferredInstallStates.clear()
                deferred?.let(::handleInstallState)
            }
            .addOnFailureListener { failure ->
                if (synchronized(LOCK) { requestGenerations[model.runtime] != generation }) {
                    return@addOnFailureListener
                }
                deferredInstallStates.clear()
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
        synchronized(LOCK) { requestGenerations[runtime] = (requestGenerations[runtime] ?: 0) + 1 }
        deferredInstallStates.clear()
        clearPendingModel(runtime)
        cancelSession(current.sessionId)
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

    private fun cancelSession(sessionId: Int) {
        if (sessionId <= 0) return
        pendingConfirmations.remove(sessionId)
        deferredInstallStates.remove(sessionId)
        synchronized(LOCK) {
            CONFIRMATION_REQUESTED.remove(sessionId)
            canceledSessions.add(sessionId)
        }
        requireNotNull(installManager).cancelInstall(sessionId)
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
            installManager = installManagerFactory(applicationContext).also {
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
        // A delayed listener/query response for a canceled session must not attach to a
        // newer request for the same shared module or reopen its confirmation dialog.
        if (synchronized(LOCK) { installState.sessionId() in canceledSessions }) return
        val runtime: LocalModelRuntime? = getRuntimeForModules(installState.moduleNames())
        if (runtime == null) {
            return
        }
        val current = getTrackedStatus(runtime)
        if (current.isActive && current.sessionId == 0) {
            deferredInstallStates[installState.sessionId()] = installState
            return
        }
        if (current.isActive && current.sessionId != installState.sessionId()) return
        if (installState.status() != SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
            pendingConfirmations.remove(installState.sessionId())
            synchronized(LOCK) { CONFIRMATION_REQUESTED.remove(installState.sessionId()) }
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
        pendingConfirmations[state.sessionId()] = state
        val activity = confirmationActivity.get()
        // Keep the session pending while backgrounded; resume presents it from the new host.
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        synchronized(LOCK) {
            if (!CONFIRMATION_REQUESTED.add(state.sessionId())) {
                return
            }
        }
        try {
            confirmationOwner = WeakReference(activity)
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
        installSplitCompat(requireNotNull(appContext))
        val otherRuntime = if (runtime == LocalModelRuntime.LLAMA_CPP) {
            LocalModelRuntime.LITERT_LM
        } else {
            LocalModelRuntime.LLAMA_CPP
        }
        for (installedRuntime in listOf(runtime, otherRuntime)) {
            // Repeated Play callbacks or startup reconciliation cannot resolve a model
            // handoff failure. Only a successful model request clears that failure.
            if (getTrackedStatus(installedRuntime).state != LocalRuntimeInstallState.INSTALLED) {
                setStatus(status(installedRuntime, LocalRuntimeInstallState.INSTALLED, 0L, 0L, "", "", 0))
            }
        }
        startPendingModelDownload(runtime)
        startPendingModelDownload(otherRuntime)
    }

    private fun startPendingModelDownload(runtime: LocalModelRuntime) {
        val modelId = getPendingModel(runtime)
        if (modelId.isEmpty()) {
            return
        }
        // No starter yet means initialization has not finished; retain the pending model.
        val starter = modelDownloadStarter ?: return
        val error = starter(modelId)
        clearPendingModel(runtime)
        if (!error.isNullOrEmpty()) {
            setStatus(
                status(
                    runtime,
                    LocalRuntimeInstallState.INSTALLED,
                    0L,
                    0L,
                    "",
                    modelId,
                    0,
                    modelDownloadError = error,
                )
            )
        } else {
            setStatus(status(runtime, LocalRuntimeInstallState.INSTALLED, 0L, 0L, "", "", 0))
        }
    }

    private fun failInstall(
        runtime: LocalModelRuntime,
        error: String, sessionId: Int
    ) {
        synchronized(LOCK) {
            CONFIRMATION_REQUESTED.remove(sessionId)
        }
        pendingConfirmations.remove(sessionId)
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
        pendingModelId: String, sessionId: Int,
        modelDownloadError: String = "",
    ): LocalRuntimeInstallStatus {
        return LocalRuntimeInstallStatus(
            state = state,
            pendingModelId = pendingModelId,
            downloadedBytes = bytesDownloaded,
            totalBytes = totalBytes,
            runtime = runtime,
            error = error,
            sessionId = sessionId,
            modelDownloadError = modelDownloadError,
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
        get() = requireNotNull(appContext).getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun setPendingModel(runtime: LocalModelRuntime, modelId: String) {
        deliveryPreferences.edit {
            putString(KEY_PENDING_MODEL_PREFIX + runtime.name, modelId)
        }
    }

    private fun getPendingModel(runtime: LocalModelRuntime): String {
        return deliveryPreferences.getString(
            KEY_PENDING_MODEL_PREFIX + runtime.name, ""
        ).orEmpty()
    }

    private fun clearPendingModel(runtime: LocalModelRuntime) {
        deliveryPreferences.edit {
            remove(KEY_PENDING_MODEL_PREFIX + runtime.name)
        }
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

    internal fun setModelDownloadStarter(starter: (String) -> String?) {
        modelDownloadStarter = starter
        if (initialized) resumeInstalledPendingDownloads()
    }

    internal fun clearModelDownloadError(modelId: String) {
        val affected = synchronized(LOCK) {
            STATUSES.values.filter { it.pendingModelId == modelId && it.modelDownloadError.isNotEmpty() }
        }
        affected.forEach { setStatus(it.copy(pendingModelId = "", modelDownloadError = "")) }
    }

}

internal fun createAndroidLocalRuntimeDelivery(context: Context): AndroidLocalRuntimeDelivery {
    val manager = LocalAiRuntimeManager().also { it.bindActivityLifecycle(context) }
    return object : AndroidLocalRuntimeDelivery {
        private var listener: LocalAiRuntimeManager.StatusListener? = null

        override val included: Boolean get() = manager.isLocalAiIncluded()

        override fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus =
            manager.getStatus(context, runtime)

        override fun isInstalled(runtime: LocalModelRuntime): Boolean =
            manager.isRuntimeInstalled(context, runtime)

        override fun request(model: LocalModelDefinition): String? =
            manager.requestRuntimeAndModelDownload(context, model)

        override fun cancel(runtime: LocalModelRuntime) =
            manager.cancelRuntimeInstall(context, runtime)

        override fun setObserver(observer: () -> Unit) {
            listener?.let(manager::removeStatusListener)
            val created = LocalAiRuntimeManager.StatusListener(observer)
            listener = created
            manager.addStatusListener(context, created)
        }

        override fun setModelDownloadStarter(starter: (String) -> String?) =
            manager.setModelDownloadStarter(starter)

        override fun clearModelDownloadError(modelId: String) = manager.clearModelDownloadError(modelId)

        override fun engineClassName(runtime: LocalModelRuntime): String? =
            manager.getEngineClassName(runtime)

        override fun runtimeLabel(runtime: LocalModelRuntime): String =
            manager.getRuntimeLabel(runtime)
    }
}
