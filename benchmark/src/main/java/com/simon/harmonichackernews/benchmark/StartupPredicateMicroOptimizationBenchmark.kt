package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.ThemePreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One automatic/dark classification pair, used by startup appearance and story/comment settings. */
@RunWith(AndroidJUnit4::class)
class StartupPredicateMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val themes = arrayOf(
        "material_daynight", "material_light", "material_dark", "hacker", "white",
        "material_fixed_daynight", "material_fixed_dark", "amoled", "gray", "unknown", null,
    )
    private var index = 0
    @Volatile private var result = 0

    @Test fun themeClassificationBefore() = benchmarkRule.measureRepeated {
        val theme = nextTheme()
        result = (if (legacyIsAutomatic(theme)) 1 else 0) + (if (legacyIsDark(theme)) 2 else 0)
    }

    @Test fun themeClassificationAfter() = benchmarkRule.measureRepeated {
        val theme = nextTheme()
        result = (if (ThemePreferences.isAutomatic(theme)) 1 else 0) +
            (if (ThemePreferences.isDark(theme)) 2 else 0)
    }

    private fun nextTheme(): String? {
        val theme = themes[index]
        index = if (index + 1 == themes.size) 0 else index + 1
        return theme
    }

    private fun legacyIsAutomatic(theme: String?): Boolean = theme in setOf(
        ThemePreferences.DEFAULT,
        ThemePreferences.MATERIAL_FIXED_AUTO,
        "darklight_daynight",
        "amoledwhite_daynight",
    )

    private fun legacyIsDark(theme: String?): Boolean = theme in setOf(
        "material_dark", ThemePreferences.MATERIAL_FIXED_DARK, "dark", "hacker", "amoled", "gray",
    )
}
