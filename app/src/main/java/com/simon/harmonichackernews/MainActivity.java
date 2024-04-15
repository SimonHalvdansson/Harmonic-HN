package com.simon.harmonichackernews;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.Changelog;
import com.simon.harmonichackernews.utils.FoldableSplitInitializer;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import java.util.ArrayList;

public class MainActivity extends BaseActivity implements StoriesFragment.StoryClickListener {

    public static ArrayList<CommentsScrollProgress> commentsScrollProgresses = new ArrayList<>();

    int lastPosition = 0;
    public OnBackPressedCallback backPressedCallback;

    public int bottom = 0;

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

        if (Utils.justUpdated(this) && SettingsUtils.shouldShowChangelog(this)) {
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

        if (Utils.isTablet(getResources())) {
            CommentsFragment fragment = new CommentsFragment();
            fragment.setArguments(bundle);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_comments_container, fragment);
            transaction.commit();
        } else {
            Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);

            if (!SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext())) {
                overridePendingTransition(R.anim.activity_in_animation, R.anim.hold);
            }
        }
    }

    private void updateFragmentLayout() {
        if (Utils.isTablet(getResources()) && findViewById(R.id.main_fragments_container) != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getInteger(R.integer.stories_pane_weight));
            findViewById(R.id.main_fragment_stories_container).setLayoutParams(params);

            int extraPadding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
            findViewById(R.id.main_fragments_container).setPadding(extraPadding, 0, 0, 0);
        }
    }

    private void showUpdateDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Changelog")
                .setMessage(Html.fromHtml(Changelog.getHTML()))
                .setNeutralButton("GitHub", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Done", null).create();

        dialog.show();
    }
}