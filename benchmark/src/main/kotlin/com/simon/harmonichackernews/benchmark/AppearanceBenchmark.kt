package com.simon.harmonichackernews.benchmark

import android.content.Context
import android.content.res.Configuration
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.settings.AppearanceRuntime
import com.simon.harmonichackernews.settings.NighttimeScheduleStore
import com.simon.harmonichackernews.settings.ThemePreferences
import java.util.Calendar
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var runtime: AppearanceRuntime
    @Volatile private var result: Any? = null
    @Volatile private var classification = 0

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
        runtime = AppearanceRuntime(
            settings = store,
            scheduleStore = scheduleStore,
            launchState = AppLaunchStateStore(store),
            settingsChanges = emptyFlow(),
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = clock,
            systemDark = systemDark,
        )
    }

    @Test fun nighttimeDisabled() = benchmarkRule.measureRepeated {
        result = runtime.selection()
    }

    private val themes = arrayOf(
        "material_daynight", "material_light", "material_dark", "hacker", "white",
        "material_fixed_daynight", "material_fixed_dark", "amoled", "gray", "unknown", null,
    )
    private var index = 0

    @Test fun themeClassification() = benchmarkRule.measureRepeated {
        val theme = nextTheme()
        classification = (if (ThemePreferences.isAutomatic(theme)) 1 else 0) +
            (if (ThemePreferences.isDark(theme)) 2 else 0)
    }

    private fun nextTheme(): String? {
        val theme = themes[index]
        index = if (index + 1 == themes.size) 0 else index + 1
        return theme
    }
}
