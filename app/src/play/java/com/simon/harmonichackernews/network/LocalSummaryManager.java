package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.summarization.Summarization;
import com.google.mlkit.genai.summarization.SummarizationRequest;
import com.google.mlkit.genai.summarization.SummarizationResult;
import com.google.mlkit.genai.summarization.Summarizer;
import com.google.mlkit.genai.summarization.SummarizerOptions;
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager;
import com.simon.harmonichackernews.summary.local.LocalModelInference;
import com.simon.harmonichackernews.summary.local.LocalModelManager;

import java.util.concurrent.ExecutionException;

/** Play distribution implementation for Gemini Nano and downloadable local models. */
final class LocalSummaryManager {
    private static final String TAG = "LocalSummaryManager";
    private static final int LOCAL_SUMMARY_MIN_CHARS = 400;
    private static final int LOCAL_SUMMARY_MAX_WORDS = 3000;
    private static volatile int cachedLocalFeatureStatus = Integer.MIN_VALUE;

    private LocalSummaryManager() {
    }

    static boolean canAttemptLocalSummarization() {
        return true;
    }

    static void checkLocalSummaryAvailability(
            Context context, SummaryManager.LocalSummaryAvailabilityCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            Summarizer summarizer = null;
            try {
                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
                int featureStatus = summarizer.checkFeatureStatus().get();
                cachedLocalFeatureStatus = featureStatus;
                postResolvedLocalAvailability(callback, featureStatus);
            } catch (ExecutionException exception) {
                Log.w(TAG, "Gemini Nano availability check failed", exception.getCause());
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
                postDownloadableModelAvailability(callback);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                SummaryManager.postLocalAvailability(callback, false, false,
                        "Gemini Nano availability check was interrupted");
            } catch (Exception exception) {
                Log.w(TAG, "Gemini Nano availability check failed", exception);
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
                postDownloadableModelAvailability(callback);
            } finally {
                if (summarizer != null) {
                    summarizer.close();
                }
            }
        }).start();
    }

    static void summarizeArticle(
            Context context, String articleUrl, SummaryManager.SummaryCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                summarizePreparedTextLocally(appContext,
                        prepareLocalSummaryInput(SummaryManager.extractMainContent(articleUrl)),
                        callback);
            } catch (ExecutionException exception) {
                SummaryManager.postFailure(callback, "Local summarization failed: "
                        + SummaryManager.getThrowableMessage(exception.getCause()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                SummaryManager.postFailure(callback, "Local summarization was interrupted");
            } catch (Exception exception) {
                SummaryManager.postFailure(callback, "Local summarization failed: "
                        + SummaryManager.getThrowableMessage(exception));
            }
        }).start();
    }

    static void summarizeText(
            Context context, String text, SummaryManager.SummaryCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                summarizePreparedTextLocally(
                        appContext, prepareLocalSummaryInput(text), callback);
            } catch (ExecutionException exception) {
                SummaryManager.postFailure(callback, "Local summarization failed: "
                        + SummaryManager.getThrowableMessage(exception.getCause()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                SummaryManager.postFailure(callback, "Local summarization was interrupted");
            } catch (Exception exception) {
                SummaryManager.postFailure(callback, "Local summarization failed: "
                        + SummaryManager.getThrowableMessage(exception));
            }
        }).start();
    }

    static boolean isLocalSummaryReady(Context context) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(context);
        if (LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            return isLocalFeatureUsable(cachedLocalFeatureStatus);
        }
        return LocalModelManager.isModelSupported(selected)
                && LocalModelManager.isSelectedModelDownloaded(context)
                && LocalAiRuntimeManager.isRuntimeInstalled(context, selected.runtime);
    }

    static boolean isLocalSummaryConfigurationKnown(Context context) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(context);
        return !LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)
                || cachedLocalFeatureStatus != Integer.MIN_VALUE;
    }

    private static SummarizerOptions createLocalSummarizerOptions(Context context) {
        return SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build();
    }

    private static void summarizePreparedTextLocally(
            Context appContext, String content, SummaryManager.SummaryCallback callback)
            throws ExecutionException, InterruptedException {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(appContext);
        if (!LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            summarizeWithDownloadedLocalModel(appContext, content, callback);
            return;
        }
        SummaryManager.postDebugInfo(callback, "Gemini Nano · load —");

        Summarizer availabilityChecker = null;
        try {
            availabilityChecker =
                    Summarization.getClient(createLocalSummarizerOptions(appContext));
            int featureStatus = availabilityChecker.checkFeatureStatus().get();
            cachedLocalFeatureStatus = featureStatus;
            if (isLocalFeatureUsable(featureStatus)) {
                summarizePreparedLocalText(appContext, content, callback);
                return;
            }
        } catch (ExecutionException exception) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
            throw exception;
        } catch (RuntimeException exception) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE;
            throw exception;
        } finally {
            if (availabilityChecker != null) {
                availabilityChecker.close();
            }
        }

        SummaryManager.postFailure(callback,
                "Gemini Nano is unavailable. Select another local model in AI summarization settings.");
    }

    private static void summarizeWithDownloadedLocalModel(
            Context appContext, String content, SummaryManager.SummaryCallback callback) {
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(appContext);
        if (!LocalModelManager.isModelSupported(selected)) {
            SummaryManager.postFailure(
                    callback, LocalModelManager.getModelUnsupportedReason(selected));
            return;
        }
        if (!LocalModelManager.isSelectedModelDownloaded(appContext)) {
            SummaryManager.postFailure(
                    callback, "Download the selected local model before using it");
            return;
        }
        if (!LocalAiRuntimeManager.isRuntimeInstalled(appContext, selected.runtime)) {
            SummaryManager.postFailure(
                    callback, "Install the selected model runtime before using it");
            return;
        }
        if (content.length() < LOCAL_SUMMARY_MIN_CHARS) {
            SummaryManager.postFailure(
                    callback, "Article is too short for local summarization");
            return;
        }

        try {
            SummaryManager.postSuccess(callback, LocalModelInference.summarize(
                    appContext,
                    content,
                    summary -> SummaryManager.postProgress(callback, summary),
                    loadMillis -> SummaryManager.postDebugInfo(callback,
                            SummaryManager.formatLoadInfo(
                                    selected.displayName, loadMillis))));
        } catch (Exception exception) {
            SummaryManager.postFailure(callback, "Local model failed: "
                    + SummaryManager.getThrowableMessage(exception));
        }
    }

    private static void summarizePreparedLocalText(
            Context appContext, String content, SummaryManager.SummaryCallback callback)
            throws ExecutionException, InterruptedException {
        Summarizer summarizer = null;
        boolean summarizerReleased = false;
        try {
            if (content.length() < LOCAL_SUMMARY_MIN_CHARS) {
                SummaryManager.postFailure(
                        callback, "Article is too short for Gemini Nano summarization");
                return;
            }

            summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext));
            int featureStatus = summarizer.checkFeatureStatus().get();
            if (featureStatus == FeatureStatus.UNAVAILABLE) {
                SummaryManager.postFailure(
                        callback, "Gemini Nano summarization is not available on this device");
            } else if (featureStatus == FeatureStatus.DOWNLOADABLE
                    || featureStatus == FeatureStatus.DOWNLOADING) {
                downloadLocalFeatureAndSummarize(content, summarizer, callback);
                summarizerReleased = true;
            } else if (featureStatus == FeatureStatus.AVAILABLE) {
                runLocalInference(content, summarizer, callback);
                summarizerReleased = true;
            } else {
                SummaryManager.postFailure(
                        callback, "Gemini Nano summarization is not available on this device");
            }
        } finally {
            if (summarizer != null && !summarizerReleased) {
                summarizer.close();
            }
        }
    }

    private static void downloadLocalFeatureAndSummarize(
            String text, Summarizer summarizer, SummaryManager.SummaryCallback callback) {
        summarizer.downloadFeature(new DownloadCallback() {
            @Override
            public void onDownloadCompleted() {
                runLocalInference(text, summarizer, callback);
            }

            @Override
            public void onDownloadFailed(GenAiException exception) {
                summarizer.close();
                SummaryManager.postFailure(callback, "Gemini Nano download failed: "
                        + SummaryManager.getThrowableMessage(exception));
            }

            @Override
            public void onDownloadProgress(long totalBytesDownloaded) {
            }

            @Override
            public void onDownloadStarted(long bytesToDownload) {
            }
        });
    }

    private static void runLocalInference(
            String text, Summarizer summarizer, SummaryManager.SummaryCallback callback) {
        new Thread(() -> {
            try {
                SummarizationRequest request = SummarizationRequest.builder(text).build();
                SummarizationResult result = summarizer.runInference(request).get();
                SummaryManager.postSuccess(callback, result.getSummary());
            } catch (ExecutionException exception) {
                SummaryManager.postFailure(callback, "Gemini Nano failed: "
                        + SummaryManager.getThrowableMessage(exception.getCause()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                SummaryManager.postFailure(
                        callback, "Gemini Nano summarization was interrupted");
            } catch (Exception exception) {
                SummaryManager.postFailure(callback, "Gemini Nano failed: "
                        + SummaryManager.getThrowableMessage(exception));
            } finally {
                summarizer.close();
            }
        }).start();
    }

    private static String prepareLocalSummaryInput(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return normalized;
        }

        String[] words = normalized.split("\\s+");
        if (words.length <= LOCAL_SUMMARY_MAX_WORDS) {
            return normalized;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < LOCAL_SUMMARY_MAX_WORDS; i++) {
            if (i > 0) {
                truncated.append(' ');
            }
            truncated.append(words[i]);
        }
        return truncated.toString();
    }

    private static boolean isLocalFeatureUsable(int featureStatus) {
        return featureStatus == FeatureStatus.AVAILABLE
                || featureStatus == FeatureStatus.DOWNLOADABLE
                || featureStatus == FeatureStatus.DOWNLOADING;
    }

    private static String getLocalFeatureStatusMessage(int featureStatus) {
        if (featureStatus == FeatureStatus.AVAILABLE) {
            return "";
        } else if (featureStatus == FeatureStatus.DOWNLOADABLE) {
            return "Gemini Nano will download before the first local summary";
        } else if (featureStatus == FeatureStatus.DOWNLOADING) {
            return "Gemini Nano is downloading";
        }
        return "Gemini Nano not available on this device";
    }

    private static void postResolvedLocalAvailability(
            SummaryManager.LocalSummaryAvailabilityCallback callback, int featureStatus) {
        if (isLocalFeatureUsable(featureStatus)) {
            SummaryManager.postLocalAvailability(
                    callback, true, false, getLocalFeatureStatusMessage(featureStatus));
        } else {
            postDownloadableModelAvailability(callback);
        }
    }

    private static void postDownloadableModelAvailability(
            SummaryManager.LocalSummaryAvailabilityCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SummaryManager.postLocalAvailability(
                    callback, true, true, "Gemini Nano isn't available on this device");
        } else {
            SummaryManager.postLocalAvailability(callback, false, false,
                    "Gemini Nano is unavailable; downloadable models require Android 12 or newer");
        }
    }
}
