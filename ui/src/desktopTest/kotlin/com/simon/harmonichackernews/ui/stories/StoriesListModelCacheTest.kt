package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.PortableStoryListState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.StoryRowModelFactory
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class StoriesListModelCacheTest {
    @Test
    fun metadataUpdatesRebuildOnlyTheChangedRowButFeedChangesAndResumeRefreshAges() = SwingUtilities.invokeAndWait {
        val listener = Proxy.newProxyInstance(
            StoriesScreenController.Listener::class.java.classLoader,
            arrayOf(StoriesScreenController.Listener::class.java),
        ) { _, _, _ -> null } as StoriesScreenController.Listener
        val controller = StoriesScreenController.create(100, object : SavedItemStateReader {
            override fun isBookmarked(itemId: Int) = false
            override fun isFavorited(itemId: Int) = false
            override fun isUpvoted(itemId: Int, isComment: Boolean) = false
        }, listener)
        val settings = StoryDisplaySettings.from(StoredUserSettings(InMemoryKeyValueStore(), emptyFlow()).story)
            .copy(showFavicons = false, font = "devicedefault")
        var rows = (1..4).map { id ->
            StoryListItemSnapshot(StorySnapshot(id, title = "Story $id", score = id),
                StoryPresentationSnapshot(loaded = true))
        }
        var selectedTypeIndex = 0
        fun publish() = controller.updateContent(StoriesState(
            mainList = PortableStoryListState(items = rows), displaySettings = settings,
            selectedTypeIndex = selectedTypeIndex,
        ))
        publish()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val builds = mutableMapOf<Int, Int>()
        val modelTimes = mutableMapOf<Int, Long>()
        val scene = ImageComposeScene(400, 1000, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    StoriesScreen(
                        controller, storyItemModelCacheKey = 0,
                        storyItemModel = { item, index, _, _, now ->
                            builds[item.id] = builds.getOrDefault(item.id, 0) + 1
                            modelTimes[item.id] = now
                            StoryRowModelFactory.create(item, index, nowMillis = now)
                        },
                        filterColors = harmonicFilterButtonColors(), pullToRefreshEnabled = false,
                    )
                }
            }
        }
        var time = 0L
        fun settle() = repeat(30) { scene.render(time.also { time += 16_666_667 }).close() }
        try {
            settle()
            assertEquals(setOf(1, 2, 3, 4), builds.keys)
            builds.clear()
            val updateTime = Clock.System.now().toEpochMilliseconds()
            rows = rows.map { if (it.id == 2) it.copy(story = it.story.copy(score = 99)) else it }
            publish()
            settle()
            assertEquals(mapOf(2 to 1), builds, "Unchanged visible rows must keep their cached models")
            assertTrue(modelTimes.getValue(2) >= updateTime, "Changed rows must use the current time")
            builds.clear()
            selectedTypeIndex = 1
            publish()
            settle()
            assertTrue(builds.keys.containsAll(listOf(1, 2, 3, 4)), "Changing feed must refresh displayed ages")
            builds.clear()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            settle()
            assertTrue(builds.keys.containsAll(listOf(1, 2, 3, 4)), "Resume must refresh every displayed age")
        } finally {
            scene.close()
        }
    }
}
