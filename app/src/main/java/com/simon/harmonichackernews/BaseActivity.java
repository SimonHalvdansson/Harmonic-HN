package com.simon.harmonichackernews;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.simon.harmonichackernews.utils.ViewUtils;

public class BaseActivity extends AppCompatActivity {

    private int navBarHeight = 0;

    @Override
    protected void onStart() {
        super.onStart();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                navBarHeight = insets.bottom;

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(findViewById(android.R.id.content));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int screenHeight = getWindow().getDecorView().getHeight();
        if (ev.getY() >= (screenHeight - navBarHeight)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }


}
