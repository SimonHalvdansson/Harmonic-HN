package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryRowModelFactoryTest {
    @Test
    fun resolvedAbsenceDoesNotRestoreTheOldRowImage() {
        val resource = StoryPreviewResourceState(1, "https://example.com", imageUrlResolved = true)
        val old = StoryRowResourcePresentation(previewImageUrl = "https://example.com/old.png")
        assertNull(old.withPreviewResource(resource).previewImageUrl)
        assertEquals(old.previewImageUrl, old.withPreviewResource(resource.copy(imageUrlResolved = false)).previewImageUrl)
    }

    @Test
    fun mapsPortableStoryFieldsAndResourceSnapshot() {
        val story = Story("A story", 42, true, false).apply {
            url = "https://news.example.com/article"
            score = 17
            descendants = 3
            aiSummaryText = "Generated summary"
        }

        val model = StoryRowModelFactory.create(
            story = story,
            position = 4,
            resources = StoryRowResourcePresentation(
                previewText = "Link summary",
                faviconUrl = "https://news.example.com/favicon.ico",
                previewImageUrl = "https://news.example.com/image.png",
                previewImageTintArgb = 0x00112233,
            ),
        )

        assertEquals("5.", model.index)
        assertEquals("A story", model.title)
        assertEquals("Link summary", model.previewText)
        assertEquals(story.getDisplayDomain(true), model.domain)
        assertEquals(story.getDisplayDomain(false), model.domainWithoutTopLevel)
        assertEquals(17, model.points)
        assertEquals(3, model.commentCount)
        assertEquals(story.timeFormatted, model.age)
        assertEquals("https://news.example.com/image.png", model.previewImageUrl)
        assertEquals(0x00112233, model.previewImageTintArgb)
    }

    @Test
    fun loadingAndFailureTitlesAreSuppliedByThePlatform() {
        val story = Story().apply { id = 1 }

        assertEquals(
            "Loading item",
            StoryRowModelFactory.create(story, loadingTitle = "Loading item").title,
        )
        story.loadingFailed = true
        assertEquals(
            "Retry item",
            StoryRowModelFactory.create(story, failedTitle = "Retry item").title,
        )
    }

    @Test
    fun usesPreResolvedDomainAndSharedModelTimestamp() {
        val story = Story("Cached presentation", 7, true, false).apply {
            url = "https://network.example/should-not-be-used"
            createdAtEpochSeconds = 3_600
        }
        val nowMillis = 7_200_000L

        val model = StoryRowModelFactory.create(
            story = story,
            resolvedDomain = "cached.example.com",
            nowMillis = nowMillis,
        )

        assertEquals("cached.example.com", model.domain)
        assertEquals("cached.example", model.domainWithoutTopLevel)
        assertEquals(ItemTimeFormatter.format(story.createdAtEpochSeconds, nowMillis), model.age)
    }

    @Test
    fun replacesPdfAndVideoTitleSuffixesWithBadges() {
        val pdfStory = Story("A paper [pdf]", 1, true, false).apply {
            pdfTitle = "A paper"
        }
        val videoStory = Story("A talk [video]", 2, true, false).apply {
            videoTitle = "A talk"
        }

        val pdfModel = StoryRowModelFactory.create(pdfStory)
        val videoModel = StoryRowModelFactory.create(videoStory)

        assertEquals("A paper", pdfModel.title)
        assertEquals(StoryTitleBadge.PDF, pdfModel.titleBadge)
        assertEquals("A talk", videoModel.title)
        assertEquals(StoryTitleBadge.VIDEO, videoModel.titleBadge)
    }

    @Test
    fun keyedPreviewResourceOverridesLegacyRowResourceFieldsImmutably() {
        val legacy = StoryRowResourcePresentation(
            previewText = "Old summary",
            previewImageUrl = "https://old.example/image.png",
        )

        val projected = legacy.withPreviewResource(
            StoryPreviewResourceState(
                storyId = 42,
                pageUrl = "https://example.com/story",
                summary = LinkSummary(description = "Fresh summary"),
                summaryResolved = true,
                imageUrl = "https://example.com/image.png",
                imageUrlResolved = true,
                imageLoadFailed = true,
            ),
        )

        assertEquals("Old summary", legacy.previewText)
        assertEquals("Fresh summary", projected.previewText)
        assertEquals("https://example.com/image.png", projected.previewImageUrl)
        assertTrue(projected.previewImageLoadFailed)
    }
}
