package com.simon.harmonichackernews;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.BuildCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;

import android.content.res.Configuration;
import android.hardware.camera2.CameraExtensionSession;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.utils.SplitChangeHandler;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class CommentsActivity extends AppCompatActivity implements CommentsFragment.BottomSheetFragmentCallback {
    public static String PREVENT_BACK = "PREVENT_BACK";

    private boolean disableSwipeAtWeb;
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

        FragmentContainerView fragmentContainerView = findViewById(R.id.comment_fragment_container_view);

        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainerView, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                fragmentContainerView.setPadding(0, Utils.shouldUseTransparentStatusBar(getApplicationContext()) ? 0 :insets.top, 0, 0);

                return windowInsets;
            }
        });

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

        disableSwipeAtWeb = Utils.shouldDisableWebviewSwipeBack(getApplicationContext());
        disableSwipeAtComments = Utils.shouldDisableCommentsSwipeBack(getApplicationContext());

        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(0, R.anim.activity_out_animation);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }


    @Override
    public void onSwitchView(boolean isAtWebView) {
        if (splitChangeHandler.isWithinSplit() && getIntent() != null) {
            swipeBackLayout.setActive(!getIntent().getBooleanExtra(PREVENT_BACK, false));
            return;
        }

        if (isAtWebView) {
            swipeBackLayout.setActive(!disableSwipeAtWeb);
        } else {
            swipeBackLayout.setActive(!disableSwipeAtComments);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splitChangeHandler.teardown();
    }
}