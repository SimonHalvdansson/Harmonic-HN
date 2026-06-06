package com.simon.harmonichackernews;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.DialogWindowUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class DialogHostActivity extends AppCompatActivity implements WelcomeDialogFragment.DismissListener {

    private static final String EXTRA_DIALOG_TYPE = "dialog_type";
    private static final String EXTRA_SHOW_VERSION_TITLE = "show_version_title";
    private static final String DIALOG_TYPE_WELCOME = "welcome";
    private static final String DIALOG_TYPE_CHANGELOG = "changelog";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, true, false);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (savedInstanceState != null) {
            return;
        }

        String dialogType = getIntent().getStringExtra(EXTRA_DIALOG_TYPE);
        if (DIALOG_TYPE_WELCOME.equals(dialogType)) {
            WelcomeDialogFragment.show(
                    getSupportFragmentManager(),
                    getIntent().getBooleanExtra(EXTRA_SHOW_VERSION_TITLE, false));
        } else if (DIALOG_TYPE_CHANGELOG.equals(dialogType)) {
            showChangelogDialog();
        } else {
            finishWithoutAnimation();
        }
    }

    @Override
    public void onWelcomeDialogDismissed() {
        finishWithoutAnimation();
    }

    private void showChangelogDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Changelog.getFormatted(this))
                .setNeutralButton("GitHub", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://github.com/SimonHalvdansson/Harmonic-HN"));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Done", null)
                .create();
        dialog.setOnDismissListener(dialogInterface -> finishWithoutAnimation());
        dialog.show();
        DialogWindowUtils.applyMaxWidth(dialog);
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

    public static void showWelcome(Context context, boolean showVersionTitle) {
        Intent intent = new Intent(context, DialogHostActivity.class);
        intent.putExtra(EXTRA_DIALOG_TYPE, DIALOG_TYPE_WELCOME);
        intent.putExtra(EXTRA_SHOW_VERSION_TITLE, showVersionTitle);
        context.startActivity(intent);
    }

    public static void showChangelog(Context context) {
        Intent intent = new Intent(context, DialogHostActivity.class);
        intent.putExtra(EXTRA_DIALOG_TYPE, DIALOG_TYPE_CHANGELOG);
        context.startActivity(intent);
    }
}
