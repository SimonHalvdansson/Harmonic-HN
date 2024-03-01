package com.simon.harmonichackernews;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.simon.harmonichackernews.utils.ThemeUtils;

public class SplitPlaceholderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.setupTheme(this);
        setContentView(R.layout.activity_split_placeholder);
    }
}