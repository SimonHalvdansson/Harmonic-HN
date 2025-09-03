package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.databinding.ActivityWelcomeBinding;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class WelcomeActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this);
        ActivityWelcomeBinding binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        final View root = binding.getRoot();
        setContentView(root);

        ImageView favicon = binding.storyListItem.storyMetaFavicon;
        favicon.setImageResource(R.drawable.quanta);

        binding.welcomeSwitchThumbnails.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            favicon.setVisibility(b ? View.VISIBLE : View.GONE);
            setBooleanSetting(compoundButton.getContext(), "pref_thumbnails", b);
        });

        binding.welcomeSwitchIndex.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            binding.storyListItem.storyIndex.setVisibility(b ? View.VISIBLE : View.GONE);
            setBooleanSetting(compoundButton.getContext(), "pref_show_index", b);
        });

        binding.welcomeSwitchPoints.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            binding.storyListItem.storyMeta.setText((b ? "53 points • " : "") + "quantamagazine.org • 2 hrs");
            setBooleanSetting(compoundButton.getContext(), "pref_show_points", b);
        });

        View.OnClickListener buttonClickListener = (View view) -> {
            setSetting(view.getContext(), "pref_theme", (String) view.getTag());
            restartActivity();
        };

        binding.welcomeButtonMaterialDaynight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonMaterialDark.setOnClickListener(buttonClickListener);
        binding.welcomeButtonMaterialLight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonDark.setOnClickListener(buttonClickListener);
        binding.welcomeButtonGray.setOnClickListener(buttonClickListener);
        binding.welcomeButtonBlack.setOnClickListener(buttonClickListener);
        binding.welcomeButtonLight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonWhite.setOnClickListener(buttonClickListener);
    }

    @SuppressLint("ApplySharedPref")
    private void setSetting(Context ctx, String key, String newTheme) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putString(key, newTheme).commit();
    }

    @SuppressLint("ApplySharedPref")
    private void setBooleanSetting(Context ctx, String key, boolean newVal) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putBoolean(key, newVal).commit();
    }

    private void restartActivity() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }

    public void done(View view) {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
