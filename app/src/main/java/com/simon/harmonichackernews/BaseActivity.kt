package com.simon.harmonichackernews

import android.R
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.simon.harmonichackernews.utils.ViewUtils

open class BaseActivity : ComponentActivity() {
    private var navBarHeight = 0

    override fun onStart() {
        super.onStart()

        val content = findViewById<View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, windowInsets ->
            navBarHeight = windowInsets
                .getInsets(WindowInsetsCompat.Type.systemBars())
                .bottom
            windowInsets
        }
        ViewUtils.requestApplyInsetsWhenAttached(content)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val screenHeight = window.decorView.height

        if (ev.y >= screenHeight - navBarHeight) {
            if (ev.action == MotionEvent.ACTION_UP) {
                // Let the ACTION_UP event through
                return super.dispatchTouchEvent(ev)
            }
            // Block other touch events in the specified area
            return true
        }
        try {
            return super.dispatchTouchEvent(ev)
        } catch (exception: IllegalArgumentException) {
            val message = exception.message
            if (message == null
                || (!message.contains("pointerIndex")
                        && !message.contains("pointer index"))
            ) {
                throw exception
            }

            // Some Android versions can deliver an inconsistent multi-touch pointer sequence.
            // This is safe to abandon, unlike exceptions thrown from inside a RecyclerView
            // layout/scroll, which must propagate so RecyclerView state is not silently poisoned.
            Log.w(TAG, "Ignoring invalid touch pointer sequence", exception)
            return false
        }
    }

    companion object {
        private const val TAG = "BaseActivity"
    }
}
