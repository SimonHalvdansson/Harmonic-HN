package com.simon.harmonichackernews.utils;

import android.view.View;
import android.view.ViewGroup;

public final class StoryMetaPlacementUtils {
    private static final int COMPACT_TOP_MARGIN_DP = 2;

    private StoryMetaPlacementUtils() {
    }

    public static boolean placeForSummary(
            View title,
            View summary,
            View metaContainer,
            boolean showSummary) {
        if (title == null
                || summary == null
                || metaContainer == null
                || !(title.getParent() instanceof ViewGroup)
                || !(summary.getParent() instanceof ViewGroup)
                || !(metaContainer.getParent() instanceof ViewGroup)
                || !(metaContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return false;
        }

        ViewGroup titleParent = (ViewGroup) title.getParent();
        ViewGroup summaryParent = (ViewGroup) summary.getParent();
        ViewGroup currentParent = (ViewGroup) metaContainer.getParent();
        ViewGroup targetParent = showSummary ? summaryParent : titleParent;
        ViewGroup.MarginLayoutParams metaParams =
                (ViewGroup.MarginLayoutParams) metaContainer.getLayoutParams();

        int targetStartMargin = 0;
        int targetTopMargin =
                Utils.pxFromDpInt(metaContainer.getResources(), COMPACT_TOP_MARGIN_DP);
        int targetEndMargin = 0;
        if (showSummary
                && summary.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams summaryParams =
                    (ViewGroup.MarginLayoutParams) summary.getLayoutParams();
            targetStartMargin = summaryParams.getMarginStart();
            targetTopMargin = summaryParams.topMargin;
            targetEndMargin = summaryParams.getMarginEnd();
        }

        boolean placementChanged = currentParent != targetParent;
        boolean marginsChanged = metaParams.getMarginStart() != targetStartMargin
                || metaParams.topMargin != targetTopMargin
                || metaParams.getMarginEnd() != targetEndMargin;
        if (!placementChanged && !marginsChanged) {
            return false;
        }

        metaParams.setMarginStart(targetStartMargin);
        metaParams.topMargin = targetTopMargin;
        metaParams.setMarginEnd(targetEndMargin);
        if (placementChanged) {
            currentParent.removeView(metaContainer);
            int anchorIndex = targetParent.indexOfChild(showSummary ? summary : title);
            targetParent.addView(
                    metaContainer,
                    Math.min(anchorIndex + 1, targetParent.getChildCount()),
                    metaParams);
        } else {
            metaContainer.setLayoutParams(metaParams);
        }
        return true;
    }
}
