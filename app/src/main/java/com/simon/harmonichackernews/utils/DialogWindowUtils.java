package com.simon.harmonichackernews.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

public class DialogWindowUtils {

    public static final int DEFAULT_MAX_WIDTH_DP = 500;

    private DialogWindowUtils() {
    }

    public static void applyMaxWidth(Dialog dialog) {
        applyMaxWidth(dialog, DEFAULT_MAX_WIDTH_DP);
    }

    public static void applyMaxWidth(Dialog dialog, int maxWidthDp) {
        if (dialog == null) {
            return;
        }

        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        Context context = dialog.getContext();
        int maxWidthPx = Math.round(maxWidthDp * context.getResources().getDisplayMetrics().density);
        int horizontalMarginPx = Math.round(48 * context.getResources().getDisplayMetrics().density);
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels - horizontalMarginPx;
        int targetWidth = Math.min(maxWidthPx, Math.max(0, availableWidth));
        if (targetWidth <= 0) {
            return;
        }

        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
