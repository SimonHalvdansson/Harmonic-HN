package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.network.CachedStoryPreviewResource
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.PreviewContent
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.settings.StoryPreviewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryListResourceRuntimeTest {
    @Test
    fun queuedPrefetchFromAnObsoleteFeedCannotRepopulatePrunedState() = runTest {
        val requests = mutableListOf<StoryPreviewResourceRequest>()
        val runtime = StoryListResourceRuntime(backgroundScope, recordingService(requests),
            settings(StoryPreviewMode.SMALL, false))
        val old = story()
        runtime.request(old)
        runCurrent()
        runtime.retainStories(emptyList(), emptyList())
        runtime.prefetchStory(old, listOf(old))
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(runtime.states().isEmpty())
        runtime.dispose()
    }

    @Test
    fun pagingCancelsOnlyObsoleteDialogLoadsAndKeepsSharedListRequests() = runTest {
        val running = mutableSetOf<Int>()
        val runtime = StoryListResourceRuntime(backgroundScope, object : StoryPreviewResourceService {
            override suspend fun readCached(request: StoryPreviewResourceRequest) = CachedStoryPreviewResource(false, null, null)
            override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                running += request.storyId
                try { awaitCancellation() } finally { running -= request.storyId }
            }
        }, settings(StoryPreviewMode.SMALL, false))
        val stories = (1..20).map { id -> story().apply { this.id = id } }
        runtime.request(stories[4]) // Story 5 is shared with the list, so dialog paging must retain it.
        runtime.openDialog(stories, 5)
        runCurrent()
        assertEquals(setOf(4, 5, 6), running)
        runtime.requestDialogWindow(15)
        runCurrent()
        assertEquals(setOf(5, 14, 15, 16), running)
        runtime.closeDialog()
        runCurrent()
        // With no retained feed, pruning also removes the last list request.
        assertTrue(running.isEmpty())
        runtime.dispose()
    }

    @Test
    fun dialogLoadsNeighboursAsItPagesAndPinsItsResourcesUntilDismissal() = runTest {
        val requests = mutableListOf<StoryPreviewResourceRequest>()
        val runtime = StoryListResourceRuntime(backgroundScope, recordingService(requests),
            settings(StoryPreviewMode.OFF, false))
        val stories = (1..500).map { id -> story().apply { this.id = id } }
        fun snapshots(source: List<Story>) = source.map { StoryListItemSnapshot(it.toSnapshot(), it.presentationSnapshot()) }
        runtime.retainStories(snapshots(stories), emptyList())
        runtime.openDialog(stories, 250)
        runCurrent()
        assertEquals(listOf(250, 251, 249), requests.map { it.storyId })
        runtime.requestDialogWindow(251)
        runCurrent()
        assertEquals(listOf(250, 251, 249, 252), requests.map { it.storyId })
        runtime.retainStories(snapshots(stories.take(1)), emptyList())
        assertEquals(setOf(249, 250, 251, 252), runtime.states().keys)
        runtime.closeDialog()
        assertTrue(runtime.states().isEmpty())
        runtime.requestDialogWindow(250)
        runCurrent()
        assertEquals(4, requests.size)
        runtime.dispose()
    }

    @Test
    fun searchAndMainResourcesSurviveUntilBothListsReleaseThem() = runTest {
        val runtime = StoryListResourceRuntime(backgroundScope, recordingService(mutableListOf()),
            settings(StoryPreviewMode.SMALL, false))
        val main = story()
        val search = story().apply { id = 43 }
        fun snapshot(story: Story) = listOf(StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot()))
        runtime.retainStories(snapshot(main), snapshot(search))
        runtime.request(main)
        runtime.request(search)
        runCurrent()
        runtime.retainStories(emptyList(), snapshot(search))
        assertEquals(setOf(43), runtime.states().keys)
        runtime.retainStories(emptyList(), emptyList())
        assertTrue(runtime.states().isEmpty())
        runtime.dispose()
    }

    @Test
    fun eitherListEnrichmentRequestsAndRetainsBothParsedValues() = runTest {
        listOf(
            settings(previewImageMode = StoryPreviewMode.SMALL, showPreviewText = false),
            settings(previewImageMode = StoryPreviewMode.OFF, showPreviewText = true),
        ).forEach { settings ->
            val requests = mutableListOf<StoryPreviewResourceRequest>()
            val runtime = StoryListResourceRuntime(
                scope = backgroundScope,
                service = recordingService(requests),
                settings = settings,
            )

            runtime.request(story())
            runCurrent()

            assertEquals(1, requests.size)
            assertTrue(requests.single().loadImage)
            assertTrue(requests.single().loadSummary)
            runtime.dispose()
        }
    }

    @Test
    fun dialogRequestLoadsBothValuesWhenListEnrichmentIsDisabled() = runTest {
        val requests = mutableListOf<StoryPreviewResourceRequest>()
        val runtime = StoryListResourceRuntime(
            scope = backgroundScope,
            service = recordingService(requests),
            settings = settings(
                previewImageMode = StoryPreviewMode.OFF,
                showPreviewText = false,
            ),
        )
        val story = story()

        runtime.request(story)
        runCurrent()
        assertTrue(requests.isEmpty())

        runtime.requestForDialog(story)
        runCurrent()

        assertEquals(1, requests.size)
        assertTrue(requests.single().loadImage)
        assertTrue(requests.single().loadSummary)
        runtime.dispose()
    }

    @Test
    fun recordedTintLivesInResourceStateWithoutMutatingCachedStoryFields() = runTest {
        val runtime = StoryListResourceRuntime(
            scope = backgroundScope,
            service = recordingService(mutableListOf()),
            settings = settings(
                previewImageMode = StoryPreviewMode.SMALL,
                showPreviewText = false,
            ),
        )
        val story = story()

        runtime.request(story)
        runCurrent()

        assertTrue(
            runtime.recordTint(
                story = story,
                kind = StoryResourceTintKind.PREVIEW_IMAGE,
                sourceUrl = "https://example.com/image.png",
                baseColorArgb = 0xff101010.toInt(),
                paletteConfigKey = "default",
                tintColorArgb = 0xff202020.toInt(),
            ),
        )
        assertEquals(0xff202020.toInt(), runtime.stateFor(story.id)?.previewTint?.tintColorArgb)
        assertFalse(story.previewImageTintColorLoaded)
        assertEquals(0, story.previewImageTintColor)
        runtime.dispose()
    }

    private fun recordingService(
        requests: MutableList<StoryPreviewResourceRequest>,
    ) = object : StoryPreviewResourceService {
        override suspend fun readCached(
            request: StoryPreviewResourceRequest,
        ): CachedStoryPreviewResource {
            requests += request
            return CachedStoryPreviewResource(
                imageUrlResolved = true,
                imageUrl = "https://example.com/image.png",
                summary = LinkSummary(description = "Cached summary"),
            )
        }

        override suspend fun load(request: StoryPreviewResourceRequest) =
            PreviewContent(null, null)
    }

    private fun story() = Story().apply {
        id = 42
        title = "Story"
        url = "https://example.com/article"
        loaded = true
        isLink = true
    }

    private fun settings(
        previewImageMode: StoryPreviewMode,
        showPreviewText: Boolean,
    ) = StoryDisplaySettings(
        showPoints = true,
        compactPoints = false,
        includeTopLevelDomain = true,
        showCommentsCount = true,
        compactView = false,
        showFavicons = true,
        previewImageMode = previewImageMode,
        borderlessLargePreviewImage = false,
        showPreviewText = showPreviewText,
        storyTextSize = 16f,
        showIndex = true,
        compactHeader = false,
        commentsButtonOnLeft = false,
        displayStyle = DisplayStyle.RAISED,
        tintCardsFromImages = false,
        paletteTintMode = "default",
        dimReadStories = true,
        hotnessThreshold = 0,
        faviconProvider = "",
        font = "default",
        commentTextSize = 16f,
    )
}
