package com.simon.harmonichackernews;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);
        setContentView(R.layout.activity_about);

        String versionText = "Version " + BuildConfig.VERSION_NAME;
        if (BuildConfig.DEBUG) {
            versionText += String.format(" (%s)", BuildConfig.BUILD_TYPE);
        }
        ((TextView) findViewById(R.id.about_version)).setText(versionText);
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
