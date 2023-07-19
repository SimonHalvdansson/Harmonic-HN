package com.simon.harmonichackernews;


import android.os.Bundle;
import android.view.View;

import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, true);

        setContentView(R.layout.activity_about);

        SwipeBackLayout swipeBackLayout = findViewById(R.id.swipeBackLayout);

        swipeBackLayout.setSwipeBackListener(new SwipeBackLayout.OnSwipeBackListener() {
            @Override
            public void onViewPositionChanged(View mView, float swipeBackFraction, float swipeBackFactor) {
                mView.invalidate();
            }

            @Override
            public void onViewSwipeFinished(View mView, boolean isEnd) {
                if (isEnd) {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });

        ScrollView scrollView = findViewById(R.id.about_scroll_view);
        scrollView.setBackgroundResource(ThemeUtils.getBackgroundColorResource(this));
        scrollView.setPadding(0, Utils.getStatusBarHeight(getResources()), 0, Utils.getNavigationBarHeight(getResources()));

        ((TextView) findViewById(R.id.about_version)).setText("Version " + BuildConfig.VERSION_NAME);
    }
}
