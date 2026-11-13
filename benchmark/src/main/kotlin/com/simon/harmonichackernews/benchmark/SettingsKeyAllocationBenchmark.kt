@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.CommentsHeaderButton
import com.simon.harmonichackernews.settings.CommentsHeaderButtons
import com.simon.harmonichackernews.settings.GlassParameter
import com.simon.harmonichackernews.settings.GlassPreferences
import com.simon.harmonichackernews.settings.GlassSwitch
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * The three enum-key read loops from StoredUserSettings, before and after key reuse.
 * Measures the same preference lookups and result maps in both paths; excludes fixture creation.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsKeyAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var headerSink: CommentsHeaderButtons? = null
    @Volatile private var glassSink: GlassPreferences? = null
    private val defaults = InMemoryKeyValueStore()
    private val configured = InMemoryKeyValueStore().apply {
        CommentsHeaderButton.entries.filter { it.configurable }.forEach {
            putBoolean(legacyHeaderKey(it), false)
        }
        GlassParameter.entries.forEach { putFloat(legacyParameterKey(it), it.range.start) }
        GlassSwitch.entries.forEach { putBoolean(legacySwitchKey(it), !it.default) }
    }

    @Before fun verifyEquivalence() {
        CommentsHeaderButton.entries.forEach { check(legacyHeaderKey(it) == it.storageKey) }
        GlassParameter.entries.forEach { check(legacyParameterKey(it) == it.storageKey) }
        GlassSwitch.entries.forEach { check(legacySwitchKey(it) == it.storageKey) }
        for (store in listOf(defaults, configured)) {
            val settings = StoredUserSettings(store, emptyFlow())
            readLegacyOverrides(store)
            check(headerSink == settings.comments.headerButtons)
            check(glassSink == settings.debug.glass)
            readOptimizedOverrides(store)
            check(headerSink == settings.comments.headerButtons)
            check(glassSink == settings.debug.glass)
        }
    }

    @Test fun configuredBefore() = benchmarkRule.measureRepeated { readLegacyOverrides(configured) }
    @Test fun configuredOptimized() = benchmarkRule.measureRepeated { readOptimizedOverrides(configured) }
    @Test fun defaultsBefore() = benchmarkRule.measureRepeated { readLegacyOverrides(defaults) }
    @Test fun defaultsOptimized() = benchmarkRule.measureRepeated { readOptimizedOverrides(defaults) }

    private fun readLegacyOverrides(store: KeyValueStore) {
        headerSink = CommentsHeaderButtons(CommentsHeaderButton.entries
            .filter { it.configurable && store.contains(legacyHeaderKey(it)) }
            .associateWith { store.getBoolean(legacyHeaderKey(it), false) })
        glassSink = GlassPreferences(
            parameters = GlassParameter.entries.filter { store.contains(legacyParameterKey(it)) }
                .associateWith { it.sanitize(store.getFloat(legacyParameterKey(it), it.default)) },
            switches = GlassSwitch.entries.filter { store.contains(legacySwitchKey(it)) }
                .associateWith { store.getBoolean(legacySwitchKey(it), it.default) },
        )
    }

    private fun readOptimizedOverrides(store: KeyValueStore) {
        headerSink = CommentsHeaderButtons(CommentsHeaderButton.entries
            .filter { it.configurable && store.contains(it.storageKey) }
            .associateWith { store.getBoolean(it.storageKey, false) })
        glassSink = GlassPreferences(
            parameters = GlassParameter.entries.filter { store.contains(it.storageKey) }
                .associateWith { it.sanitize(store.getFloat(it.storageKey, it.default)) },
            switches = GlassSwitch.entries.filter { store.contains(it.storageKey) }
                .associateWith { store.getBoolean(it.storageKey, it.default) },
        )
    }

    // Frozen pre-change getter bodies; no other work differs between the paired read loops.
    private fun legacyHeaderKey(button: CommentsHeaderButton) =
        "pref_comments_header_button_${button.name.lowercase()}"
    private fun legacyParameterKey(parameter: GlassParameter) = "pref_debug_glass_${parameter.name}"
    private fun legacySwitchKey(option: GlassSwitch) = "pref_debug_glass_${option.name}_enabled"
}
