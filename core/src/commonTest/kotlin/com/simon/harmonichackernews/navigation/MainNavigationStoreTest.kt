package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainNavigationStoreTest {
    @Test
    fun submissionsOpenedFromDebugUseTheirActualStackParent() {
        val store = MainNavigationStore()
        store.openSettings("debug")
        store.showUserDialog("pg")
        store.dismissUserDialog()
        store.openSubmissions("pg")
        assertEquals(MainDestination.SETTINGS, store.state.value.parentDestination(MainDestination.SUBMISSIONS))
        store.openStory(StoryRoute(42))
        assertEquals(MainDestination.SUBMISSIONS, store.state.value.parentDestination(MainDestination.STORY))
        store.detailRemovedFromBackStack()
        store.closeSubmissions()
        assertEquals(MainDestination.SETTINGS, store.state.value.currentDestination)
        assertEquals("debug", store.state.value.currentSettingsSectionRoute)
        store.closeSettings()
        store.openSubmissions("pg")
        assertEquals(MainDestination.STORIES, store.state.value.parentDestination(MainDestination.SUBMISSIONS))
        assertNull(store.state.value.parentDestination(MainDestination.SETTINGS))
    }

    @Test
    fun browserExitCanReturnDirectlyToStoriesFromNestedDestinations() {
        val store = MainNavigationStore()
        store.openSettings("debug")
        store.openStory(StoryRoute(42))
        store.openLinkedStory(StoryRoute(43).toDestination())
        store.returnToStories()
        assertEquals(listOf(MainNavigationEntry.Stories), store.state.value.destinationStack)
        assertNull(store.state.value.storyRequest)
        assertEquals(MainDestination.STORIES, store.state.value.currentDestination)
        store.returnToStories()
        assertEquals(1, store.state.value.destinationStack.size)
    }

    @Test
    fun publishesEveryTransitionAsASnapshot() {
        val store = MainNavigationStore()

        store.openSettings("appearance")
        assertEquals("appearance", store.state.value.settingsRequest?.initialSectionRoute)

        store.openStory(StoryRoute(42, showWebsite = true))
        assertEquals("appearance", store.state.value.settingsRequest?.initialSectionRoute)
        assertEquals(42, store.state.value.storyRequest?.storyId)
        assertEquals(
            listOf(MainDestination.STORIES, MainDestination.SETTINGS, MainDestination.STORY),
            store.state.value.destinationStack.map(MainNavigationEntry::destination),
        )
        assertEquals(MainDestination.STORY, store.state.value.currentDestination)
    }

    @Test
    fun publishesTransientDialogDescriptorsAndDismissals() {
        val store = MainNavigationStore()
        val challenge = HackerNewsCaptchaChallenge(
            actionUrl = "https://news.ycombinator.com/login",
            siteKey = "key",
            formFields = emptyList(),
            useCookies = true,
        )

        val captcha = store.showCaptchaDialog(challenge)
        val user = store.showUserDialog("simon")
        val failure = store.showFailureDetailDialog("Failed", "Details", null)

        assertEquals(captcha, store.state.value.captchaRequest)
        assertEquals(user, store.state.value.userRequest)
        assertEquals(failure, store.state.value.failureRequest)

        store.dismissCaptchaDialog()
        store.dismissUserDialog()
        store.dismissFailureDetailDialog()

        assertNull(store.state.value.captchaRequest)
        assertNull(store.state.value.userRequest)
        assertNull(store.state.value.failureRequest)
    }
}
