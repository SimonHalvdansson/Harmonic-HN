package com.simon.harmonichackernews.ui.common

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.insets.ProtectionLayout
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils
import com.simon.harmonichackernews.utils.ThemeUtils

abstract class HarmonicComposeActivity : AppCompatActivity() {

    private lateinit var statusBarProtection: ProtectionLayout

    @Composable
    protected abstract fun HarmonicContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeUtils.setupTheme(this, false)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        statusBarProtection = ProtectionLayout(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                HarmonicTheme {
                    HarmonicContent()
                }
            }
        }
        statusBarProtection.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(statusBarProtection)
        applyStatusBarProtection()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarProtection()
    }

    private fun applyStatusBarProtection() {
        StatusBarProtectionUtils.setTopProtection(
            statusBarProtection,
            StatusBarProtectionUtils.getPaneBackgroundColor(this),
        )
    }
}
