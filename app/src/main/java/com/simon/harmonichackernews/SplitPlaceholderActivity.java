package com.simon.harmonichackernews;

import android.os.Bundle;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.simon.harmonichackernews.utils.ThemeUtils;

public class SplitPlaceholderActivity extends AppCompatActivity {
    private OnBackPressedCallback searchBackCallback;
    private final MainActivity.SearchBackStateListener searchBackStateListener =
            enabled -> syncSearchBackCallbackEnabled();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.setupTheme(this);
        setContentView(R.layout.activity_split_placeholder);
        setupSearchBackCallback();
        MainActivity.addSearchBackStateListener(searchBackStateListener);
    }

    @Override
    protected void onDestroy() {
        MainActivity.removeSearchBackStateListener(searchBackStateListener);
        super.onDestroy();
    }

    private void setupSearchBackCallback() {
        searchBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackCancelled() {
                MainActivity.cancelActiveSearchBackProgress();
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                MainActivity.updateActiveSearchBackProgress(backEvent.getProgress());
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                MainActivity.startActiveSearchBackProgress(backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                if (MainActivity.finishActiveSearchBackProgress()) {
                    syncSearchBackCallbackEnabled();
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                syncSearchBackCallbackEnabled();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, searchBackCallback);
        syncSearchBackCallbackEnabled();
    }

    private void syncSearchBackCallbackEnabled() {
        if (searchBackCallback != null) {
            searchBackCallback.setEnabled(MainActivity.isSearchBackActive());
        }
    }
}
