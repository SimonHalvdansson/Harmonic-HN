package com.simon.harmonichackernews;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.simon.harmonichackernews.databinding.ActivitySettingsDetailBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class DebugActivity extends AppCompatActivity {

    private ActivitySettingsDetailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyStatusBarProtection();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new DebugFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyStatusBarProtection();
    }

    private void applyStatusBarProtection() {
        StatusBarProtectionUtils.setTopProtection(
                binding.settingsStatusBarProtection,
                SettingsUtils.shouldUseTranslucentStatusBar(this),
                StatusBarProtectionUtils.getPaneBackgroundColor(this));
    }
}
