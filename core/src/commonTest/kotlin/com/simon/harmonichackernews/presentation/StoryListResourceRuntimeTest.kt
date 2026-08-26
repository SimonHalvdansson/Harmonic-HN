package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CachedStoryPreviewResource
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.PreviewContent
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryListResourceRuntimeTest {
    @Test
    fun eitherListEnrichmentRequestsAndRetainsBothParsedValues() = runTest {
        listOf(
            settings(previewImageMode = StoryPreviewPreferences.SMALL, showSummary = false),
            settings(previewImageMode = StoryPreviewPreferences.OFF, showSummary = true),
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
                previewImageMode = StoryPreviewPreferences.OFF,
                showSummary = false,
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
        previewImageMode: String,
        showSummary: Boolean,
    ) = StoryDisplaySettings(
        showPoints = true,
        compactPoints = false,
        includeTopLevelDomain = true,
        showCommentsCount = true,
        compactView = false,
        thumbnails = true,
        previewImageMode = previewImageMode,
        borderlessLargePreviewImage = false,
        showSummary = showSummary,
        storyTextSize = 16f,
        showIndex = true,
        compactHeader = false,
        leftAlign = false,
        cardStyle = true,
        tintCardUsingPreview = false,
        paletteTintMode = "default",
        grayOutClicked = true,
        hotness = 0,
        faviconProvider = "",
        font = "default",
        commentTextSize = 16f,
    )
}
