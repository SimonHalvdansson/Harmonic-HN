package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.simon.harmonichackernews.settings.AppearancePreferenceFragment;
import com.simon.harmonichackernews.settings.CommentsPreferenceFragment;
import com.simon.harmonichackernews.settings.DataStoragePreferenceFragment;
import com.simon.harmonichackernews.settings.FiltersTagsPreferenceFragment;
import com.simon.harmonichackernews.settings.SettingsCallback;
import com.simon.harmonichackernews.settings.SettingsHeaderFragment;
import com.simon.harmonichackernews.settings.StoriesPreferenceFragment;
import com.simon.harmonichackernews.settings.WebLinksPreferenceFragment;
import com.simon.harmonichackernews.utils.ThemeUtils;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        SettingsCallback {

    private static final int TWO_PANE_MIN_WIDTH_DP = 600;
    private static final int TWO_PANE_SPACER_DP = 16;
    private static final int TWO_PANE_LIST_WEIGHT = 2;
    private static final int TWO_PANE_DETAIL_WEIGHT = 3;

    private static final String STATE_DETAIL_CLASS = "state_detail_class";
    private static final String STATE_DETAIL_KEY = "state_detail_key";
    private static final String STATE_NEEDS_RESTART = "state_needs_restart";
    private static final String STATE_TWO_PANE = "state_two_pane";

    private static final Map<String, String> FRAGMENT_TO_KEY = new HashMap<>();
    static {
        FRAGMENT_TO_KEY.put(AppearancePreferenceFragment.class.getName(), SettingsHeaderFragment.DEFAULT_KEY);
        FRAGMENT_TO_KEY.put(StoriesPreferenceFragment.class.getName(), "pref_header_stories");
        FRAGMENT_TO_KEY.put(WebLinksPreferenceFragment.class.getName(), "pref_header_web_links");
        FRAGMENT_TO_KEY.put(CommentsPreferenceFragment.class.getName(), "pref_header_comments");
        FRAGMENT_TO_KEY.put(FiltersTagsPreferenceFragment.class.getName(), "pref_header_filters_tags");
        FRAGMENT_TO_KEY.put(DataStoragePreferenceFragment.class.getName(), "pref_header_data_storage");
    }

    private static boolean requestFullRestart = false;
    public final static String EXTRA_REQUEST_RESTART = "EXTRA_REQUEST_RESTART";

    private boolean needsRestart = false;
    private boolean isTwoPane = false;
    private String currentDetailClassName = AppearancePreferenceFragment.class.getName();
    private String currentDetailKey = SettingsHeaderFragment.DEFAULT_KEY;
    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestFullRestart = false;

        ThemeUtils.setupTheme(this, false);

        setContentView(R.layout.activity_settings);

        if (savedInstanceState != null) {
            currentDetailClassName = savedInstanceState.getString(STATE_DETAIL_CLASS,
                    AppearancePreferenceFragment.class.getName());
            currentDetailKey = savedInstanceState.getString(STATE_DETAIL_KEY,
                    SettingsHeaderFragment.DEFAULT_KEY);
            needsRestart = savedInstanceState.getBoolean(STATE_NEEDS_RESTART, false);
            isTwoPane = savedInstanceState.getBoolean(STATE_TWO_PANE, false);
        }

        setupTwoPane();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsHeaderFragment())
                    .commit();

            if (isTwoPane) {
                Fragment detail = getSupportFragmentManager().getFragmentFactory()
                        .instantiate(getClassLoader(), currentDetailClassName);
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings_detail, detail)
                        .commit();
            }
        } else if (isTwoPane) {
            // Restore header selection highlight after process death
            getSupportFragmentManager().executePendingTransactions();
            Fragment header = getSupportFragmentManager().findFragmentById(R.id.settings);
            if (header instanceof SettingsHeaderFragment) {
                ((SettingsHeaderFragment) header).setSelectedKey(currentDetailKey);
            }
        }

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    // Pop sub-screen first, then re-evaluate
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(needsRestart);
                } else if (needsRestart) {
                    handleExit();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_REQUEST_RESTART, false)) {
            needsRestart = true;
        }

        if (needsRestart) {
            backPressedCallback.setEnabled(true);
        }
    }

    private void setupTwoPane() {
        boolean wasTwoPane = isTwoPane;

        WindowMetrics metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(this);
        float density = getResources().getDisplayMetrics().density;
        float widthDp = metrics.getBounds().width() / density;

        View root = findViewById(R.id.settings_linear_layout);
        View settingsPane = findViewById(R.id.settings);
        View detailPane = findViewById(R.id.settings_detail);
        View spacer = findViewById(R.id.settings_pane_spacer);

        if (widthDp >= TWO_PANE_MIN_WIDTH_DP && detailPane != null) {
            isTwoPane = true;

            detailPane.setVisibility(View.VISIBLE);
            detailPane.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, TWO_PANE_DETAIL_WEIGHT));

            if (spacer != null) {
                spacer.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams spacerParams = spacer.getLayoutParams();
                spacerParams.width = Math.round(TWO_PANE_SPACER_DP * density);
                spacer.setLayoutParams(spacerParams);
            }

            settingsPane.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, TWO_PANE_LIST_WEIGHT));

            int padding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
            root.setPadding(padding, 0, padding, 0);

        } else {
            isTwoPane = false;

            if (detailPane != null) {
                detailPane.setVisibility(View.GONE);
            }
            if (spacer != null) {
                spacer.setVisibility(View.GONE);
            }

            settingsPane.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            int padding = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
            root.setPadding(padding, 0, padding, 0);
        }

        // Handle fragment migration on fold/unfold
        Fragment existingFragment = getSupportFragmentManager().findFragmentById(R.id.settings);
        if (existingFragment != null && wasTwoPane != isTwoPane) {
            FragmentManager fm = getSupportFragmentManager();
            if (isTwoPane) {
                // Single-pane -> two-pane: remember what the user was viewing
                Fragment currentSingle = fm.findFragmentById(R.id.settings);
                if (currentSingle != null && !(currentSingle instanceof SettingsHeaderFragment)) {
                    currentDetailClassName = currentSingle.getClass().getName();
                    String key = FRAGMENT_TO_KEY.get(currentDetailClassName);
                    if (key != null) {
                        currentDetailKey = key;
                    }
                }

                fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                Fragment detail = fm.getFragmentFactory()
                        .instantiate(getClassLoader(), currentDetailClassName);
                fm.beginTransaction()
                        .replace(R.id.settings_detail, detail)
                        .commit();

                // Update header selection
                fm.executePendingTransactions();
                Fragment header = fm.findFragmentById(R.id.settings);
                if (header instanceof SettingsHeaderFragment) {
                    ((SettingsHeaderFragment) header).setSelectedKey(currentDetailKey);
                }
            } else {
                // Two-pane -> single-pane: move detail into main pane
                Fragment detailFragment = fm.findFragmentById(R.id.settings_detail);
                if (detailFragment != null) {
                    Fragment detail = fm.getFragmentFactory()
                            .instantiate(getClassLoader(), currentDetailClassName);
                    fm.beginTransaction()
                            .remove(detailFragment)
                            .replace(R.id.settings, detail)
                            .addToBackStack(null)
                            .commit();
                }
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        String fragmentClassName = pref.getFragment();
        if (fragmentClassName == null) {
            return false;
        }

        currentDetailClassName = fragmentClassName;
        currentDetailKey = pref.getKey();

        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), fragmentClassName);
        fragment.setArguments(pref.getExtras());

        if (isTwoPane) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_detail, fragment)
                    .commit();

            Fragment headerFragment = getSupportFragmentManager().findFragmentById(R.id.settings);
            if (headerFragment instanceof SettingsHeaderFragment) {
                ((SettingsHeaderFragment) headerFragment).setSelectedKey(pref.getKey());
            }
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, fragment)
                    .addToBackStack(null)
                    .commit();
        }

        return true;
    }

    @Override
    public void onRequestRestart() {
        needsRestart = true;
        backPressedCallback.setEnabled(true);
    }

    @Override
    public void onRequestFullRestart() {
        needsRestart = true;
        requestFullRestart = true;
        backPressedCallback.setEnabled(true);
    }

    @Override
    public boolean isTwoPane() {
        return isTwoPane;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupTwoPane();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DETAIL_CLASS, currentDetailClassName);
        outState.putString(STATE_DETAIL_KEY, currentDetailKey);
        outState.putBoolean(STATE_NEEDS_RESTART, needsRestart);
        outState.putBoolean(STATE_TWO_PANE, isTwoPane);
    }

    private void handleExit() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (requestFullRestart) {
            Runtime.getRuntime().exit(0);
        }
    }
}
