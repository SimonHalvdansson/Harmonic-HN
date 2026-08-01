package com.simon.harmonichackernews.utils;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.OptIn;
import androidx.core.util.Consumer;
import androidx.window.embedding.SplitAttributes.SplitType;
import androidx.window.embedding.SplitController;
import androidx.window.embedding.SplitInfo;
import androidx.window.java.embedding.SplitControllerCallbackAdapter;

import java.util.List;

/**
 * Keeps the stored split ratio in sync with the split the system is actually showing, so that
 * dragging the divider between the two panes is remembered.
 */
@OptIn(markerClass = androidx.window.core.ExperimentalWindowApi.class)
public class SplitRatioTracker {

    private static final String RATIO_DESCRIPTION_PREFIX = "ratio:";

    private final SplitControllerCallbackAdapter splitCallbackAdapter;
    private final Context context;
    private final Consumer<List<SplitInfo>> splitInfoConsumer = this::onSplitListUpdate;

    public SplitRatioTracker(Activity activity) {
        this.context = activity.getApplicationContext();
        this.splitCallbackAdapter = new SplitControllerCallbackAdapter(SplitController.getInstance(activity));

        splitCallbackAdapter.addSplitListener(
                activity,
                Runnable::run,
                splitInfoConsumer
        );
    }

    private void onSplitListUpdate(List<SplitInfo> splitInfoList) {
        for (SplitInfo split : splitInfoList) {
            int ratio = parseRatio(split.getSplitAttributes().getSplitType());
            if (ratio != SettingsUtils.SPLIT_PANE_RATIO_UNSET
                    && ratio != SettingsUtils.getSplitPaneRatio(context)) {
                SettingsUtils.setSplitPaneRatio(context, ratio);
            }
        }
    }

    /**
     * The ratio of a split is only exposed through the description of its split type, which is
     * formatted as "ratio:0.35". Anything else, such as an expanded container, is ignored.
     */
    private static int parseRatio(SplitType splitType) {
        String description = String.valueOf(splitType);
        if (!description.startsWith(RATIO_DESCRIPTION_PREFIX)) {
            return SettingsUtils.SPLIT_PANE_RATIO_UNSET;
        }

        try {
            float ratio = Float.parseFloat(description.substring(RATIO_DESCRIPTION_PREFIX.length()));
            int percentage = Math.round(ratio * 100);
            if (percentage < SettingsUtils.MIN_SPLIT_PANE_RATIO
                    || percentage > SettingsUtils.MAX_SPLIT_PANE_RATIO) {
                return SettingsUtils.SPLIT_PANE_RATIO_UNSET;
            }
            return percentage;
        } catch (NumberFormatException e) {
            return SettingsUtils.SPLIT_PANE_RATIO_UNSET;
        }
    }

    public void teardown() {
        splitCallbackAdapter.removeSplitListener(splitInfoConsumer);
    }
}
