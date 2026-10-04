package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.StoryRowModelFactory
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {
    @Test
    fun cachedAgeRefreshesAfterRepeatedResumesWithoutContentChanges() = SwingUtilities.invokeAndWait {
        val owner = TestLifecycleOwner()
        val createdAt = 1_700_000_000
        val story = StoryListItemSnapshot(
            StorySnapshot(42, title = "Unchanged story", createdAtEpochSeconds = createdAt),
            StoryPresentationSnapshot(loaded = true),
        )
        val hourMillis = 3_600_000L
        var now = createdAt * 1_000L + hourMillis
        var age = ""
        val scene = ImageComposeScene(1, 1) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val time = rememberTimeOnResume(nowMillis = { now })
                age = remember(story, time) { StoryRowModelFactory.create(story, nowMillis = time) }.age
            }
        }
        try {
            scene.render().close()
            assertEquals("1h", age)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            now += 2 * hourMillis
            scene.render().close()
            assertEquals("1h", age)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            scene.render().close()
            assertEquals("3h", age)
            // Focus loss can pause a desktop window without stopping it.
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            now += 2 * hourMillis
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            scene.render().close()
            assertEquals("5h", age)
        } finally {
            scene.close()
        }
        assertEquals(0, owner.lifecycle.observerCount)
    }

    @Test
    fun firstResumeRefreshesContentComposedWhileInactive() = SwingUtilities.invokeAndWait {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED)
        var now = 1_000L
        var displayedTime = 0L
        val scene = ImageComposeScene(1, 1) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                displayedTime = rememberTimeOnResume(nowMillis = { now })
            }
        }
        try {
            scene.render().close()
            assertEquals(now, displayedTime)
            now = 60_000L
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            scene.render().close()
            assertEquals(now, displayedTime)
        } finally {
            scene.close()
        }
    }

    private class TestLifecycleOwner(state: Lifecycle.State = Lifecycle.State.RESUMED) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = state }
    }
}
