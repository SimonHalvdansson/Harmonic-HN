package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainNavigationStateTest {
    @Test
    fun storyDestinationRoundTripsImmutableDomainSeedWithoutResourceState() {
        val source = StoryDestination(
            storyId = 42,
            seed = StoryNavigationSeed(
                story = StorySnapshot(
                    id = 42,
                    title = "Title",
                    author = "author",
                    url = "https://example.com",
                    childIds = listOf(1, 2),
                    pollOptionIds = listOf(3, 4),
                    descendantCount = 8,
                    score = 9,
                ),
                isLink = true,
            ),
        )

        val story = source.toStory()

        assertEquals(42, story.id)
        assertEquals("Title", story.title)
        assertEquals("author", story.by)
        assertTrue(story.loaded)
        assertFalse(story.previewImageUrlLoaded)
        assertFalse(story.previewImageTintColorLoaded)
        assertEquals(listOf(1, 2), story.kids?.toList())
        assertEquals(listOf(3, 4), story.pollOptions?.toList())
        assertEquals(8, story.descendants)
        assertEquals(9, story.score)
        assertTrue(story.isLink)
    }

    @Test
    fun closingAStoryOpenedFromSettingsRestoresTheSettingsSection() {
        val state = MainNavigationState()
        state.openSettings("appearance")
        state.openStory(StoryDestination(storyId = 42))

        assertNull(state.settingsRequest)
        assertTrue(state.storyOpenedFromSettings)

        state.detailRemovedFromBackStack()

        assertNull(state.storyRequest)
        assertEquals("appearance", state.settingsRequest?.initialSectionRoute)
        assertFalse(state.storyOpenedFromSettings)
    }

    @Test
    fun stableRouteRestoresWithoutCarryingStoryPresentationState() {
        val route = StoryRoute(storyId = 42, showWebsite = true, scrollToCommentId = 7)
        val state = MainNavigationState(MainNavigationRestoration(storyRoute = route))

        assertEquals(route, state.storyRequest?.route)
        assertEquals(42, state.storyRequest?.destination?.storyId)
        assertNull(state.storyRequest?.destination?.seed)
    }

    @Test
    fun restoredRequestsContinueWithIncreasingSerials() {
        val state = MainNavigationState(
            MainNavigationRestoration(
                storyDestination = StoryDestination(storyId = 1),
                storyRequestSerial = 4,
                settingsOpen = true,
                settingsRequestSerial = 7,
                settingsSectionRoute = "data",
            ),
        )

        assertEquals(4, state.storyRequest?.serial)
        assertEquals(7, state.settingsRequest?.serial)

        state.openStory(StoryDestination(storyId = 2))
        state.openSettings("comments")

        assertEquals(5, state.storyRequest?.serial)
        assertEquals(8, state.settingsRequest?.serial)
    }

    @Test
    fun settingsRestartRequestIsConsumedExactlyOnce() {
        val state = MainNavigationState()
        state.openSettings(null)
        state.onSettingsThemeChanged()

        assertTrue(state.consumeSettingsRestartRequest())
        assertFalse(state.consumeSettingsRestartRequest())
        assertEquals(1, state.settingsThemeRevision)
    }

    @Test
    fun openingEditorDismissesCompetingMainSurfaces() {
        val state = MainNavigationState()
        state.openSettings("stories")
        state.openSubmissions("simon")
        state.openCoulombGas()

        state.openEditor(EditorDestination(EditorType.POST))

        assertNull(state.settingsRequest)
        assertNull(state.submissionsRequest)
        assertFalse(state.coulombGasVisible)
        assertEquals(EditorType.POST, state.editorRequest?.destination?.type)
    }

    @Test
    fun transientDialogsOwnSerialAndPresencePolicyInSharedState() {
        val state = MainNavigationState()
        val firstCaptcha = state.showCaptchaDialog(captcha("first"))
        val replacementCaptcha = state.showCaptchaDialog(captcha("second"))
        val user = state.showUserDialog("simon")
        val failure = state.showFailureDetailDialog("Failed", "Details", "copy")

        assertEquals(1, firstCaptcha.serial)
        assertEquals(2, replacementCaptcha.serial)
        assertEquals(replacementCaptcha, state.captchaRequest)
        assertEquals(1, user?.serial)
        assertEquals(user, state.userRequest)
        assertEquals(1, failure.serial)
        assertEquals(failure, state.failureRequest)

        assertEquals(replacementCaptcha, state.dismissCaptchaDialog())
        assertEquals(user, state.dismissUserDialog())
        assertEquals(failure, state.dismissFailureDetailDialog())
        assertNull(state.captchaRequest)
        assertNull(state.userRequest)
        assertNull(state.failureRequest)
    }

    @Test
    fun restoredUserDialogContinuesItsSerialSequence() {
        val state = MainNavigationState(
            MainNavigationRestoration(
                userDialogUserName = "restored-user",
                userDialogSerial = 7,
            ),
        )

        assertEquals(MainUserRequest(7, "restored-user"), state.userRequest)

        val next = state.showUserDialog("next-user")

        assertEquals(MainUserRequest(8, "next-user"), next)
    }

    @Test
    fun blankUserDoesNotReplaceVisibleUserDialog() {
        val state = MainNavigationState()
        val visible = state.showUserDialog("simon")

        assertNull(state.showUserDialog("  "))
        assertEquals(visible, state.userRequest)
    }

    private fun captcha(siteKey: String) = HackerNewsCaptchaChallenge(
        actionUrl = "https://news.ycombinator.com/login",
        siteKey = siteKey,
        formFields = emptyList(),
        useCookies = true,
    )
}
