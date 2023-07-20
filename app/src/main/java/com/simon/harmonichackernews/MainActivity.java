package com.simon.harmonichackernews;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;

import android.net.Uri;
import android.os.Bundle;

import android.text.Html;
import android.view.View;
import android.view.ViewGroup;

import android.widget.LinearLayout;
import android.window.OnBackInvokedCallback;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.FoldableSplitInitializer;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class MainActivity extends AppCompatActivity implements StoriesFragment.StoryClickListener {

    int lastPosition = 0;
    public OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isFirstAppStart(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        }

        ThemeUtils.setupTheme(this);

        setContentView(R.layout.activity_main);

        updateFragmentLayout();

        if (Utils.justUpdated(this)) {
            showUpdateDialog();
        }

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                final StoriesFragment fragment = (StoriesFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_stories_container);

                if (fragment != null) {
                    fragment.exitSearch();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        backPressedCallback.setEnabled(false);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFragmentLayout();
    }

    @Override
    public void openStory(Story story, int pos, boolean showWebsite) {
        Bundle bundle = story.toBundle();

        bundle.putInt(CommentsFragment.EXTRA_FORWARD, pos - lastPosition);
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        if (FoldableSplitInitializer.isSplitSupported(this)) {
            bundle.putBoolean(CommentsActivity.PREVENT_BACK, true);
        }

        lastPosition = pos;

        if (Utils.isTablet(this)) {
            CommentsFragment fragment = new CommentsFragment();
            fragment.setArguments(bundle);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_comments_container, fragment);
            transaction.commit();
        } else {
            Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);

            overridePendingTransition(R.anim.activity_in_animation, 0);
        }
    }

    private void updateFragmentLayout() {
        if (Utils.isTablet(getApplicationContext()) && findViewById(R.id.main_fragments_container) != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getInteger(R.integer.stories_pane_weight));
            findViewById(R.id.main_fragment_stories_container).setLayoutParams(params);

            int extraPadding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
            if (Utils.shouldUseTransparentStatusBar(this)) {
                findViewById(R.id.main_fragments_container).setPadding(extraPadding, 0, 0, 0);
            } else {
                findViewById(R.id.main_fragments_container).setPadding(extraPadding, Utils.getStatusBarHeight(getResources()), 0, 0);
            }
        }
    }

    private void showUpdateDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Html.fromHtml("<b>Version 1.9:</b><br>" +
                        "In case you missed it, Harmonic is now open source! There have already been a bunch of nice pull requests (see below), feel free to check out the repo! <br><br>" +
                        "- Added experimental support for foldables (thanks Travis Gayle!)<br>" +
                        "- Added option for transparent status bar (thanks fireph!)<br>" +
                        "- Added Android 13 dynamic theme icon (thanks Ramit Suri)<br>" +
                        "- New automatic Material You theme (thanks kyleatmakrs)<br>" +
                        "- Fixed a submissions crash (thanks Timothy J. Frisch!)<br>" +
                        "- Layout fixes (thanks AppearamidGuy)<br>" +
                        "- Reworked bottom sheet behavior, this should fix squished icons for Samsung phones among others hopefully and also fix Android 12L tablet navigation bar issues (more fixes for this will come later)<br><br>" +
                        "Plus some small minor things :)"))
                .setNeutralButton("GitHub", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Dismiss", null).create();

        dialog.show();
    }
}