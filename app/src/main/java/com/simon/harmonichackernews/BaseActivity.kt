package com.simon.harmonichackernews

import android.R
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.NonNull
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.simon.harmonichackernews.utils.ViewUtils

open class BaseActivity : ComponentActivity() {
    private var navBarHeight = 0

    override fun onStart() {
        super.onStart()

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View?>(R.id.content),
            object : OnApplyWindowInsetsListener {
                override fun onApplyWindowInsets(
                    v: View,
                    windowInsets: WindowInsetsCompat
                ): WindowInsetsCompat {
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

                    navBarHeight = insets.bottom

                    return windowInsets
                }
            })
        ViewUtils.requestApplyInsetsWhenAttached(findViewById<View?>(R.id.content))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val screenHeight = getWindow().getDecorView().getHeight()
        val actionType = ev.getAction()

        if (ev.getY() >= (screenHeight - navBarHeight)) {
            if (actionType == MotionEvent.ACTION_UP) {
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
