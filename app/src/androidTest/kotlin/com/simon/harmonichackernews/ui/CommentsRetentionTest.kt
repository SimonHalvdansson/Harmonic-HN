package com.simon.harmonichackernews.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.AndroidCommentsCoordinator
import com.simon.harmonichackernews.AndroidCommentsWebViewController
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.navigation.AndroidMainNavigationController
import com.simon.harmonichackernews.ui.settings.DebugSampleContentLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise real pane disposal and native browser teardown, including a detail no longer composed. */
@RunWith(AndroidJUnit4::class)
class CommentsRetentionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun replacingTabletSubmissionHistoryReleasesHiddenDetails() = exerciseRemoval(close = false)

    @Test fun closingTabletSubmissionsReleasesHiddenDetails() = exerciseRemoval(close = true)

    @Test fun returningToHiddenTabletDetailPreservesItsCoordinator() {
        withSubmissions { navigation ->
            val first = openDetail(navigation)
            compose.runOnIdle { navigation.openLinkedStory(sample("Reference links post")) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue(cached(navigation).containsValue(first))
                assertTrue(first.readField<CoroutineScope>("coroutineScope").isActive)
                navigation.detailRemovedFromBackStack()
            }
            compose.waitForIdle()
            compose.runOnIdle { assertSame(first, navigation.getCommentsCoordinator()) }
        }
    }

    @Test fun removedBookmarksDoNotAccumulateMutationRevisions() = runBlocking {
        val repository = SavedItemsRepository(InMemoryKeyValueStore())
        repeat(1_000) { id ->
            repository.setMembershipAtomic(SavedItemSource.BOOKMARKS, id + 1, true, 1)
            repository.setMembershipAtomic(SavedItemSource.BOOKMARKS, id + 1, false, 1)
        }
        assertTrue(repository.loadItems(SavedItemSource.BOOKMARKS).isEmpty())
        assertEquals(0, repository.readField<Map<*, *>>("itemMutationRevisions").size)
    }

    private fun exerciseRemoval(close: Boolean) = withSubmissions { navigation ->
        repeat(3) {
            val first = openDetail(navigation)
            val browser = compose.runOnIdle {
                first.readField<Any>("viewSession")
                    .readField<AndroidCommentsWebViewController>("webViewController")
                    .also { it.initializeForVisibleWebsite() }
            }
            compose.waitUntil(15_000) { compose.runOnIdle { browser.hasWebView() } }
            compose.runOnIdle { navigation.openLinkedStory(sample("Reference links post")) }
            compose.waitForIdle()
            compose.runOnIdle {
                // A is no longer composed, but remains a valid back target for B.
                assertTrue(cached(navigation).containsValue(first))
                assertFalse(navigation.readField<Map<Int, Int>>("commentsCoordinatorReferences")
                    .containsKey(first.sessionKey))
                if (close) navigation.closeSubmissions()
                else navigation.openSubmissionStory(sample("Poll"))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("Only currently reachable details should remain cached",
                    if (close) 0 else 1, cached(navigation).size)
                assertNull("Discarded screen must release its UI", first.composeUiController)
                assertFalse("Discarded screen must cancel its scope",
                    first.readField<CoroutineScope>("coroutineScope").isActive)
                assertFalse("Discarded screen must destroy its WebView", browser.hasWebView())
                if (close) navigation.openSubmissions("pg")
            }
            compose.waitForIdle()
        }
    }

    private fun withSubmissions(block: (AndroidMainNavigationController) -> Unit) {
        val navigation = compose.activity.navigationController
        assumeTrue("Requires a two-pane emulator", navigation.isAdaptiveTwoPane())
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.openSubmissions("pg")
            }
            compose.waitForIdle()
            block(navigation)
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    private fun openDetail(navigation: AndroidMainNavigationController): AndroidCommentsCoordinator {
        compose.runOnIdle { navigation.openSubmissionStory(sample("Link post")) }
        compose.waitForIdle()
        return compose.runOnIdle { requireNotNull(navigation.getCommentsCoordinator()) }
    }

    private fun sample(title: String): StoryDestination = StoryDestination(
        DebugSampleContentLinks.single { it.title == title }.url.substringAfter("id=").toInt(),
    )

    private fun cached(navigation: AndroidMainNavigationController): Map<Int, AndroidCommentsCoordinator> =
        navigation.readField("commentsCoordinatorCache")

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.readField(name: String): T = javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(this) as T
    }
}
