package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreloadWebviewDialogBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class PreloadWebViewDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_preload_webview_dialog";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        PreloadWebviewDialogBinding binding = PreloadWebviewDialogBinding.inflate(getLayoutInflater());
        RadioGroup modeGroup = binding.preloadWebviewModeGroup;
        Slider batterySlider = binding.preloadWebviewBatterySlider;
        TextView batteryLabel = binding.preloadWebviewBatteryLabel;
        View batteryControls = binding.preloadWebviewBatteryControls;

        String currentMode = SettingsUtils.shouldPreloadWebView(requireContext());
        modeGroup.check(getRadioButtonIdForMode(currentMode));

        int currentMinimumBattery = roundToBatteryStep(SettingsUtils.getPreloadWebViewMinimumBattery(requireContext()));
        batterySlider.setLabelFormatter(value -> formatBatteryLabel(Math.round(value)));
        batterySlider.setValue(currentMinimumBattery);
        updateBatteryLabel(batteryLabel, currentMinimumBattery);
        setBatteryControlsEnabled(batteryControls, !SettingsUtils.PRELOAD_WEBVIEW_NEVER.equals(currentMode));

        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
                setBatteryControlsEnabled(batteryControls, checkedId != R.id.preload_webview_never));
        batterySlider.addOnChangeListener((slider, value, fromUser) ->
                updateBatteryLabel(batteryLabel, Math.round(value)));

        builder.setTitle("Preload websites");
        builder.setView(binding.getRoot());
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit();
            editor.putString(SettingsUtils.PREF_PRELOAD_WEBVIEW,
                    getModeForRadioButtonId(modeGroup.getCheckedRadioButtonId()));
            editor.putInt(SettingsUtils.PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY,
                    roundToBatteryStep(Math.round(batterySlider.getValue())));
            editor.apply();
        });
        return builder.create();
    }

    public static void show(FragmentManager fm) {
        new PreloadWebViewDialogFragment().show(fm, TAG);
    }

    private int getRadioButtonIdForMode(String mode) {
        if (SettingsUtils.PRELOAD_WEBVIEW_ALWAYS.equals(mode)) {
            return R.id.preload_webview_always;
        }
        if (SettingsUtils.PRELOAD_WEBVIEW_ONLY_WIFI.equals(mode)) {
            return R.id.preload_webview_only_wifi;
        }
        return R.id.preload_webview_never;
    }

    private String getModeForRadioButtonId(int checkedId) {
        if (checkedId == R.id.preload_webview_always) {
            return SettingsUtils.PRELOAD_WEBVIEW_ALWAYS;
        }
        if (checkedId == R.id.preload_webview_only_wifi) {
            return SettingsUtils.PRELOAD_WEBVIEW_ONLY_WIFI;
        }
        return SettingsUtils.PRELOAD_WEBVIEW_NEVER;
    }

    private void updateBatteryLabel(TextView label, int minimumBattery) {
        label.setText("Minimum battery: " + formatBatteryLabel(minimumBattery));
    }

    private String formatBatteryLabel(int minimumBattery) {
        if (minimumBattery <= SettingsUtils.DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY) {
            return "Any";
        }
        return minimumBattery + "%";
    }

    private int roundToBatteryStep(int value) {
        return Math.max(0, Math.min(100, Math.round(value / 5f) * 5));
    }

    private void setBatteryControlsEnabled(View rootView, boolean enabled) {
        rootView.setAlpha(enabled ? 1f : 0.5f);
        setViewAndChildrenEnabled(rootView, enabled);
    }

    private void setViewAndChildrenEnabled(View rootView, boolean enabled) {
        rootView.setEnabled(enabled);
        if (rootView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                setViewAndChildrenEnabled(group.getChildAt(i), enabled);
            }
        }
    }
}
