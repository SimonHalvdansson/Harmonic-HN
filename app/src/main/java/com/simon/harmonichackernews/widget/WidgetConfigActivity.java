package com.simon.harmonichackernews.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class WidgetConfigActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "widget_config";
    private static final String KEY_FEED_TYPE_PREFIX = "feed_type_";
    private static final String KEY_FEED_NAME_PREFIX = "feed_name_";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.setupTheme(this);

        // Set canceled result initially — if user backs out, widget won't be added
        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_widget_config);

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

        RadioGroup feedGroup = findViewById(R.id.widget_config_feed_group);
        Button confirmButton = findViewById(R.id.widget_config_confirm);

        confirmButton.setOnClickListener(v -> {
            String feedUrl;
            String feedName;

            int checkedId = feedGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.widget_config_new) {
                feedUrl = Utils.URL_NEW;
                feedName = "New";
            } else if (checkedId == R.id.widget_config_best) {
                feedUrl = Utils.URL_BEST;
                feedName = "Best";
            } else if (checkedId == R.id.widget_config_ask) {
                feedUrl = Utils.URL_ASK;
                feedName = "Ask HN";
            } else if (checkedId == R.id.widget_config_show) {
                feedUrl = Utils.URL_SHOW;
                feedName = "Show HN";
            } else if (checkedId == R.id.widget_config_jobs) {
                feedUrl = Utils.URL_JOBS;
                feedName = "Jobs";
            } else {
                feedUrl = Utils.URL_TOP;
                feedName = "Top";
            }

            // Save config
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_FEED_TYPE_PREFIX + appWidgetId, feedUrl)
                    .putString(KEY_FEED_NAME_PREFIX + appWidgetId, feedName)
                    .apply();

            // Trigger widget update
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            StoriesWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId);

            // Set result and finish
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    public static String getFeedUrl(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_FEED_TYPE_PREFIX + appWidgetId, Utils.URL_TOP);
    }

    public static String getFeedName(android.content.Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_FEED_NAME_PREFIX + appWidgetId, null);
    }
}
