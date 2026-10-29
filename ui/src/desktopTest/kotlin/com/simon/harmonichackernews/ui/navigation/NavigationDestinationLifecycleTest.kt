package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NavigationDestinationLifecycleTest {
    @Test
    fun retainedScreensStopAndPoppedScreensAreDestroyedAfterTheirExit() = SwingUtilities.invokeAndWait {
        val host = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var entries by mutableStateOf(emptyList<String>())
        val owners = mutableMapOf<String, LifecycleOwner>()
        val identities = mutableMapOf<String, Any>()
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    ActivityNavigationStack(entries, { it }, root = { Probe("root", owners, identities) },
                        content = { Probe(it, owners, identities) })
                }
            }
        }
        var time = 0L
        fun frames(count: Int = 45) { repeat(count) { scene.render(time.also { time += 16_666_667 }).close() } }
        fun state(key: String) = owners.getValue(key).lifecycle.currentState
        try {
            frames()
            assertEquals(Lifecycle.State.RESUMED, state("root"))
            val root = identities.getValue("root")
            entries = listOf("first")
            frames(6)
            assertEquals(Lifecycle.State.STARTED, state("root"))
            assertEquals(Lifecycle.State.STARTED, state("first"))
            frames()
            assertEquals(Lifecycle.State.CREATED, state("root"))
            assertEquals(Lifecycle.State.RESUMED, state("first"))
            val first = identities.getValue("first")
            entries = listOf("first", "second")
            frames()
            assertEquals(Lifecycle.State.CREATED, state("first"))
            assertEquals(Lifecycle.State.RESUMED, state("second"))
            entries = listOf("first")
            frames(6)
            assertEquals(Lifecycle.State.CREATED, state("second"))
            assertEquals(Lifecycle.State.STARTED, state("first"))
            frames()
            assertEquals(Lifecycle.State.DESTROYED, state("second"))
            assertEquals(Lifecycle.State.RESUMED, state("first"))
            assertSame(first, identities["first"])
            host.lifecycle.currentState = Lifecycle.State.CREATED
            frames(3)
            assertEquals(Lifecycle.State.CREATED, state("first"))
            host.lifecycle.currentState = Lifecycle.State.RESUMED
            frames(3)
            assertEquals(Lifecycle.State.RESUMED, state("first"))
            entries = emptyList()
            frames()
            assertSame(root, identities["root"])
            assertEquals(Lifecycle.State.RESUMED, state("root"))
            assertEquals(Lifecycle.State.DESTROYED, state("first"))
        } finally { scene.close() }
        assertEquals(Lifecycle.State.DESTROYED, state("root"))
    }

    @Test
    fun predictiveCancellationAndCompletionRespectNestedLifecycles() = SwingUtilities.invokeAndWait {
        var outer by mutableStateOf(emptyList<String>())
        var inner by mutableStateOf(listOf("detail"))
        var preview by mutableStateOf<ActivityBackPreview<String>?>(null)
        var completed by mutableStateOf(false)
        val owners = mutableMapOf<String, LifecycleOwner>()
        val identities = mutableMapOf<String, Any>()
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                ActivityNavigationStack(outer, { it }, root = {
                    ActivityNavigationStack(inner, { it }, preview = preview, completedPredictiveBack = completed,
                        root = { Probe("list", owners, identities) }, content = { Probe(it, owners, identities) })
                }, content = { Probe(it, owners, identities) })
            }
        }
        var time = 0L
        fun frames(count: Int = 45) { repeat(count) { scene.render(time.also { time += 16_666_667 }).close() } }
        fun state(key: String) = owners.getValue(key).lifecycle.currentState
        try {
            frames()
            val detail = identities.getValue("detail")
            preview = ActivityBackPreview("detail", null, Modifier, Modifier)
            frames(4)
            assertEquals(Lifecycle.State.STARTED, state("list"))
            assertEquals(Lifecycle.State.STARTED, state("detail"))
            preview = null
            frames()
            assertEquals(Lifecycle.State.CREATED, state("list"))
            assertEquals(Lifecycle.State.RESUMED, state("detail"))
            outer = listOf("cover")
            frames()
            assertEquals(Lifecycle.State.CREATED, state("detail"))
            outer = emptyList()
            frames()
            assertEquals(Lifecycle.State.RESUMED, state("detail"))
            assertSame(detail, identities["detail"])
            preview = ActivityBackPreview("detail", null, Modifier, Modifier)
            frames(4)
            inner = emptyList()
            completed = true
            frames(4)
            preview = null
            completed = false
            frames()
            assertEquals(Lifecycle.State.DESTROYED, state("detail"))
            assertEquals(Lifecycle.State.RESUMED, state("list"))
        } finally { scene.close() }
    }

    @Composable
    private fun Probe(key: String, owners: MutableMap<String, LifecycleOwner>, identities: MutableMap<String, Any>) {
        val owner = LocalLifecycleOwner.current
        val identity = remember { Any() }
        SideEffect { owners[key] = owner; identities[key] = identity }
    }
}
