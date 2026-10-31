package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
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

        val model = createBoth(
            story = story,
            position = 4,
            resources = StoryRowResourcePresentation(
                previewText = "Link summary",
                faviconUrl = "https://news.example.com/favicon.ico",
                previewImageUrl = "https://news.example.com/image.png",
                previewImageLoadFailed = true,
                faviconTintArgb = 0x00445566,
                previewImageTintArgb = 0x00112233,
                tintFallbackArgb = 0x00778899,
            ),
        )

        assertEquals("5.", model.index)
        assertEquals("A story", model.title)
        assertEquals("Link summary", model.previewText)
        assertEquals(story.getDisplayDomain(true), model.domain)
        assertEquals(story.getDisplayDomain(false), model.domainWithoutTopLevel)
        assertEquals(17, model.points)
        assertEquals(3, model.commentCount)
        assertEquals(ItemTimeFormatter.format(story.createdAtEpochSeconds, TEST_NOW_MILLIS), model.age)
        assertEquals("https://news.example.com/favicon.ico", model.faviconUrl)
        assertEquals("https://news.example.com/image.png", model.previewImageUrl)
        assertTrue(model.previewImageLoadFailed)
        assertEquals(0x00445566, model.faviconTintArgb)
        assertEquals(0x00112233, model.previewImageTintArgb)
        assertEquals(0x00778899, model.tintFallbackArgb)
    }

    @Test
    fun loadingAndFailureTitlesAreSuppliedByThePlatform() {
        val story = Story().apply { id = 1 }

        assertEquals(
            "Loading item",
            createBoth(story, loadingTitle = "Loading item").title,
        )
        story.loadingFailed = true
        assertEquals(
            "Retry item",
            createBoth(story, failedTitle = "Retry item").title,
        )
        story.title = ""
        assertEquals("", createBoth(story).title)
        story.title = "Loaded title"
        assertEquals("Loaded title", createBoth(story).title)
    }

    @Test
    fun usesPreResolvedDomainAndSharedModelTimestamp() {
        val story = Story("Cached presentation", 7, true, false).apply {
            url = "https://network.example/should-not-be-used"
            createdAtEpochSeconds = 3_600
        }
        val nowMillis = 7_200_000L

        val model = createBoth(
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

        val pdfModel = createBoth(pdfStory)
        val videoModel = createBoth(videoStory)

        assertEquals("A paper", pdfModel.title)
        assertEquals(StoryTitleBadge.PDF, pdfModel.titleBadge)
        assertEquals("A talk", videoModel.title)
        assertEquals(StoryTitleBadge.VIDEO, videoModel.titleBadge)
    }

    @Test
    fun previewTextKeepsResourceThenLinkThenAiPrecedenceIncludingEmptyValues() {
        val story = Story("A story", 42, true, false).apply {
            linkSummaryDescription = "Link summary"
            aiSummaryText = "AI summary"
        }
        assertEquals("Resource summary", createBoth(
            story, resources = StoryRowResourcePresentation(previewText = "Resource summary"),
        ).previewText)
        assertEquals("", createBoth(
            story, resources = StoryRowResourcePresentation(previewText = ""),
        ).previewText)
        assertEquals("Link summary", createBoth(story).previewText)
        story.linkSummaryDescription = ""
        assertEquals("", createBoth(story).previewText)
        story.linkSummaryDescription = null
        assertEquals("AI summary", createBoth(story).previewText)
        story.aiSummaryText = null
        assertEquals("", createBoth(story).previewText)
    }

    @Test
    fun missingAndInvalidUrlsKeepEmptyDomainsAndRespectExplicitOverrides() {
        val story = Story("A story", 42, true, false)
        for (url in listOf(null, "https://%")) {
            story.url = url
            val model = createBoth(story)
            assertEquals("", model.domain)
            assertEquals("", model.domainWithoutTopLevel)
            assertEquals("", model.index)
            assertNull(model.titleBadge)
        }
        story.url = "https://www.example.com/article"
        assertEquals("", createBoth(story, resolvedDomain = "").domain)
        assertEquals("override.example.com", createBoth(story, resolvedDomain = "override.example.com").domain)
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

    private fun createBoth(
        story: Story,
        position: Int? = null,
        resources: StoryRowResourcePresentation = StoryRowResourcePresentation(),
        loadingTitle: String = "Loading…",
        failedTitle: String = "Tap to retry",
        resolvedDomain: String? = null,
        nowMillis: Long = TEST_NOW_MILLIS,
    ): StoryRowModel {
        val model = StoryRowModelFactory.create(
            story, position, resources, loadingTitle, failedTitle, resolvedDomain, nowMillis,
        )
        val snapshotModel = StoryRowModelFactory.create(
            StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot()),
            position, resources, loadingTitle, failedTitle, resolvedDomain, nowMillis,
        )
        assertEquals(model, snapshotModel, "Both story inputs should produce the same row")
        return model
    }

    private companion object {
        const val TEST_NOW_MILLIS = 7_200_000L
    }
}
