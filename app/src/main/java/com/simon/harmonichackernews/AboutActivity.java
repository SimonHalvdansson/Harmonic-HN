package com.simon.harmonichackernews;


import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);
        setContentView(R.layout.activity_about);

        ((TextView) findViewById(R.id.about_version)).setText("Version " + BuildConfig.VERSION_NAME);
    }
}
