package com.simon.harmonichackernews;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import androidx.fragment.app.FragmentTransaction;

import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.databinding.ActivityCommentsBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.SplitChangeHandler;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class CommentsActivity extends BaseActivity implements CommentsFragment.BottomSheetFragmentCallback {
    public static String PREVENT_BACK = "PREVENT_BACK";
    private boolean disableSwipeAtComments;
    private SwipeBackLayout swipeBackLayout;
    private SplitChangeHandler splitChangeHandler;
    private boolean swipeBack = false;
    private CommentsFragment commentsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        swipeBack = !SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext());

        ThemeUtils.setupTheme(this, swipeBack);
        ActivityCommentsBinding binding = ActivityCommentsBinding.inflate(getLayoutInflater());
        final View root = binding.getRoot();

        /*this is a long story. On CommentsActivity, the default theme is dependent on the android version.
            For 24-29, we set SwipeBack as the default theme and do nothing more
            For 30-33, we set AppTheme as the default and perhaps switch to SwipeBack if the user wants to, but in that case we indicate translucency manually
            which makes it so that SwipeBack can peek to MainActivity
            For 34+, translucent = true is set automatically in the onResume (after a delay) which makes the peek work

            On a somewhat related note, transparent status bar messes the intended MainActivity -> CommentsActivity transition up sadly...
        * */
        if (swipeBack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setTranslucent(true);
        }
        setContentView(root);

        commentsFragment = new CommentsFragment();
        commentsFragment.setArguments(getIntent().getExtras());
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);
        transaction.replace(R.id.comment_fragment_container_view, commentsFragment);
        transaction.commit();

        swipeBackLayout = binding.swipeBackLayout;
        this.splitChangeHandler = new SplitChangeHandler(this, swipeBackLayout);

        swipeBackLayout.setSwipeBackListener(new SwipeBackLayout.OnSwipeBackListener() {
            @Override
            public void onViewPositionChanged(View mView, float swipeBackFraction, float swipeBackFactor) {
                mView.invalidate();
            }

            @Override
            public void onViewSwipeFinished(View mView, boolean isEnd) {
                if (isEnd) {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });

        disableSwipeAtComments = !swipeBack;
    }

    @Override
    public void onSwitchView(boolean isAtWebView) {
        if (splitChangeHandler.isWithinSplit() && getIntent() != null) {
            swipeBackLayout.setActive(!getIntent().getBooleanExtra(PREVENT_BACK, false));
            return;
        }

        if (isAtWebView) {
            swipeBackLayout.setActive(false);
        } else {
            swipeBackLayout.setActive(!disableSwipeAtComments);
        }
    }

    // We only need to do the translucent setting on Android 14 and above as its purpose is to
    // make the predictive back animation nice (when we peek back from a deeper activity,
    // CommentsActivity cannot be transparent). The theme already sets the activity to translucent
    // so when we animate in we are transparent which is important!
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && swipeBack) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    setTranslucent(true);
                }
            }, 400);
        }
    }

    // If we set translucency to false immediately onPause we can trigger animations by accident so
    // we delay things a little
    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && swipeBack) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    setTranslucent(false);
                }
            }, 300);

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splitChangeHandler.teardown();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String volumeNavigationMode = SettingsUtils.getCommentsVolumeNavigationMode(getApplicationContext());
        if (!SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED.equals(volumeNavigationMode)) {
            boolean topLevelOnly = SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL.equals(volumeNavigationMode);
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (commentsFragment != null) {
                    commentsFragment.navigateToNextComment(topLevelOnly);
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (commentsFragment != null) {
                    commentsFragment.navigateToPreviousComment(topLevelOnly);
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
