package com.simon.harmonichackernews;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class SplitPlaceholderActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ThemeUtils.setupTheme(this);
    setContentView(R.layout.activity_split_placeholder);
  }
}