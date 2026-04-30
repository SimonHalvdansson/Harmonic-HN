package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.settings.SettingsFragmentFactory;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class SettingsDetailActivity extends AppCompatActivity implements SettingsCallback {

    public static final String EXTRA_FRAGMENT_CLASS = "EXTRA_FRAGMENT_CLASS";
    public static final String EXTRA_DETAIL_KEY = "EXTRA_DETAIL_KEY";
    public static final String EXTRA_FRAGMENT_ARGUMENTS = "EXTRA_FRAGMENT_ARGUMENTS";

    private static final String STATE_FRAGMENT_CLASS = "state_fragment_class";
    private static final String STATE_DETAIL_KEY = "state_detail_key";
    private static final String STATE_NEEDS_RESTART = "state_needs_restart";
    private static final String STATE_NEEDS_FULL_RESTART = "state_needs_full_restart";

    private String fragmentClassName;
    private String detailKey;
    private boolean needsRestart;
    private boolean needsFullRestart;
    private boolean returningToSettings;

    public static Intent createIntent(
            @NonNull AppCompatActivity activity,
            @NonNull String fragmentClassName,
            @Nullable String detailKey,
            @Nullable Bundle fragmentArguments) {
        Intent intent = new Intent(activity, SettingsDetailActivity.class);
        intent.putExtra(EXTRA_FRAGMENT_CLASS, fragmentClassName);
        intent.putExtra(EXTRA_DETAIL_KEY, detailKey);
        if (fragmentArguments != null) {
            intent.putExtra(EXTRA_FRAGMENT_ARGUMENTS, fragmentArguments);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);
        setContentView(R.layout.activity_settings_detail);
        applySinglePanePadding();

        if (savedInstanceState != null) {
            fragmentClassName = savedInstanceState.getString(STATE_FRAGMENT_CLASS);
            detailKey = savedInstanceState.getString(STATE_DETAIL_KEY);
            needsRestart = savedInstanceState.getBoolean(STATE_NEEDS_RESTART, false);
            needsFullRestart = savedInstanceState.getBoolean(STATE_NEEDS_FULL_RESTART, false);
        } else {
            Intent intent = getIntent();
            fragmentClassName = intent.getStringExtra(EXTRA_FRAGMENT_CLASS);
            detailKey = intent.getStringExtra(EXTRA_DETAIL_KEY);
        }

        if (shouldUseSettingsTwoPane()) {
            returnToSettingsActivity();
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Fragment fragment = SettingsFragmentFactory.create(
                    getSupportFragmentManager(), getClassLoader(), fragmentClassName);
            if (fragment == null) {
                finish();
                return;
            }

            Bundle arguments = getIntent().getBundleExtra(EXTRA_FRAGMENT_ARGUMENTS);
            if (arguments != null) {
                fragment.setArguments(arguments);
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.settings, fragment)
                    .commit();
        }
    }

    @Override
    public void finish() {
        if (!returningToSettings && (needsRestart || needsFullRestart)) {
            returnToSettingsActivity();
        }
        super.finish();
    }

    @Override
    public void onRequestRestart() {
        needsRestart = true;
    }

    @Override
    public void onRequestFullRestart() {
        needsRestart = true;
        needsFullRestart = true;
    }

    public void restartSettingsActivity() {
        needsRestart = true;
        restartSettingsStack();
        finish();
    }

    @Override
    public boolean isTwoPane() {
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySinglePanePadding();
        if (shouldUseSettingsTwoPane()) {
            returnToSettingsActivity();
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FRAGMENT_CLASS, fragmentClassName);
        outState.putString(STATE_DETAIL_KEY, detailKey);
        outState.putBoolean(STATE_NEEDS_RESTART, needsRestart);
        outState.putBoolean(STATE_NEEDS_FULL_RESTART, needsFullRestart);
    }

    private void applySinglePanePadding() {
        View root = findViewById(R.id.settings_linear_layout);
        int padding = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
        root.setPadding(padding, 0, padding, 0);
    }

    private boolean shouldUseSettingsTwoPane() {
        WindowMetrics metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(this);
        float density = getResources().getDisplayMetrics().density;
        float widthDp = metrics.getBounds().width() / density;
        return widthDp >= SettingsActivity.TWO_PANE_MIN_WIDTH_DP;
    }

    private void returnToSettingsActivity() {
        returningToSettings = true;
        Intent intent = new Intent(this, SettingsActivity.class);
        if (needsRestart || needsFullRestart) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        intent.putExtra(SettingsActivity.EXTRA_DETAIL_CLASS, fragmentClassName);
        intent.putExtra(SettingsActivity.EXTRA_DETAIL_KEY, detailKey);
        if (needsRestart || needsFullRestart) {
            intent.putExtra(SettingsActivity.EXTRA_REQUEST_RESTART, true);
        }
        if (needsFullRestart) {
            intent.putExtra(SettingsActivity.EXTRA_REQUEST_FULL_RESTART, true);
        }
        startActivity(intent);
    }

    private void restartSettingsStack() {
        returningToSettings = true;

        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        settingsIntent.putExtra(SettingsActivity.EXTRA_DETAIL_CLASS, fragmentClassName);
        settingsIntent.putExtra(SettingsActivity.EXTRA_DETAIL_KEY, detailKey);
        settingsIntent.putExtra(SettingsActivity.EXTRA_REQUEST_RESTART, true);
        settingsIntent.putExtra(SettingsActivity.EXTRA_RELAUNCH_DETAIL, true);
        if (needsFullRestart) {
            settingsIntent.putExtra(SettingsActivity.EXTRA_REQUEST_FULL_RESTART, true);
        }
        startActivity(settingsIntent);
    }
}
