package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.insets.GradientProtection;
import androidx.core.view.insets.ProtectionLayout;

import java.util.Collections;

public final class StatusBarProtectionUtils {

    private StatusBarProtectionUtils() {
    }

    @ColorInt
    public static int getPaneBackgroundColor(@NonNull Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(context, typedValue.resourceId);
        }
        return typedValue.data;
    }

    public static void setTopProtection(@Nullable ProtectionLayout layout,
                                        boolean enabled,
                                        @ColorInt int color) {
        if (layout == null) {
            return;
        }

        if (!enabled) {
            layout.setProtections(Collections.emptyList());
            return;
        }

        layout.setProtections(Collections.singletonList(
                new GradientProtection(WindowInsetsCompat.Side.TOP, color)));
    }
}
