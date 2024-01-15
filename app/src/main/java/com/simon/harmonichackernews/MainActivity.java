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
                .setMessage(Html.fromHtml( "<b>Version 2.0.2:</b><br>" +
                        "- Fixed crash when opening PDF in browser<br>" +
                        "- More material 3 styling<br>" +
                        "- Added option to hide comment count (thanks Loïc Carr)<br>" +
                        "- Added loading indicator to main screen for initial load<br>" +
                        "- Added option to swap comment tap/long press behavior<br>" +
                        "- Improved code comment formatting (thanks @naitgacem)<br>" +
                        "- Added option to disable startup changelog (this thing)<br>" +
                        "- Fixed collapsed comment cutoff issue<br>" +
                        "- Fixed Material dialog discard button placement (thanks Carsten Hagemann)<br>" +
                        "<br>" +
                        "<b>Version 2.0.1:</b><br>" +
                        "- Fixed crash when WebView runs out of memory<br>" +
                        "- Fixed crash when encountering some network errors<br>" +
                        "<br>" +
                        "<b>Version 2.0:</b><br>" +
                        "- Material (auto) is new default theme, I recommend you try this out<br>" +
                        "- Added ability to search comments<br>" +
                        "- Updated splash screen to use correct dark mode color<br>" +
                        "- Added GitHub repo link preview<br>" +
                        "- Added Wikipedia link preview<br>" +
                        "- Updated arXiv link preview<br>" +
                        "- Added Algolia API error information<br>" +
                        "- Experimental fix for EncryptedSharedPreferences crash<br>" +
                        "- Added optional Twitter/X to Nitter redirect<br>" +
                        "- Design tweaks to story page<br>" +
                        "- Changed default favicon provider to Google<br>" +
                        "- Removed WebView back, only device back from now on<br>" +
                        "- Updated preferences to be more Material 3<br>" +
                        "- Fixed issue with transparent status bar<br>" +
                        "- Added experimental ability to post stories<br>" +
                        "- Fixed several Android 14 predictive back issue<br>" +
                        "- Added Material You comment depth indicators<br>" +
                        "- Restored correct search settings (thanks Leslie Cheng)<br>" +
                        "- Improved comment show/hide animation<br>" +
                        "- Expanded font selection to affect more components<br>" +
                        "- Prompt to update stale stories<br>" +
                        "- Changed default favicon provider to Google<br>" +
                        "- Fixed a possible WebView crash<br>" +
                        "- Fixed 2 comments crashes<br>" +
                        "<br>" +
                        "<b>Version 1.11.1:</b><br>" +
                        "- Fixed submissions failed to load (thanks Leslie Cheng)<br>" +
                        "- New dialog-less animation when posting comments<br>" +
                        "- Added arXiv abstract resolver<br>" +
                        "- Fixed rare Android 14 predictive back flashing<br>" +
                        "- Added touch rejection on navigation bar<br>" +
                        "- Added long comment scrim in submissions<br>" +
                        "<br>" +
                        "<b>Version 1.11:</b><br>" +
                        "- Added alternative comment sortings (thanks John Rapp Farnes)<br>" +
                        "- Fixed user bio bottom padding<br>" +
                        "- Added option to choose favicon provider<br>" +
                        "- Improved old Android navigation scrim (thanks AppearamidGuy)<br>" +
                        "- Optimized AdBlock adlist loading (thanks AppearamidGuy)<br>" +
                        "- New comment opening animation when not using SwipeBack<br>" +
                        "- Improved logging for login errors<br>" +
                        "- Fixed scroll restoration bug<br>" +
                        "- Improved collapsed comment scroll performance (thanks Yigit Boyar)<br>" +
                        "- Made story text selectable (thanks AppearamidGuy)<br>" +
                        "- Fixed tablet comment scroll bug<br>" +
                        "- Fixed rare long click crash<br>" +
                        "<br>" +
                        "<b>Version 1.10:</b><br>" +
                        "- Added option to select start page (thanks Thomas Dalgetty)<br>" +
                        "- Comment scroll progress is now saved <br>" +
                        "- Posts can be marked as read/unread by long pressing and interacting with a small menu<br>" +
                        "- Added option to hide clicked posts<br>" +
                        "- Updated predictive back to work on all screens<br>" +
                        "- New scroll behavior when full collapsing comments<br>" +
                        "- Added option to collapse all top-level comments by default<br>" +
                        "- Fixed two comments related crashes<br>" +
                        "<br>" +
                        "<b>Version 1.9.6:</b><br>" +
                        "- Increased stability of WebView (thanks AppearamidGuy and flofriday)<br>" +
                        "- Added option to use device back button for WebView<br>" +
                        "- Fixed a tablet bottom sheet layout bug<br>" +
                        "- Minor search bar behavior changes<br>" +
                        "<br>" +
                        "<b>Version 1.9.5:</b><br>" +
                        "- Fixed comment navigation buttons always visible<br>" +
                        "<br>" +
                        "<b>Version 1.9.4:</b><br>" +
                        "- A white background now fades in behind the WebView after 2 seconds to better handle transparent websites<br>" +
                        "- Fixed scroll issue with comment navigation buttons<br>" +
                        "- Improved internal link handling<br>" +
                        "- Better parsing of post titles<br>" +
                        "- Animated keyboard when composing comments<br>" +
                        "<br>" +
                        "<b>Version 1.9.3:</b><br>" +
                        "- Sharing now only shares URL<br>" +
                        "- Experimental fix to WebView memory leak<br>" +
                        "- Fixed crash when loading submissions<br>" +
                        "- Fixed PDF viewer crash<br>" +
                        "- Added partial caching of post titles<br>" +
                        "- New animation for \"Tap to update\" button<br>" +
                        "- Increased maximum number of loaded submissions<br>" +
                        "- Fixed error while opening submissions<br>" +
                        "- Reworked padding system throughout the app (thanks AppearamidGuy)<br>" +
                        "- Added support for combined text/link posts (thanks Jonas Wunderlich)<br>" +
                        "- Better WebView intent handling<br>" +
                        "<br>" +
                        "<b>Version 1.9.2:</b><br>" +
                        "- Fixed white theme" +
                        "<br><br>" +
                        "<b>Version 1.9.1:</b><br>" +
                        "- Fixed compact header padding<br>" +
                        "- Fixed dark WebView, the API was significantly changed<br>" +
                        "- Added a setting to disable comment animations<br>" +
                        "- Added initial support for Android 14's predictive back gesture<br>" +
                        "- Updated bottom sheet animation slightly<br>" +
                        "- Updated dependencies<br>" +
                        "<br>" +
                        "<b>Version 1.9:</b><br>" +
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
                .setNegativeButton("Done", null).create();

        dialog.show();
    }
}