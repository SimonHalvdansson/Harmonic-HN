package com.simon.harmonichackernews;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.ActivityAboutBinding;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);
        ActivityAboutBinding binding = ActivityAboutBinding.inflate(getLayoutInflater());
        final View root = binding.getRoot();
        setContentView(root);

        // Draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        final View container = binding.aboutContainer;

        // Remember original paddings
        final int padLeft  = container.getPaddingLeft();
        final int padTop   = container.getPaddingTop();
        final int padRight = container.getPaddingRight();
        final int padBot   = container.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars   = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime    = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            container.setPadding(
                    padLeft + Math.max(bars.left, cutout.left),
                    padTop   + bars.top,
                    padRight   + Math.max(bars.right, cutout.right),
                    padBot   + Math.max(bars.bottom, ime.bottom)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        String versionText = "Version " + BuildConfig.VERSION_NAME;
        if (BuildConfig.DEBUG) {
            versionText += String.format(" (%s)", BuildConfig.BUILD_TYPE);
        }
        binding.aboutVersion.setText(versionText);
    }

    public void openGithub(View v) {
        String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    public void openChangelog(View v) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Html.fromHtml(Changelog.getHTML()))
                .setNegativeButton("Done", null).create();

        dialog.show();
    }

    public void openPrivacy(View v) {
        Utils.launchCustomTab(this, "https://simonhalvdansson.github.io/harmonic_privacy.html");
    }
}
