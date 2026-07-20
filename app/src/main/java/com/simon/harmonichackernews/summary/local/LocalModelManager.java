package com.simon.harmonichackernews.summary.local;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.simon.harmonichackernews.R;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Catalog and lifecycle for Gemini Nano and downloadable local LLMs. */
public final class LocalModelManager {
    public static final String PREF_SELECTED_MODEL = "pref_ai_local_model";
    public static final String MODEL_GEMINI_NANO = "gemini-nano";
    public static final String MODEL_E2B = "gemma-4-e2b";
    public static final String MODEL_E4B = "gemma-4-e4b";
    public static final String MODEL_BONSAI_17B = "bonsai-1.7b";
    public static final String MODEL_BONSAI_4B = "bonsai-4b";
    public static final String MODEL_BONSAI_8B = "bonsai-8b";
    public static final String MODEL_QWEN_08B = "qwen-3.5-0.8b";
    public static final String MODEL_NEMOTRON_4B = "nemotron-3-nano-4b";
    public static final String MODEL_MINISTRAL_3B = "ministral-3-3b";
    public static final String MODEL_LFM_12B = "lfm-2.5-1.2b";

    private static final String WORK_NAME_PREFIX = "local_ai_model_download_";
    private static final String MODELS_DIR = "local_ai_models";
    private static final String LEGACY_E2B_FILE_NAME = "gemma-4-E2B-it.litertlm";
    private static final String LEGACY_QWEN_EXACT_FILE_NAME =
            "Qwen3.5-0.8B-hybrid-exact-c2048.litertlm";
    private static final long STORAGE_BUFFER_BYTES = 256L * 1024L * 1024L;

    private static final ModelInfo GEMINI_NANO = new ModelInfo(
            MODEL_GEMINI_NANO, "Gemini Nano", "System managed", "",
            R.drawable.model_logo_google, "", "", 0L, false,
            Runtime.GEMINI_NANO, 0);
    private static final ModelInfo E2B = new ModelInfo(
            MODEL_E2B,
            "Gemma 4 E2B",
            "2B effective",
            "QAT 2/4/8-bit",
            R.drawable.model_logo_google,
            "gemma-4-E2B-it-qat-mobile.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/"
                    + "361a4010ad6d88fc5c86e148e333c0342b99763d/gemma-4-E2B-it.litertlm?download=true",
            2_588_147_712L,
            true,
            Runtime.LITERT_LM,
            4096);
    private static final ModelInfo E4B = new ModelInfo(
            MODEL_E4B,
            "Gemma 4 E4B",
            "4B effective",
            "4-bit per-channel",
            R.drawable.model_logo_google,
            "gemma-4-E4B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/"
                    + "9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
            3_654_467_584L,
            true,
            Runtime.LITERT_LM,
            4096);
    private static final ModelInfo BONSAI_17B = new ModelInfo(
            MODEL_BONSAI_17B,
            "Bonsai 1.7B",
            "1.7B",
            "Q1_0",
            R.drawable.model_logo_prism,
            "Bonsai-1.7B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-1.7B-gguf/resolve/"
                    + "210a9e99f79cb184909d49595906526eb2b3dd9a/"
                    + "Bonsai-1.7B-Q1_0.gguf?download=true",
            248_302_272L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final ModelInfo BONSAI_4B = new ModelInfo(
            MODEL_BONSAI_4B,
            "Bonsai 4B",
            "4B",
            "Q1_0",
            R.drawable.model_logo_prism,
            "Bonsai-4B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-4B-gguf/resolve/"
                    + "78f2c2bacd0904ffaba24b4873ed975e5818354a/"
                    + "Bonsai-4B-Q1_0.gguf?download=true",
            572_270_624L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final ModelInfo BONSAI_8B = new ModelInfo(
            MODEL_BONSAI_8B,
            "Bonsai 8B",
            "8B",
            "Q1_0",
            R.drawable.model_logo_prism,
            "Bonsai-8B-Q1_0.gguf",
            "https://huggingface.co/prism-ml/Bonsai-8B-gguf/resolve/"
                    + "48516770dd04643643e9f9019a2a349cf26c5dbd/"
                    + "Bonsai-8B-Q1_0.gguf?download=true",
            1_158_654_496L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final ModelInfo QWEN_08B = new ModelInfo(
            MODEL_QWEN_08B,
            "Qwen 3.5 0.8B",
            "0.8B",
            "Q4_K_M",
            R.drawable.model_logo_qwen,
            "Qwen3.5-0.8B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/"
                    + "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/"
                    + "Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
            532_517_120L,
            true,
            Runtime.LLAMA_CPP,
            2048);
    private static final ModelInfo NEMOTRON_4B = new ModelInfo(
            MODEL_NEMOTRON_4B,
            "Nemotron 3 Nano 4B",
            "4B",
            "Q4_K_M",
            R.drawable.model_logo_nvidia,
            "NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf",
            "https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF/resolve/"
                    + "18d83da545bdfde657afff71123d7ffc8965edfa/"
                    + "NVIDIA-Nemotron3-Nano-4B-Q4_K_M.gguf?download=true",
            2_837_072_864L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final ModelInfo MINISTRAL_3B = new ModelInfo(
            MODEL_MINISTRAL_3B,
            "Ministral 3 3B",
            "3.4B",
            "Q4_K_M",
            R.drawable.model_logo_mistral,
            "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/"
                    + "eb599d408350ea2bb60452cb86be7c7b2fc28227/"
                    + "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf?download=true",
            2_147_023_008L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final ModelInfo LFM_12B = new ModelInfo(
            MODEL_LFM_12B,
            "LFM2.5 1.2B",
            "1.2B",
            "Q4_K_M",
            R.drawable.model_logo_liquid,
            "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/"
                    + "047e06635fbe71469926b35ea414537245218200/"
                    + "LFM2.5-1.2B-Instruct-Q4_K_M.gguf?download=true",
            730_895_168L,
            true,
            Runtime.LLAMA_CPP,
            4096);
    private static final List<ModelInfo> MODELS = Collections.unmodifiableList(Arrays.asList(
            GEMINI_NANO, E2B, E4B,
            BONSAI_17B, BONSAI_4B, BONSAI_8B, QWEN_08B,
            NEMOTRON_4B, MINISTRAL_3B, LFM_12B));

    private static final Set<StatusListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Map<String, WorkInfo> CURRENT_WORK = new ConcurrentHashMap<>();
    private static boolean initialized;
    private static Context appContext;

    private LocalModelManager() {
    }

    public interface StatusListener {
        void onStatusChanged(Status status);
    }

    public enum State {
        NOT_DOWNLOADED,
        PARTIALLY_DOWNLOADED,
        WAITING,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }

    public enum Runtime {
        GEMINI_NANO,
        LITERT_LM,
        LLAMA_CPP
    }

    public static final class ModelInfo {
        public final String id;
        public final String displayName;
        public final String parameterSize;
        public final String quantization;
        public final int iconResId;
        public final String fileName;
        public final String url;
        public final long sizeBytes;
        public final boolean downloadable;
        public final Runtime runtime;
        public final int contextTokens;

        private ModelInfo(String id, String displayName, String parameterSize,
                          String quantization, int iconResId, String fileName,
                          String url, long sizeBytes, boolean downloadable,
                          Runtime runtime, int contextTokens) {
            this.id = id;
            this.displayName = displayName;
            this.parameterSize = parameterSize;
            this.quantization = quantization;
            this.iconResId = iconResId;
            this.fileName = fileName;
            this.url = url;
            this.sizeBytes = sizeBytes;
            this.downloadable = downloadable;
            this.runtime = runtime;
            this.contextTokens = contextTokens;
        }
    }

    public static final class Status {
        public final ModelInfo model;
        public final State state;
        public final long receivedBytes;
        public final String error;

        private Status(ModelInfo model, State state, long receivedBytes, String error) {
            this.model = model;
            this.state = state;
            this.receivedBytes = receivedBytes;
            this.error = error;
        }

        public int getProgressPercent() {
            if (model.sizeBytes <= 0L) {
                return 0;
            }
            return (int) Math.min(100L, receivedBytes * 100L / model.sizeBytes);
        }
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isModelSupported(ModelInfo model) {
        if (!model.downloadable) {
            return true;
        }
        return isSupported()
                && (model.runtime != Runtime.LITERT_LM || Process.is64Bit());
    }

    public static String getModelUnsupportedReason(ModelInfo model) {
        if (!model.downloadable || isModelSupported(model)) {
            return "";
        }
        if (!isSupported()) {
            return "Requires Android 12 or newer";
        }
        return "Requires a 64-bit Android device";
    }

    public static List<ModelInfo> getModels() {
        return MODELS;
    }

    public static ModelInfo getSelectedModel(Context context) {
        String id = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_SELECTED_MODEL, MODEL_GEMINI_NANO);
        return getModel(id);
    }

    public static ModelInfo getModel(String id) {
        for (ModelInfo model : MODELS) {
            if (model.id.equals(id)) {
                return model;
            }
        }
        return GEMINI_NANO;
    }

    public static void selectModel(Context context, String modelId) {
        ModelInfo model = getModel(modelId);
        if (!isModelSupported(model)
                || (model.downloadable && !isModelDownloaded(context, model))) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_SELECTED_MODEL, model.id)
                .apply();
        notifyListeners();
    }

    public static void clearSelectedModel(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(PREF_SELECTED_MODEL)
                .apply();
        notifyListeners();
    }

    public static boolean isSelectedModelDownloaded(Context context) {
        return isModelDownloaded(context, getSelectedModel(context));
    }

    public static boolean isModelDownloaded(Context context, ModelInfo model) {
        if (!model.downloadable) {
            return false;
        }
        File file = getModelFile(context, model.id, model.fileName);
        return file.isFile() && file.length() == model.sizeBytes;
    }

    public static String getSelectedModelPath(Context context) {
        ModelInfo model = getSelectedModel(context);
        return getModelFile(context, model.id, model.fileName).getAbsolutePath();
    }

    public static Status getSelectedStatus(Context context) {
        initialize(context);
        return getStatus(context, getSelectedModel(context));
    }

    public static Status getStatus(Context context, ModelInfo model) {
        initialize(context);
        if (!model.downloadable) {
            return new Status(model, State.NOT_DOWNLOADED, 0L, "");
        }

        File finalFile = getModelFile(context, model.id, model.fileName);
        if (finalFile.isFile() && finalFile.length() == model.sizeBytes) {
            return new Status(model, State.DOWNLOADED, model.sizeBytes, "");
        }

        WorkInfo info = CURRENT_WORK.get(model.id);
        if (info != null) {
            long received = info.getProgress()
                    .getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L);
            if (info.getState() == WorkInfo.State.RUNNING) {
                return new Status(model, State.DOWNLOADING, received, "");
            }
            if (info.getState() == WorkInfo.State.ENQUEUED
                    || info.getState() == WorkInfo.State.BLOCKED) {
                return new Status(model, State.WAITING, received, "");
            }
            if (info.getState() == WorkInfo.State.FAILED) {
                String error = info.getOutputData().getString(LocalModelDownloadWorker.KEY_ERROR);
                return new Status(model, State.FAILED, received,
                        error == null ? "Model download failed" : error);
            }
        }

        File partialFile = getPartialModelFile(context, model.id, model.fileName);
        if (partialFile.isFile() && partialFile.length() > 0L) {
            return new Status(model, State.PARTIALLY_DOWNLOADED,
                    partialFile.length(), "");
        }
        return new Status(model, State.NOT_DOWNLOADED, 0L, "");
    }

    @Nullable
    public static String downloadSelectedModel(Context context) {
        return downloadModel(context, getSelectedModel(context).id);
    }

    @Nullable
    public static String downloadModel(Context context, String modelId) {
        initialize(context);
        ModelInfo model = getModel(modelId);
        if (!model.downloadable) {
            return model.displayName + " is built into supported devices.";
        }
        if (!isModelSupported(model)) {
            return getModelUnsupportedReason(model) + ".";
        }
        File finalFile = getModelFile(context, model.id, model.fileName);
        File partialFile = getPartialModelFile(context, model.id, model.fileName);
        if (finalFile.isFile() && finalFile.length() == model.sizeBytes) {
            return null;
        }
        if (isDownloadForModelActive(model.id)) {
            return null;
        }
        deleteObsoleteModelFiles(context, model);
        deleteInferenceCacheFiles(context, model);
        if (finalFile.exists()) {
            finalFile.delete();
        }

        long remainingBytes = Math.max(0L, model.sizeBytes - partialFile.length());
        File root = getModelsRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            return "Could not create local model storage.";
        }
        if (root.getUsableSpace() < remainingBytes + STORAGE_BUFFER_BYTES) {
            return "Not enough free space. " + model.displayName + " needs "
                    + formatBytes(remainingBytes + STORAGE_BUFFER_BYTES) + " available.";
        }

        Data inputData = new Data.Builder()
                .putString(LocalModelDownloadWorker.KEY_MODEL_ID, model.id)
                .putString(LocalModelDownloadWorker.KEY_MODEL_NAME, model.displayName)
                .putString(LocalModelDownloadWorker.KEY_MODEL_URL, model.url)
                .putString(LocalModelDownloadWorker.KEY_FILE_NAME, model.fileName)
                .putLong(LocalModelDownloadWorker.KEY_EXPECTED_BYTES, model.sizeBytes)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocalModelDownloadWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(model.id)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                getWorkName(model.id), ExistingWorkPolicy.REPLACE, request);
        return null;
    }

    public static void cancelDownload(Context context, String modelId) {
        initialize(context);
        ModelInfo model = getModel(modelId);
        WorkManager.getInstance(context).cancelUniqueWork(getWorkName(model.id))
                .getResult().addListener(() -> {
            deleteKnownModelFiles(context, model, false);
            CURRENT_WORK.remove(model.id);
            notifyListeners();
        }, ContextCompat.getMainExecutor(context));
    }

    public static void removeSelectedModel(Context context) {
        removeModel(context, getSelectedModel(context).id);
    }

    public static void removeModel(Context context, String modelId) {
        initialize(context);
        ModelInfo model = getModel(modelId);
        if (!model.downloadable) {
            return;
        }
        if (isDownloadForModelActive(model.id)) {
            cancelDownload(context, model.id);
            return;
        }
        deleteKnownModelFiles(context, model, true);
        if (model.id.equals(getSelectedModel(context).id)) {
            clearSelectedModel(context);
            return;
        }
        notifyListeners();
    }

    public static void addStatusListener(Context context, StatusListener listener) {
        initialize(context);
        LISTENERS.add(listener);
        listener.onStatusChanged(getSelectedStatus(context));
    }

    public static void removeStatusListener(StatusListener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean isDownloadActive() {
        for (WorkInfo info : CURRENT_WORK.values()) {
            if (isActive(info)) {
                return true;
            }
        }
        return false;
    }

    public static long getTotalMemoryBytes(Context context) {
        ActivityManager manager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return 0L;
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem;
    }

    public static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000d);
        }
        if (bytes >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000d);
        }
        return bytes + " bytes";
    }

    static File getModelFile(Context context, String modelId, String fileName) {
        return new File(new File(getModelsRoot(context), modelId), fileName);
    }

    static File getPartialModelFile(Context context, String modelId, String fileName) {
        return new File(new File(getModelsRoot(context), modelId), fileName + ".download");
    }

    private static File getModelsRoot(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external == null ? context.getFilesDir() : external;
        return new File(base, MODELS_DIR);
    }

    private static synchronized void initialize(Context context) {
        if (initialized) {
            return;
        }
        initialized = true;
        appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);
        for (ModelInfo model : MODELS) {
            if (model.downloadable) {
                workManager.getWorkInfosForUniqueWorkLiveData(getWorkName(model.id))
                        .observeForever(infos -> onWorkInfosChanged(model.id, infos));
            }
        }
    }

    private static void onWorkInfosChanged(String modelId, List<WorkInfo> infos) {
        WorkInfo selected = null;
        if (infos != null) {
            for (WorkInfo info : infos) {
                if (isActive(info)) {
                    selected = info;
                    break;
                }
                if (selected == null && info.getState() == WorkInfo.State.FAILED) {
                    selected = info;
                }
            }
        }
        if (selected == null) {
            CURRENT_WORK.remove(modelId);
        } else {
            CURRENT_WORK.put(modelId, selected);
        }
        notifyListeners();
    }

    private static boolean isDownloadForModelActive(String modelId) {
        return isActive(CURRENT_WORK.get(modelId));
    }

    private static boolean isActive(@Nullable WorkInfo info) {
        return info != null && (info.getState() == WorkInfo.State.RUNNING
                || info.getState() == WorkInfo.State.ENQUEUED
                || info.getState() == WorkInfo.State.BLOCKED);
    }

    private static String getWorkName(String modelId) {
        return WORK_NAME_PREFIX + modelId;
    }

    private static void deleteKnownModelFiles(Context context, ModelInfo model,
                                              boolean includeFinalFile) {
        File partialFile = getPartialModelFile(context, model.id, model.fileName);
        if (partialFile.exists()) {
            partialFile.delete();
        }
        if (includeFinalFile) {
            File finalFile = getModelFile(context, model.id, model.fileName);
            if (finalFile.exists()) {
                finalFile.delete();
            }
            deleteInferenceCacheFiles(context, model);
        }
        File modelDir = partialFile.getParentFile();
        if (modelDir != null) {
            if (includeFinalFile) {
                File[] files = modelDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            file.delete();
                        }
                    }
                }
            }
            String[] children = modelDir.list();
            if (children != null && children.length == 0) {
                modelDir.delete();
            }
        }
    }

    private static void deleteObsoleteModelFiles(Context context, ModelInfo model) {
        File finalFile = getModelFile(context, model.id, model.fileName);
        File partialFile = getPartialModelFile(context, model.id, model.fileName);
        File modelDir = finalFile.getParentFile();
        if (modelDir == null) {
            return;
        }
        File[] files = modelDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.equals(finalFile) && !file.equals(partialFile)) {
                file.delete();
            }
        }
    }

    private static void deleteInferenceCacheFiles(Context context, ModelInfo model) {
        File[] cacheFiles = context.getCacheDir().listFiles();
        if (cacheFiles == null) {
            return;
        }
        String currentPrefix = model.fileName + ".xnnpack_cache_";
        String legacyPrefix = MODEL_E2B.equals(model.id)
                ? LEGACY_E2B_FILE_NAME + ".xnnpack_cache_"
                : MODEL_QWEN_08B.equals(model.id)
                        ? LEGACY_QWEN_EXACT_FILE_NAME + ".xnnpack_cache_"
                        : "";
        for (File cacheFile : cacheFiles) {
            String name = cacheFile.getName();
            if (cacheFile.isFile()
                    && (name.startsWith(currentPrefix)
                    || (!legacyPrefix.isEmpty() && name.startsWith(legacyPrefix)))) {
                cacheFile.delete();
            }
        }
    }

    private static void notifyListeners() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        Status status = getStatus(context, getSelectedModel(context));
        for (StatusListener listener : LISTENERS) {
            listener.onStatusChanged(status);
        }
    }
}
