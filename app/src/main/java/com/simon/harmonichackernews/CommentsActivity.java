package com.simon.harmonichackernews;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.SplitChangeHandler;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class CommentsActivity extends AppCompatActivity implements CommentsFragment.BottomSheetFragmentCallback {
    public static String PREVENT_BACK = "PREVENT_BACK";

    private boolean disableSwipeAtComments;
    private SwipeBackLayout swipeBackLayout;
    private SplitChangeHandler splitChangeHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, true);

        setContentView(R.layout.activity_comments);

        CommentsFragment fragment = new CommentsFragment();
        fragment.setArguments(getIntent().getExtras());
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.comment_fragment_container_view, fragment);
        transaction.commit();

        swipeBackLayout = findViewById(R.id.swipeBackLayout);
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

        disableSwipeAtComments = SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext());
    }

    @Override
    public void onSwitchView(boolean isAtWebView) {
        if (splitChangeHandler.isWithinSplit() && getIntent() != null) {
            swipeBackLayout.setActive(!getIntent().getBooleanExtra(PREVENT_BACK, false));
            return;
        }

        if (!isAtWebView) {
            swipeBackLayout.setActive(!disableSwipeAtComments);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    setTranslucent(true);
                }
            }, 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTranslucent(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splitChangeHandler.teardown();
    }
}