package com.simon.harmonichackernews.benchmark

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.settings.*
import java.util.Calendar
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Independent before/after kernels for the three startup micro-optimizations.
 * This measures warmed operations, not cold-start wall time. Window setters and navigation
 * dispatch are unchanged and excluded. Both theme-reuse cases use the frozen old selector,
 * so their delta does not also include the nighttime fast path.
 */
@RunWith(AndroidJUnit4::class)
class StartupMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var legacy: StartupLegacyAppearanceSelection
    private lateinit var optimized: AppearanceRuntime
    private val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Volatile private var result: Any? = null
    @Volatile private var lightBars = false

    @Before
    fun prepare() {
        // Only this benchmark APK's private fixture store is reset; normal app settings are untouched.
        val preferences = context.getSharedPreferences("startup_micro_fixture", Context.MODE_PRIVATE)
        check(preferences.edit().clear().commit())
        val store = BenchmarkSharedPreferencesStore(preferences)
        val scheduleStore = NighttimeScheduleStore(store)
        val clock = {
            Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        }
        val systemDark = {
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        legacy = StartupLegacyAppearanceSelection(store, scheduleStore, clock, systemDark)
        optimized = AppearanceRuntime(
            settings = store,
            scheduleStore = scheduleStore,
            launchState = AppLaunchStateStore(store),
            settingsChanges = emptyFlow(),
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = clock,
            systemDark = systemDark,
        )
        check(legacy.selection() == optimized.selection())
        check(launcherDestinationBefore(launcherIntent) == launcherDestinationAfter(launcherIntent))
    }

    @Test fun themeReuseBefore() = benchmarkRule.measureRepeated {
        val theme = legacy.selection().theme
        val lightStatus = !isDark(legacy.selection().theme)
        val lightNavigation = !isDark(legacy.selection().theme)
        result = theme
        lightBars = lightStatus && lightNavigation
    }

    @Test fun themeReuseAfter() = benchmarkRule.measureRepeated {
        val selection = legacy.selection()
        result = selection.theme
        lightBars = !selection.dark && !selection.dark
    }

    @Test fun nighttimeDisabledBefore() = benchmarkRule.measureRepeated {
        result = legacy.selection()
    }

    @Test fun nighttimeDisabledAfter() = benchmarkRule.measureRepeated {
        result = optimized.selection()
    }

    @Test fun launcherBundleBefore() = benchmarkRule.measureRepeated {
        result = launcherDestinationBefore(launcherIntent)
    }

    @Test fun launcherBundleAfter() = benchmarkRule.measureRepeated {
        result = launcherDestinationAfter(launcherIntent)
    }

    private fun isDark(theme: String): Boolean = if (ThemePreferences.isAutomatic(theme)) {
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    } else ThemePreferences.isDark(theme)

    // Exact no-extras path from MainLaunchIntentRouter.directStoryDestination. The populated
    // legacy-Bundle branch is deliberately excluded: this benchmark is for ordinary launches.
    private fun launcherDestinationBefore(intent: Intent): StoryDestination? =
        (intent.extras?.let(::Bundle) ?: Bundle()).let { arguments ->
            (AppDestinationCodec.decode(intent.getStringExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA))
                as? StoryDestination) ?: if (arguments.getInt(EXTRA_ID, -1) <= 0) null
            else error("Only no-extras launcher intents belong in this benchmark")
        }

    private fun launcherDestinationAfter(intent: Intent): StoryDestination? {
        (AppDestinationCodec.decode(intent.getStringExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA))
            as? StoryDestination)?.let { return it }
        val arguments = intent.extras ?: return null
        return if (arguments.getInt(EXTRA_ID, -1) <= 0) null
        else error("Only no-extras launcher intents belong in this benchmark")
    }

    private companion object {
        const val EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID"
    }
}

/** Frozen AppearanceRuntime.selection() from 6d172d39, before any of these changes. */
private class StartupLegacyAppearanceSelection(
    private val settings: KeyValueStore,
    private val scheduleStore: NighttimeScheduleStore,
    private val currentMinutesFromMidnight: () -> Int,
    private val systemDark: () -> Boolean,
) {
    val schedule: NighttimeSchedule get() = scheduleStore.load()

    fun selection(): ThemeSelection = ThemeSelectionPolicy.select(
        configuredTheme = settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
        nighttimeTheme = settings.getString(
            ThemePreferences.NIGHTTIME_KEY,
            ThemePreferences.DEFAULT_NIGHTTIME,
        ),
        useSpecialNighttimeTheme = settings.getBoolean(
            UserPreferenceKeys.SPECIAL_NIGHTTIME,
            false,
        ),
        schedule = schedule,
        currentMinutesFromMidnight = currentMinutesFromMidnight(),
        systemDark = systemDark(),
        followSystem = if (settings.contains(ThemePreferences.FOLLOW_SYSTEM_KEY)) {
            settings.getBoolean(ThemePreferences.FOLLOW_SYSTEM_KEY, true)
        } else {
            ThemePreferences.isAutomatic(
                settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
            )
        },
        manualDark = if (settings.contains(ThemePreferences.MANUAL_DARK_KEY)) {
            settings.getBoolean(ThemePreferences.MANUAL_DARK_KEY, false)
        } else {
            ThemePreferences.isDark(
                settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
            )
        },
        lightTheme = settings.getString(ThemePreferences.LIGHT_KEY),
        darkTheme = settings.getString(ThemePreferences.DARK_KEY),
        accentPreset = settings.getString(
            ThemePreferences.ACCENT_KEY,
            ThemePreferences.ACCENT_DEFAULT,
        ) ?: ThemePreferences.ACCENT_DEFAULT,
    )
}
