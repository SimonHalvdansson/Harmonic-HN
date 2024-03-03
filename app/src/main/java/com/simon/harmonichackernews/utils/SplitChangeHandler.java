package com.simon.harmonichackernews.utils;

import android.app.Activity;

import androidx.annotation.OptIn;
import androidx.window.embedding.SplitAttributes.SplitType;
import androidx.window.embedding.SplitController;
import androidx.window.embedding.SplitInfo;
import androidx.window.java.embedding.SplitControllerCallbackAdapter;

import com.gw.swipeback.SwipeBackLayout;

import java.util.List;

@OptIn(markerClass = androidx.window.core.ExperimentalWindowApi.class)
public class SplitChangeHandler {
    private final SplitControllerCallbackAdapter splitCallbackAdapter;
    private final SwipeBackLayout layout;
    private boolean isWithinSplit = false;

    public SplitChangeHandler(Activity activity, SwipeBackLayout swipeBackLayout) {
        this.splitCallbackAdapter = new SplitControllerCallbackAdapter(SplitController.getInstance(activity));
        this.layout = swipeBackLayout;
        // Note: I (Simon) removed this line as swipeBack needs a transparent background in
        // order to display the activity behind it
        // swipeBackLayout.setBackgroundColor(ContextCompat.getColor(activity, ThemeUtils.getBackgroundColorResource(activity)));

        splitCallbackAdapter.addSplitListener(
                activity,
                Runnable::run,
                this::onSplitListUpdate
        );
    }

    private void onSplitListUpdate(List<SplitInfo> splitInfoList) {
        for (SplitInfo split : splitInfoList) {
            if (!split.getSplitAttributes().getSplitType().equals(SplitType.SPLIT_TYPE_EXPAND)) {
                isWithinSplit = true;
                break;
            }
            isWithinSplit = false;
        }

        layout.setMaskAlpha(isWithinSplit ? 0 : 125);
    }

    public boolean isWithinSplit() {
        return isWithinSplit;
    }

    public void teardown() {
        splitCallbackAdapter.removeSplitListener(this::onSplitListUpdate);
    }
}
