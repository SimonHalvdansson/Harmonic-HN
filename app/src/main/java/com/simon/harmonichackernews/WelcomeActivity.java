package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this);

        setContentView(R.layout.activity_welcome);

        ImageView favicon = findViewById(R.id.story_meta_favicon);
        TextView index = findViewById(R.id.story_index);
        TextView meta = findViewById(R.id.story_meta);
        Button materialDaynightButton = findViewById(R.id.welcome_button_material_daynight);
        Button materialDarkButton = findViewById(R.id.welcome_button_material_dark);
        Button materialLightButton = findViewById(R.id.welcome_button_material_light);
        Button darkButton = findViewById(R.id.welcome_button_dark);
        Button grayButton = findViewById(R.id.welcome_button_gray);
        Button blackButton = findViewById(R.id.welcome_button_black);
        Button lightButton = findViewById(R.id.welcome_button_light);
        Button whiteButton = findViewById(R.id.welcome_button_white);

        favicon.setImageResource(R.drawable.quanta);

        MaterialSwitch thumbnailSwitch = findViewById(R.id.welcome_switch_thumbnails);
        MaterialSwitch pointsSwitch = findViewById(R.id.welcome_switch_points);
        MaterialSwitch indexSwitch = findViewById(R.id.welcome_switch_index);

        thumbnailSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                favicon.setVisibility(b ? View.VISIBLE : View.GONE);
                setBooleanSetting(compoundButton.getContext(), "pref_thumbnails", b);
            }
        });

        indexSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                index.setVisibility(b ? View.VISIBLE : View.GONE);
                setBooleanSetting(compoundButton.getContext(), "pref_show_index", b);
            }
        });

        pointsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                meta.setText((b ? "53 points • " : "") + "quantamagazine.org • 2 hrs");
                setBooleanSetting(compoundButton.getContext(), "pref_show_points", b);
            }
        });

        View.OnClickListener buttonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSetting(view.getContext(), "pref_theme", (String) view.getTag());
                restartActivity();
            }
        };

        materialDaynightButton.setOnClickListener(buttonClickListener);
        materialDarkButton.setOnClickListener(buttonClickListener);
        materialLightButton.setOnClickListener(buttonClickListener);
        darkButton.setOnClickListener(buttonClickListener);
        grayButton.setOnClickListener(buttonClickListener);
        blackButton.setOnClickListener(buttonClickListener);
        lightButton.setOnClickListener(buttonClickListener);
        whiteButton.setOnClickListener(buttonClickListener);
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
