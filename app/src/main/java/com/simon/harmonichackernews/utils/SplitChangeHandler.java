package com.simon.harmonichackernews.utils;

import android.app.Activity;

import androidx.annotation.OptIn;
import androidx.core.util.Consumer;
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
    private final Runnable splitChangedListener;
    private final Consumer<List<SplitInfo>> splitInfoConsumer = this::onSplitListUpdate;
    private boolean isWithinSplit = false;

    public SplitChangeHandler(Activity activity, SwipeBackLayout swipeBackLayout) {
        this(activity, swipeBackLayout, null);
    }

    public SplitChangeHandler(Activity activity, SwipeBackLayout swipeBackLayout, Runnable splitChangedListener) {
        this.splitCallbackAdapter = new SplitControllerCallbackAdapter(SplitController.getInstance(activity));
        this.layout = swipeBackLayout;
        this.splitChangedListener = splitChangedListener;
        // Note: I (Simon) removed this line as swipeBack needs a transparent background in
        // order to display the activity behind it
        // swipeBackLayout.setBackgroundColor(ContextCompat.getColor(activity, ThemeUtils.getBackgroundColorResource(activity)));

        splitCallbackAdapter.addSplitListener(
                activity,
                Runnable::run,
                splitInfoConsumer
        );
    }

    private void onSplitListUpdate(List<SplitInfo> splitInfoList) {
        boolean wasWithinSplit = isWithinSplit;
        isWithinSplit = false;
        for (SplitInfo split : splitInfoList) {
            if (!split.getSplitAttributes().getSplitType().equals(SplitType.SPLIT_TYPE_EXPAND)) {
                isWithinSplit = true;
                break;
            }
        }

        layout.setMaskAlpha(isWithinSplit ? 0 : 125);
        if (wasWithinSplit != isWithinSplit && splitChangedListener != null) {
            splitChangedListener.run();
        }
    }

    public boolean isWithinSplit() {
        return isWithinSplit;
    }

    public void teardown() {
        splitCallbackAdapter.removeSplitListener(splitInfoConsumer);
    }
}
