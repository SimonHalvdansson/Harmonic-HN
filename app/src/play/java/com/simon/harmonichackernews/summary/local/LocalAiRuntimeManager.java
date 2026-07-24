package com.simon.harmonichackernews.summary.local;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentSender;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.play.core.splitcompat.SplitCompat;
import com.google.android.play.core.splitinstall.SplitInstallException;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Installs Play-delivered local-AI runtimes before their model download starts. */
public final class LocalAiRuntimeManager {
    private static final String MODULE_RUNTIME = "local_ai_runtime";
    private static final String ENGINE_LLAMA =
            "com.simon.harmonichackernews.localai.llama.LlamaInferenceEngine";
    private static final String ENGINE_LITERT =
            "com.simon.harmonichackernews.localai.litert.LiteRtInferenceEngine";
    private static final String DELIVERY_PREFS = "local_ai_runtime_delivery";
    private static final String KEY_PENDING_MODEL_PREFIX = "pending_model_";
    private static final int CONFIRMATION_REQUEST_CODE = 0x4c41;

    private static final Object LOCK = new Object();
    private static final Set<StatusListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Map<LocalModelManager.Runtime, Status> STATUSES =
            new EnumMap<>(LocalModelManager.Runtime.class);
    private static final Set<Integer> CONFIRMATION_REQUESTED = new HashSet<>();

    private static Context appContext;
    private static SplitInstallManager installManager;
    private static WeakReference<Activity> confirmationActivity = new WeakReference<>(null);
    private static boolean initialized;

    private static final SplitInstallStateUpdatedListener INSTALL_LISTENER =
            LocalAiRuntimeManager::handleInstallState;

    private LocalAiRuntimeManager() {
    }

    public interface StatusListener {
        void onRuntimeStatusChanged();
    }

    public enum State {
        NOT_INSTALLED,
        PENDING,
        DOWNLOADING,
        INSTALLING,
        INSTALLED,
        FAILED,
        CANCELED
    }

    public static final class Status {
        public final LocalModelManager.Runtime runtime;
        public final State state;
        public final long bytesDownloaded;
        public final long totalBytes;
        public final String error;
        public final String pendingModelId;
        public final int sessionId;

        private Status(LocalModelManager.Runtime runtime, State state,
                       long bytesDownloaded, long totalBytes, String error,
                       String pendingModelId, int sessionId) {
            this.runtime = runtime;
            this.state = state;
            this.bytesDownloaded = bytesDownloaded;
            this.totalBytes = totalBytes;
            this.error = error;
            this.pendingModelId = pendingModelId;
            this.sessionId = sessionId;
        }

        public boolean isActive() {
            return state == State.PENDING
                    || state == State.DOWNLOADING
                    || state == State.INSTALLING;
        }

        public int getProgressPercent() {
            if (totalBytes <= 0L) {
                return 0;
            }
            return (int) Math.min(100L, bytesDownloaded * 100L / totalBytes);
        }
    }

    public static void addStatusListener(Context context, StatusListener listener) {
        initialize(context);
        LISTENERS.add(listener);
        listener.onRuntimeStatusChanged();
    }

    public static void removeStatusListener(StatusListener listener) {
        LISTENERS.remove(listener);
    }

    public static Status getStatus(Context context, LocalModelManager.Runtime runtime) {
        initialize(context);
        if (runtime == LocalModelManager.Runtime.GEMINI_NANO) {
            return status(runtime, State.INSTALLED, 0L, 0L, "", "", 0);
        }
        synchronized (LOCK) {
            Status tracked = STATUSES.get(runtime);
            if (tracked != null && tracked.state != State.INSTALLED) {
                return tracked;
            }
        }
        return isRuntimeInstalled(context, runtime)
                ? status(runtime, State.INSTALLED, 0L, 0L, "", "", 0)
                : status(runtime, State.NOT_INSTALLED, 0L, 0L, "", "", 0);
    }

    public static boolean isRuntimeInstalled(Context context,
                                             LocalModelManager.Runtime runtime) {
        if (runtime == LocalModelManager.Runtime.GEMINI_NANO) {
            return true;
        }
        initialize(context);
        return installManager.getInstalledModules().contains(getModuleName(runtime));
    }

    @Nullable
    public static String requestRuntimeAndModelDownload(Context context, String modelId) {
        initialize(context);
        LocalModelManager.ModelInfo model = LocalModelManager.getModel(modelId);
        if (!model.downloadable) {
            return model.displayName + " is built into supported devices.";
        }
        if (!LocalModelManager.isModelSupported(model)) {
            return LocalModelManager.getModelUnsupportedReason(model) + ".";
        }

        Activity activity = findActivity(context);
        if (activity != null) {
            confirmationActivity = new WeakReference<>(activity);
        }

        Status current = getStatus(context, model.runtime);
        if (current.isActive()) {
            if (model.id.equals(current.pendingModelId)) {
                return null;
            }
            return "Wait for the current " + getRuntimeLabel(model.runtime)
                    + " installation to finish.";
        }
        LocalModelManager.Runtime otherRuntime = model.runtime
                == LocalModelManager.Runtime.LLAMA_CPP
                ? LocalModelManager.Runtime.LITERT_LM
                : LocalModelManager.Runtime.LLAMA_CPP;
        if (getStatus(context, otherRuntime).isActive()) {
            return "Wait for the current local AI runtime installation to finish.";
        }

        setPendingModel(model.runtime, model.id);
        if (isRuntimeInstalled(context, model.runtime)) {
            startPendingModelDownload(model.runtime);
            return null;
        }

        setStatus(status(model.runtime, State.PENDING, 0L, 0L,
                "", model.id, 0));
        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(getModuleName(model.runtime))
                .build();
        installManager.startInstall(request)
                .addOnSuccessListener(sessionId -> {
                    if (sessionId == 0 || isRuntimeInstalled(appContext, model.runtime)) {
                        onRuntimeInstalled(model.runtime);
                        return;
                    }
                    Status latest = getTrackedStatus(model.runtime);
                    setStatus(status(model.runtime, State.PENDING,
                            latest.bytesDownloaded, latest.totalBytes, "",
                            model.id, sessionId));
                })
                .addOnFailureListener(failure -> failInstall(
                        model.runtime, getInstallFailureMessage(failure), 0));
        return null;
    }

    public static void cancelRuntimeInstall(Context context,
                                            LocalModelManager.Runtime runtime) {
        initialize(context);
        Status current = getStatus(context, runtime);
        clearPendingModel(runtime);
        if (current.sessionId > 0) {
            installManager.cancelInstall(current.sessionId);
        }
        setStatus(status(runtime, State.CANCELED, current.bytesDownloaded,
                current.totalBytes, "", current.pendingModelId, current.sessionId));
    }

    public static String getRuntimeLabel(LocalModelManager.Runtime runtime) {
        if (runtime == LocalModelManager.Runtime.LLAMA_CPP
                || runtime == LocalModelManager.Runtime.LITERT_LM) {
            return "local AI runtime";
        }
        return "Gemini Nano";
    }

    public static String getEngineClassName(LocalModelManager.Runtime runtime) {
        if (runtime == LocalModelManager.Runtime.LLAMA_CPP) {
            return ENGINE_LLAMA;
        }
        if (runtime == LocalModelManager.Runtime.LITERT_LM) {
            return ENGINE_LITERT;
        }
        throw new IllegalArgumentException("Gemini Nano does not use a feature runtime");
    }

    private static void initialize(Context context) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            appContext = context.getApplicationContext();
            installManager = SplitInstallManagerFactory.create(appContext);
            installManager.registerListener(INSTALL_LISTENER);
            initialized = true;
        }

        installManager.getSessionStates().addOnSuccessListener(states -> {
            for (SplitInstallSessionState state : states) {
                handleInstallState(state);
            }
            resumeInstalledPendingDownloads();
        });
        resumeInstalledPendingDownloads();
    }

    private static void resumeInstalledPendingDownloads() {
        for (LocalModelManager.Runtime runtime : new LocalModelManager.Runtime[]{
                LocalModelManager.Runtime.LLAMA_CPP,
                LocalModelManager.Runtime.LITERT_LM}) {
            if (isRuntimeInstalledWithoutInitialization(runtime)
                    && !getPendingModel(runtime).isEmpty()) {
                ContextCompat.getMainExecutor(appContext).execute(
                        () -> onRuntimeInstalled(runtime));
            }
        }
    }

    private static void handleInstallState(SplitInstallSessionState installState) {
        LocalModelManager.Runtime runtime = getRuntimeForModules(installState.moduleNames());
        if (runtime == null) {
            return;
        }
        String pendingModel = getPendingModel(runtime);
        switch (installState.status()) {
            case SplitInstallSessionStatus.PENDING:
                setStatus(status(runtime, State.PENDING, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                break;
            case SplitInstallSessionStatus.DOWNLOADING:
                setStatus(status(runtime, State.DOWNLOADING, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                break;
            case SplitInstallSessionStatus.DOWNLOADED:
            case SplitInstallSessionStatus.INSTALLING:
                setStatus(status(runtime, State.INSTALLING, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                break;
            case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
                setStatus(status(runtime, State.PENDING, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                requestConfirmation(runtime, installState);
                break;
            case SplitInstallSessionStatus.INSTALLED:
                synchronized (LOCK) {
                    CONFIRMATION_REQUESTED.remove(installState.sessionId());
                }
                onRuntimeInstalled(runtime);
                break;
            case SplitInstallSessionStatus.FAILED:
                failInstall(runtime, getInstallErrorMessage(installState.errorCode()),
                        installState.sessionId());
                break;
            case SplitInstallSessionStatus.CANCELING:
                setStatus(status(runtime, State.PENDING, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                break;
            case SplitInstallSessionStatus.CANCELED:
                clearPendingModel(runtime);
                setStatus(status(runtime, State.CANCELED, installState.bytesDownloaded(),
                        installState.totalBytesToDownload(), "", pendingModel,
                        installState.sessionId()));
                break;
            default:
                break;
        }
    }

    private static void requestConfirmation(LocalModelManager.Runtime runtime,
                                            SplitInstallSessionState state) {
        synchronized (LOCK) {
            if (!CONFIRMATION_REQUESTED.add(state.sessionId())) {
                return;
            }
        }
        Activity activity = confirmationActivity.get();
        if (activity == null || activity.isFinishing()) {
            failInstall(runtime,
                    "Keep the settings screen open to confirm the runtime download.",
                    state.sessionId());
            return;
        }
        try {
            if (!installManager.startConfirmationDialogForResult(
                    state, activity, CONFIRMATION_REQUEST_CODE)) {
                failInstall(runtime, "Could not show the runtime download confirmation.",
                        state.sessionId());
            }
        } catch (IntentSender.SendIntentException exception) {
            failInstall(runtime, "Could not show the runtime download confirmation.",
                    state.sessionId());
        }
    }

    private static void onRuntimeInstalled(LocalModelManager.Runtime runtime) {
        SplitCompat.install(appContext);
        setStatus(status(runtime, State.INSTALLED, 0L, 0L, "", "", 0));
        LocalModelManager.Runtime otherRuntime = runtime
                == LocalModelManager.Runtime.LLAMA_CPP
                ? LocalModelManager.Runtime.LITERT_LM
                : LocalModelManager.Runtime.LLAMA_CPP;
        setStatus(status(otherRuntime, State.INSTALLED, 0L, 0L, "", "", 0));
        startPendingModelDownload(runtime);
        startPendingModelDownload(otherRuntime);
    }

    private static void startPendingModelDownload(LocalModelManager.Runtime runtime) {
        String modelId = getPendingModel(runtime);
        if (modelId.isEmpty()) {
            return;
        }
        clearPendingModel(runtime);
        String error = LocalModelManager.downloadModel(appContext, modelId);
        if (error != null && !error.isEmpty()) {
            setStatus(status(runtime, State.FAILED, 0L, 0L, error, modelId, 0));
        }
    }

    private static void failInstall(LocalModelManager.Runtime runtime,
                                    String error, int sessionId) {
        synchronized (LOCK) {
            CONFIRMATION_REQUESTED.remove(sessionId);
        }
        setStatus(status(runtime, State.FAILED, 0L, 0L, error,
                getPendingModel(runtime), sessionId));
    }

    private static Status getTrackedStatus(LocalModelManager.Runtime runtime) {
        synchronized (LOCK) {
            Status status = STATUSES.get(runtime);
            return status == null
                    ? status(runtime, State.NOT_INSTALLED, 0L, 0L, "", "", 0)
                    : status;
        }
    }

    private static void setStatus(Status status) {
        synchronized (LOCK) {
            STATUSES.put(status.runtime, status);
        }
        notifyListeners();
    }

    private static Status status(LocalModelManager.Runtime runtime, State state,
                                 long bytesDownloaded, long totalBytes, String error,
                                 String pendingModelId, int sessionId) {
        return new Status(runtime, state, bytesDownloaded, totalBytes,
                error == null ? "" : error,
                pendingModelId == null ? "" : pendingModelId, sessionId);
    }

    private static void notifyListeners() {
        if (appContext == null) {
            return;
        }
        ContextCompat.getMainExecutor(appContext).execute(() -> {
            for (StatusListener listener : LISTENERS) {
                listener.onRuntimeStatusChanged();
            }
        });
    }

    private static String getModuleName(LocalModelManager.Runtime runtime) {
        if (runtime == LocalModelManager.Runtime.LLAMA_CPP
                || runtime == LocalModelManager.Runtime.LITERT_LM) {
            return MODULE_RUNTIME;
        }
        throw new IllegalArgumentException("Gemini Nano has no feature module");
    }

    @Nullable
    private static LocalModelManager.Runtime getRuntimeForModules(
            java.util.List<String> modules) {
        if (!modules.contains(MODULE_RUNTIME)) {
            return null;
        }
        synchronized (LOCK) {
            Status llama = STATUSES.get(LocalModelManager.Runtime.LLAMA_CPP);
            if (llama != null && llama.isActive()) {
                return LocalModelManager.Runtime.LLAMA_CPP;
            }
            Status litert = STATUSES.get(LocalModelManager.Runtime.LITERT_LM);
            if (litert != null && litert.isActive()) {
                return LocalModelManager.Runtime.LITERT_LM;
            }
        }
        if (!getPendingModel(LocalModelManager.Runtime.LLAMA_CPP).isEmpty()) {
            return LocalModelManager.Runtime.LLAMA_CPP;
        }
        if (!getPendingModel(LocalModelManager.Runtime.LITERT_LM).isEmpty()) {
            return LocalModelManager.Runtime.LITERT_LM;
        }
        return null;
    }

    private static SharedPreferences getDeliveryPreferences() {
        return appContext.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE);
    }

    private static void setPendingModel(LocalModelManager.Runtime runtime, String modelId) {
        getDeliveryPreferences().edit()
                .putString(KEY_PENDING_MODEL_PREFIX + runtime.name(), modelId)
                .apply();
    }

    private static String getPendingModel(LocalModelManager.Runtime runtime) {
        return getDeliveryPreferences().getString(
                KEY_PENDING_MODEL_PREFIX + runtime.name(), "");
    }

    private static void clearPendingModel(LocalModelManager.Runtime runtime) {
        getDeliveryPreferences().edit()
                .remove(KEY_PENDING_MODEL_PREFIX + runtime.name())
                .apply();
    }

    private static boolean isRuntimeInstalledWithoutInitialization(
            LocalModelManager.Runtime runtime) {
        return installManager != null
                && installManager.getInstalledModules().contains(getModuleName(runtime));
    }

    @Nullable
    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static String getInstallFailureMessage(Exception failure) {
        if (failure instanceof SplitInstallException) {
            return getInstallErrorMessage(((SplitInstallException) failure).getErrorCode());
        }
        String message = failure.getMessage();
        return message == null || message.isEmpty()
                ? "Could not install the local AI runtime."
                : "Could not install the local AI runtime: " + message;
    }

    private static String getInstallErrorMessage(int errorCode) {
        switch (errorCode) {
            case SplitInstallErrorCode.NETWORK_ERROR:
                return "The runtime download failed because of a network error.";
            case SplitInstallErrorCode.INSUFFICIENT_STORAGE:
                return "Not enough free space to install the local AI runtime.";
            case SplitInstallErrorCode.PLAY_STORE_NOT_FOUND:
                return "Google Play is required to download the local AI runtime.";
            case SplitInstallErrorCode.API_NOT_AVAILABLE:
                return "On-demand runtime delivery is unavailable on this device.";
            case SplitInstallErrorCode.MODULE_UNAVAILABLE:
                return "This local AI runtime is unavailable for the installed app version.";
            case SplitInstallErrorCode.APP_NOT_OWNED:
                return "Install Harmonic from Google Play to download this runtime.";
            case SplitInstallErrorCode.ACCESS_DENIED:
                return "Keep Harmonic in the foreground while starting the runtime download.";
            case SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED:
                return "Another app feature is currently being installed. Try again shortly.";
            default:
                return "Could not install the local AI runtime (error " + errorCode + ").";
        }
    }
}
