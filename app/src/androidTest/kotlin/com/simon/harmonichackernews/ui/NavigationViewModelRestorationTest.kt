package com.simon.harmonichackernews.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.AndroidCommentsCoordinator
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationViewModelRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun rotationRetainsEachStorySessionAndItsSettingsParent() {
        val navigation = compose.activity.navigationController
        compose.runOnIdle {
            navigation.closeWelcome()
            navigation.dismissChangelogDialog()
            navigation.openSettings("debug")
        }
        compose.waitForIdle()
        compose.runOnIdle { navigation.openStory(StoryRoute(47938725).toDestination()) }
        compose.waitForIdle()
        val firstHost = requireNotNull(navigation.getCommentsCoordinator())
        val firstSession = session(firstHost)
        compose.runOnIdle { navigation.openLinkedStory(StoryRoute(48352939).toDestination()) }
        compose.waitForIdle()
        val secondHost = requireNotNull(navigation.getCommentsCoordinator())
        val secondSession = session(secondHost)
        assertNotSame(firstSession, secondSession)
        val expectedStack = navigation.navigationState.restoration()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val recreated = compose.activity.navigationController
        compose.runOnIdle {
            assertSame(navigation.scene, recreated.scene)
            assertEquals(expectedStack, recreated.navigationState.restoration())
            val host = requireNotNull(recreated.getCommentsCoordinator())
            assertNotSame("Native views must belong to the new activity", secondHost, host)
            assertSame(secondSession, session(host))
            recreated.navigationState.detailRemovedFromBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(firstSession, session(requireNotNull(recreated.getCommentsCoordinator())))
            recreated.navigationState.detailRemovedFromBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(MainDestination.SETTINGS, recreated.navigationState.state.value.currentDestination)
            assertEquals("debug", recreated.navigationState.state.value.settingsRequest?.initialSectionRoute)
            recreated.closeSettings()
        }
    }

    private fun session(coordinator: AndroidCommentsCoordinator): Any =
        AndroidCommentsCoordinator::class.java.getDeclaredField("sessionState").let {
            it.isAccessible = true
            requireNotNull(it.get(coordinator))
        }
}
