package com.simon.harmonichackernews

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bootstrap UI must work before preferences and the application composition are available. */
internal fun ComponentActivity.runAfterStartup(
    prepare: suspend () -> Unit,
    retryStartup: () -> Unit,
    onFailure: () -> Unit = {},
    onReady: () -> Unit,
) {
    var attempt: Job? = null
    fun start() {
        if (attempt?.isActive == true) return
        attempt = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                prepare()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HarmonicStartup", "Could not open saved data", error)
                showStartupFailure {
                    retryStartup()
                    start()
                }
                onFailure()
                return@launch
            }
            onReady()
        }
    }
    start()
}

private fun ComponentActivity.showStartupFailure(onRetry: () -> Unit) {
    val padding = (24 * resources.displayMetrics.density).roundToInt()
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(padding, padding, padding, padding)
        addView(TextView(context).apply {
            setText(R.string.startup_failure_title)
            textSize = 22f
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            setText(R.string.startup_failure_message)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        })
        addView(Button(context).apply {
            setText(R.string.startup_retry)
            setOnClickListener {
                isEnabled = false
                setText(R.string.startup_retrying)
                onRetry()
            }
        })
        addView(Button(context).apply {
            setText(R.string.startup_app_settings)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)))
                } catch (_: ActivityNotFoundException) {
                    isEnabled = false
                }
            }
        })
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }
    ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    setContentView(scroll)
    ViewCompat.requestApplyInsets(scroll)
}
