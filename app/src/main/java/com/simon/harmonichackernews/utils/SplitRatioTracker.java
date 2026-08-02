package com.simon.harmonichackernews.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.core.util.Consumer;
import androidx.window.WindowSdkExtensions;
import androidx.window.embedding.SplitAttributes.SplitType;
import androidx.window.embedding.SplitController;
import androidx.window.embedding.SplitInfo;
import androidx.window.java.embedding.SplitControllerCallbackAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the split the system is showing and the stored split ratio in sync, in both directions:
 * dragging the divider between the two panes is remembered, and a ratio picked in settings is
 * applied to the split which is already showing.
 */
@OptIn(markerClass = androidx.window.core.ExperimentalWindowApi.class)
public class SplitRatioTracker {

    private static final String TAG = "SplitRatioTracker";
    private static final String RATIO_DESCRIPTION_PREFIX = "ratio:";

    /** Window SDK extension version required to update the attributes of an existing split. */
    private static final int UPDATE_SPLIT_ATTRIBUTES_EXTENSION_VERSION = 3;

    private final SplitControllerCallbackAdapter splitCallbackAdapter;
    private final SplitController splitController;
    private final Context context;
    private final List<SplitInfo> activeSplits = new ArrayList<>();
    private final Consumer<List<SplitInfo>> splitInfoConsumer = this::onSplitListUpdate;

    public SplitRatioTracker(Activity activity) {
        this.context = activity.getApplicationContext();
        this.splitController = SplitController.getInstance(activity);
        this.splitCallbackAdapter = new SplitControllerCallbackAdapter(splitController);

        splitCallbackAdapter.addSplitListener(
                activity,
                Runnable::run,
                splitInfoConsumer
        );
    }

    private void onSplitListUpdate(List<SplitInfo> splitInfoList) {
        activeSplits.clear();
        activeSplits.addAll(splitInfoList);

        for (SplitInfo split : splitInfoList) {
            int ratio = parseRatio(split.getSplitAttributes().getSplitType());
            if (ratio != SettingsUtils.SPLIT_PANE_RATIO_UNSET
                    && ratio != SettingsUtils.getSplitPaneRatio(context)) {
                // The divider was dragged. Store where it ended up and rebuild the rules so that
                // the next split, and any re-evaluation of this one, keeps the new ratio.
                SettingsUtils.setSplitPaneRatio(context, ratio);
                FoldableSplitInitializer.applyRules(context);
            }
        }
    }

    /**
     * Applies the stored ratio to the splits which are currently showing. Used when the ratio was
     * changed in settings, since the rules alone only affect splits created afterwards.
     */
    public void applyStoredRatioToActiveSplits() {
        if (activeSplits.isEmpty()
                || WindowSdkExtensions.getInstance().getExtensionVersion()
                        < UPDATE_SPLIT_ATTRIBUTES_EXTENSION_VERSION) {
            return;
        }

        int storedRatio = SettingsUtils.getSplitPaneRatio(context);

        for (SplitInfo split : activeSplits) {
            int ratio = parseRatio(split.getSplitAttributes().getSplitType());
            if (ratio == SettingsUtils.SPLIT_PANE_RATIO_UNSET || ratio == storedRatio) {
                // Not a ratio based split, or already showing the ratio we want
                continue;
            }

            try {
                splitController.updateSplitAttributes(
                        split, FoldableSplitInitializer.createSplitAttributes(context));
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, "Could not update the attributes of a visible split", e);
                return;
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
        activeSplits.clear();
    }
}
