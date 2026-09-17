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
    fun storyPushKeepsItsSettingsParentAndOnePopRestoresIt() {
        val state = MainNavigationState()
        state.openSettings("appearance")
        val settingsRequest = state.settingsRequest
        state.openStory(StoryDestination(storyId = 42))

        assertEquals(settingsRequest, state.settingsRequest)
        assertEquals(MainDestination.STORY, state.currentDestination)
        assertEquals(
            listOf(MainDestination.STORIES, MainDestination.SETTINGS, MainDestination.STORY),
            state.destinationStack.map(MainNavigationEntry::destination),
        )

        state.detailRemovedFromBackStack()

        assertNull(state.storyRequest)
        assertEquals(settingsRequest, state.settingsRequest)
        assertEquals(MainDestination.SETTINGS, state.currentDestination)
        assertEquals(
            listOf(MainDestination.STORIES, MainDestination.SETTINGS),
            state.destinationStack.map(MainNavigationEntry::destination),
        )
    }

    @Test
    fun linkedStoryKeepsTheOriginalStoryAsItsImmediateBackTarget() {
        val store = MainNavigationStore()
        store.openStory(StoryDestination(storyId = 49_449_677))

        store.openLinkedStory(StoryDestination(storyId = 49_449_650))

        assertEquals(
            listOf(MainDestination.STORIES, MainDestination.STORY, MainDestination.STORY),
            store.state.value.destinationStack.map(MainNavigationEntry::destination),
        )
        assertEquals(listOf(49_449_677, 49_449_650), store.state.value.storyBackStack.map { it.storyId })
        assertEquals(MainDestination.STORY, store.state.value.storyParentDestination)
        assertEquals(MainDestination.STORIES, store.state.value.storyStackParentDestination)
        assertEquals(49_449_650, store.state.value.storyRequest?.storyId)

        store.detailRemovedFromBackStack()

        assertEquals(MainDestination.STORY, store.state.value.currentDestination)
        assertEquals(listOf(49_449_677), store.state.value.storyBackStack.map { it.storyId })
        assertEquals(MainDestination.STORIES, store.state.value.storyParentDestination)
        assertEquals(MainDestination.STORIES, store.state.value.storyStackParentDestination)
        assertEquals(49_449_677, store.state.value.storyRequest?.storyId)
    }

    @Test
    fun linkedStoryRetainsSettingsAsTheParentOfTheWholeStoryStack() {
        val store = MainNavigationStore()
        store.openSettings("debug")
        store.openStory(StoryDestination(storyId = 1))
        store.openLinkedStory(StoryDestination(storyId = 2))

        assertEquals(MainDestination.STORY, store.state.value.storyParentDestination)
        assertEquals(MainDestination.SETTINGS, store.state.value.storyStackParentDestination)
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
    fun typedBackStackSupportsNestedDestinationsWithoutOriginFlags() {
        val state = MainNavigationState()
        state.openStory(StoryDestination(storyId = 1))
        state.openSubmissions("simon")
        state.openStory(StoryDestination(storyId = 2))

        assertEquals(
            listOf(
                MainDestination.STORIES,
                MainDestination.STORY,
                MainDestination.SUBMISSIONS,
                MainDestination.STORY,
            ),
            state.destinationStack.map { it.destination },
        )

        state.detailRemovedFromBackStack()
        assertEquals(MainDestination.SUBMISSIONS, state.currentDestination)
        assertEquals(1, state.storyRequest?.storyId)

        state.closeSubmissions()
        assertEquals(MainDestination.STORY, state.currentDestination)
        assertEquals(1, state.storyRequest?.storyId)
    }

    @Test
    fun openingEditorPushesAboveTheCurrentDestination() {
        val state = MainNavigationState()
        state.openSettings("stories")
        state.openSubmissions("simon")
        state.openCoulombGas()

        state.openEditor(EditorDestination(EditorType.POST))

        assertEquals("stories", state.settingsRequest?.initialSectionRoute)
        assertEquals("simon", state.submissionsRequest?.userName)
        assertFalse(state.coulombGasVisible)
        assertEquals(MainDestination.EDITOR, state.currentDestination)
        assertEquals(EditorType.POST, state.editorRequest?.destination?.type)

        state.closeEditor()
        assertEquals(MainDestination.IMMERSIVE, state.currentDestination)
        assertTrue(state.coulombGasVisible)
    }

    @Test
    fun reopeningDestinationsReplacesTheirTopEntry() {
        val state = MainNavigationState()

        state.openStory(StoryDestination(storyId = 1))
        state.openStory(StoryDestination(storyId = 2))
        state.openSettings("stories")
        state.openSettings("comments")
        state.openEditor(EditorDestination(EditorType.POST))
        state.openEditor(EditorDestination(EditorType.COMMENT_REPLY, itemId = 42))
        state.openSubmissions("first")
        state.openSubmissions("second")

        assertEquals(
            listOf(
                MainDestination.STORIES,
                MainDestination.STORY,
                MainDestination.SETTINGS,
                MainDestination.EDITOR,
                MainDestination.SUBMISSIONS,
            ),
            state.destinationStack.map(MainNavigationEntry::destination),
        )
        assertEquals(MainStoryRequest(2, StoryDestination(storyId = 2)), state.storyRequest)
        assertEquals(MainSettingsRequest(2, "comments"), state.settingsRequest)
        assertEquals(
            MainEditorRequest(2, EditorDestination(EditorType.COMMENT_REPLY, itemId = 42)),
            state.editorRequest,
        )
        assertEquals(MainSubmissionsRequest(2, "second"), state.submissionsRequest)
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
