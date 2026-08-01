package com.simon.harmonichackernews

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsShell
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.ThemeUtils

/**
 * Compose-only Settings host. Deprecated View hosts remain only for temporary source compatibility;
 * all app entry points route here.
 */
class ComposeSettingsActivity : AppCompatActivity() {

    private var needsRestart = false
    private var requestedSection by mutableStateOf<SettingsSection?>(null)
    private var themeRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        needsRestart = savedInstanceState?.getBoolean(STATE_NEEDS_RESTART)
            ?: intent.getBooleanExtra(EXTRA_NEEDS_RESTART, false)
        requestedSection = sectionFromIntent(intent)

        ThemeUtils.setupTheme(this, false)
        val detectDarkMode = { _: android.content.res.Resources ->
            ThemeUtils.isDarkMode(this)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                detectDarkMode,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                detectDarkMode,
            ),
        )

        setContent {
            Crossfade(
                targetState = themeRevision,
                animationSpec = tween(durationMillis = 220),
                label = "Settings theme",
            ) { revision ->
                key(revision) {
                    HarmonicTheme {
                        SettingsContent()
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsContent() {
        SettingsShell(
            initialSection = requestedSection,
            onBackFromSettings = ::leaveSettings,
            onSectionChanged = { section ->
                intent.putExtra(EXTRA_SECTION, section.route)
            },
            onThemeChanged = {
                needsRestart = true
                intent.putExtra(EXTRA_NEEDS_RESTART, true)
                requestedSection = sectionFromIntent(intent)
                ThemeUtils.setupTheme(this, false)
                themeRevision++
            },
            onRequestRestart = {
                needsRestart = true
                intent.putExtra(EXTRA_NEEDS_RESTART, true)
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedSection = sectionFromIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_NEEDS_RESTART, needsRestart)
        super.onSaveInstanceState(outState)
    }

    private fun leaveSettings() {
        if (!needsRestart) {
            finish()
            return
        }

        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launchIntent)
        }
    }

    private fun sectionFromIntent(intent: Intent): SettingsSection? =
        intent.getStringExtra(EXTRA_SECTION)?.let(SettingsSection::fromRoute)

    companion object {
        const val EXTRA_SECTION =
            "com.simon.harmonichackernews.ComposeSettingsActivity.EXTRA_SECTION"
        private const val EXTRA_NEEDS_RESTART =
            "com.simon.harmonichackernews.ComposeSettingsActivity.EXTRA_NEEDS_RESTART"
        private const val STATE_NEEDS_RESTART = "state_needs_restart"

        @JvmStatic
        fun createAiSummaryIntent(context: Context): Intent =
            Intent(context, ComposeSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SECTION, SettingsSection.AiSummary.route)
    }
}
