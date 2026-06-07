package com.simon.harmonichackernews.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.ActivityWidgetConfigBinding;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class WidgetConfigActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "widget_config";
    private static final String KEY_FEED_TYPE_PREFIX = "feed_type_";
    private static final String KEY_FEED_NAME_PREFIX = "feed_name_";
    private static final String KEY_STORY_COUNT_PREFIX = "story_count_";
    private static final int STORY_COUNT_SMALL = 8;
    private static final int STORY_COUNT_MEDIUM = 16;
    private static final int STORY_COUNT_LARGE = 24;
    private static final int DEFAULT_STORY_COUNT = STORY_COUNT_MEDIUM;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ActivityWidgetConfigBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.setupTheme(this);

        // Set canceled result initially — if user backs out, widget won't be added
        setResult(RESULT_CANCELED);

        binding = ActivityWidgetConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyResponsivePadding();

        // Get widget ID from intent
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        MaterialButtonToggleGroup storyCountGroup = binding.widgetConfigStoryCountGroup;
        storyCountGroup.check(getStoryCountButtonId(getStoryCount(this, appWidgetId)));

        binding.widgetConfigConfirm.setOnClickListener(v -> {
            String feedUrl;
            String feedName;

            int checkedId = binding.widgetConfigFeedGroup.getCheckedRadioButtonId();
            if (checkedId == binding.widgetConfigNew.getId()) {
                feedUrl = Utils.URL_NEW;
                feedName = "New stories";
            } else if (checkedId == binding.widgetConfigBest.getId()) {
                feedUrl = Utils.URL_BEST;
                feedName = "Best stories";
            } else if (checkedId == binding.widgetConfigAsk.getId()) {
                feedUrl = Utils.URL_ASK;
                feedName = "Ask HN";
            } else if (checkedId == binding.widgetConfigShow.getId()) {
                feedUrl = Utils.URL_SHOW;
                feedName = "Show HN";
            } else if (checkedId == binding.widgetConfigJobs.getId()) {
                feedUrl = Utils.URL_JOBS;
                feedName = "Jobs";
            } else {
                feedUrl = Utils.URL_TOP;
                feedName = "Top stories";
            }

            int storyCount = getSelectedStoryCount(storyCountGroup.getCheckedButtonId());

            // Save config
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_FEED_TYPE_PREFIX + appWidgetId, feedUrl)
                    .putString(KEY_FEED_NAME_PREFIX + appWidgetId, feedName)
                    .putInt(KEY_STORY_COUNT_PREFIX + appWidgetId, storyCount)
                    .apply();

            // Set result and finish
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsivePadding();
    }

    private void applyResponsivePadding() {
        int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
        setHorizontalPadding(binding.widgetConfigContentContainer, sideMargin);
        setHorizontalPadding(binding.widgetConfigButtonContainer, sideMargin);
    }

    private void setHorizontalPadding(View view, int sidePadding) {
        view.setPadding(sidePadding, view.getPaddingTop(), sidePadding, view.getPaddingBottom());
    }

    public static String getFeedUrl(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_FEED_TYPE_PREFIX + appWidgetId, Utils.URL_TOP);
    }

    public static String getFeedName(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_FEED_NAME_PREFIX + appWidgetId, null);
    }

    static int getStoryCount(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int storyCount = prefs.getInt(KEY_STORY_COUNT_PREFIX + appWidgetId, DEFAULT_STORY_COUNT);
        if (storyCount == STORY_COUNT_SMALL || storyCount == STORY_COUNT_MEDIUM || storyCount == STORY_COUNT_LARGE) {
            return storyCount;
        }
        if (storyCount == 10) {
            return STORY_COUNT_SMALL;
        }
        if (storyCount == 20) {
            return STORY_COUNT_MEDIUM;
        }
        if (storyCount == 30 || storyCount == 40) {
            return STORY_COUNT_LARGE;
        }
        return DEFAULT_STORY_COUNT;
    }

    static int getFetchStoryCount(android.content.Context context, int appWidgetId) {
        int visibleStoryCount = getStoryCount(context, appWidgetId);
        if (visibleStoryCount == STORY_COUNT_SMALL) {
            return 10;
        }
        if (visibleStoryCount == STORY_COUNT_LARGE) {
            return 28;
        }
        return 20;
    }

    private static int getSelectedStoryCount(int checkedButtonId) {
        if (checkedButtonId == R.id.widget_config_count_10) {
            return STORY_COUNT_SMALL;
        } else if (checkedButtonId == R.id.widget_config_count_30) {
            return STORY_COUNT_LARGE;
        }
        return DEFAULT_STORY_COUNT;
    }

    private static int getStoryCountButtonId(int storyCount) {
        if (storyCount == STORY_COUNT_SMALL) {
            return R.id.widget_config_count_10;
        } else if (storyCount == STORY_COUNT_LARGE) {
            return R.id.widget_config_count_30;
        }
        return R.id.widget_config_count_20;
    }

    static void clearPreferences(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_FEED_TYPE_PREFIX + appWidgetId)
                .remove(KEY_FEED_NAME_PREFIX + appWidgetId)
                .remove(KEY_STORY_COUNT_PREFIX + appWidgetId)
                .apply();
    }
}
