package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainNavigationStoreTest {
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
