package com.simon.harmonichackernews.network;

import android.content.Context;

/** Fail-closed local summarization boundary for the FOSS distribution. */
final class LocalSummaryManager {
    private static final String UNAVAILABLE_MESSAGE =
            "Local AI is not included in the FOSS distribution.";

    private LocalSummaryManager() {
    }

    static boolean canAttemptLocalSummarization() {
        return false;
    }

    static void checkLocalSummaryAvailability(
            Context context, SummaryManager.LocalSummaryAvailabilityCallback callback) {
        SummaryManager.postLocalAvailability(callback, false, false, UNAVAILABLE_MESSAGE);
    }

    static void summarizeArticle(
            Context context, String articleUrl, SummaryManager.SummaryCallback callback) {
        SummaryManager.postFailure(callback, UNAVAILABLE_MESSAGE);
    }

    static void summarizeText(
            Context context, String text, SummaryManager.SummaryCallback callback) {
        SummaryManager.postFailure(callback, UNAVAILABLE_MESSAGE);
    }

    static boolean isLocalSummaryReady(Context context) {
        return false;
    }

    static boolean isLocalSummaryConfigurationKnown(Context context) {
        return true;
    }
}
