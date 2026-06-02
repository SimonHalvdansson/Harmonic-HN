package com.simon.harmonichackernews.settings;

import android.animation.ArgbEvaluator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadDepthIndicatorsDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_thread_depth_indicators_dialog";

    private final List<View> previewIndicators = new ArrayList<>();
    private final List<Integer> previewIndicatorColors = new ArrayList<>();
    private final List<ValueAnimator> previewAnimators = new ArrayList<>();
    private final Map<String, MaterialButton> modeButtons = new HashMap<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.thread_depth_indicators_dialog, null);
        builder.setTitle("Thread depth indicators");
        builder.setView(rootView);

        LinearLayout previewContainer = rootView.findViewById(R.id.thread_depth_preview);
        buildPreviewRows(rootView, previewContainer);

        modeButtons.put(CommentDepthIndicatorUtils.MODE_THEME_DEFAULT, rootView.findViewById(R.id.thread_depth_theme_default));
        modeButtons.put(CommentDepthIndicatorUtils.MODE_MATERIAL_YOU, rootView.findViewById(R.id.thread_depth_material_you));
        modeButtons.put(CommentDepthIndicatorUtils.MODE_COLORS, rootView.findViewById(R.id.thread_depth_colors));
        modeButtons.put(CommentDepthIndicatorUtils.MODE_MONOCHROME, rootView.findViewById(R.id.thread_depth_monochrome));
        modeButtons.put(CommentDepthIndicatorUtils.MODE_NONE, rootView.findViewById(R.id.thread_depth_none));

        String currentMode = SettingsUtils.getPreferredCommentDepthIndicatorMode(requireContext());
        for (Map.Entry<String, MaterialButton> entry : modeButtons.entrySet()) {
            String mode = entry.getKey();
            MaterialButton button = entry.getValue();
            button.setCheckable(true);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setOnClickListener(view -> {
                SettingsUtils.setPreferredCommentDepthIndicatorMode(requireContext(), mode);
                updateButtons(mode);
                updatePreview(mode, true);
            });
        }

        updateButtons(currentMode);
        updatePreview(currentMode, false);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> updateButtons(SettingsUtils.getPreferredCommentDepthIndicatorMode(requireContext())));
        return dialog;
    }

    public static void show(FragmentManager fm) {
        new ThreadDepthIndicatorsDialogFragment().show(fm, TAG);
    }

    @Override
    public void onDestroyView() {
        for (ValueAnimator animator : new ArrayList<>(previewAnimators)) {
            animator.cancel();
        }
        previewAnimators.clear();
        previewIndicators.clear();
        previewIndicatorColors.clear();
        modeButtons.clear();
        super.onDestroyView();
    }

    private void buildPreviewRows(View rootView, LinearLayout previewContainer) {
        Context context = rootView.getContext();
        Typeface typeface = ResourcesCompat.getFont(context, R.font.product_sans);
        int textColor = MaterialColors.getColor(rootView, R.attr.storyColorNormal);
        int rowHeight = Utils.pxFromDpInt(getResources(), 40);
        int indicatorWidth = Utils.pxFromDpInt(getResources(), 3);
        int indicatorMarginEnd = Utils.pxFromDpInt(getResources(), 10);
        int depthSpacing = Utils.pxFromDpInt(getResources(), 12);

        for (int i = 0; i < CommentDepthIndicatorUtils.COMMENT_DEPTH_COLOR_COUNT; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(depthSpacing * i, 0, 0, 0);
            previewContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    rowHeight));

            View indicator = new View(context);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(indicatorWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            indicatorParams.setMarginEnd(indicatorMarginEnd);
            row.addView(indicator, indicatorParams);
            previewIndicators.add(indicator);

            TextView textView = new TextView(context);
            textView.setText("Comment " + (i + 1));
            textView.setTextColor(textColor);
            textView.setTextSize(15);
            textView.setTypeface(typeface);
            row.addView(textView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void updateButtons(String mode) {
        String safeMode = CommentDepthIndicatorUtils.sanitizeMode(mode);
        for (Map.Entry<String, MaterialButton> entry : modeButtons.entrySet()) {
            boolean selected = entry.getKey().equals(safeMode);
            MaterialButton button = entry.getValue();
            button.setChecked(selected);
            if (selected) {
                button.setIconResource(R.drawable.ic_action_check);
            } else {
                button.setIcon(null);
            }
        }
    }

    private void updatePreview(String mode, boolean animate) {
        Context context = requireContext();
        String theme = ThemeUtils.getPreferredTheme(context);
        boolean showIndicators = CommentDepthIndicatorUtils.shouldShowIndicators(mode);
        for (int i = 0; i < previewIndicators.size(); i++) {
            View indicator = previewIndicators.get(i);
            indicator.setVisibility(showIndicators ? View.VISIBLE : View.GONE);
            if (!showIndicators) {
                continue;
            }

            int color = ContextCompat.getColor(context,
                    CommentDepthIndicatorUtils.getColorResource(context, mode, theme, i));
            if (i >= previewIndicatorColors.size()) {
                previewIndicatorColors.add(color);
                indicator.setBackgroundColor(color);
                continue;
            }

            int oldColor = previewIndicatorColors.get(i);
            previewIndicatorColors.set(i, color);
            if (!animate || oldColor == color) {
                indicator.setBackgroundColor(color);
                continue;
            }

            ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), oldColor, color);
            animator.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> indicator.setBackgroundColor((int) animation.getAnimatedValue()));
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previewAnimators.remove(animation);
                }
            });
            previewAnimators.add(animator);
            animator.start();
        }
    }
}
