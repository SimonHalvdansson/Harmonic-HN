package com.simon.harmonichackernews;

import android.util.Log;
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

    private static final String TAG = "BaseActivity";
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
        int actionType = ev.getAction();

        if (ev.getY() >= (screenHeight - navBarHeight)) {
            if (actionType == MotionEvent.ACTION_UP) {
                // Let the ACTION_UP event through
                return super.dispatchTouchEvent(ev);
            }
            // Block other touch events in the specified area
            return true;
        }
        try {
            return super.dispatchTouchEvent(ev);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message == null
                    || (!message.contains("pointerIndex")
                    && !message.contains("pointer index"))) {
                throw exception;
            }

            // Some Android versions can deliver an inconsistent multi-touch pointer sequence.
            // This is safe to abandon, unlike exceptions thrown from inside a RecyclerView
            // layout/scroll, which must propagate so RecyclerView state is not silently poisoned.
            Log.w(TAG, "Ignoring invalid touch pointer sequence", exception);
            return false;
        }
    }


}
