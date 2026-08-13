package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.ui.content.StoryHeaderTintPresentation
import com.simon.harmonichackernews.ui.content.storyHeaderTintPresentation

data class CommentsHeaderPresentation(
    val posterTag: String,
    val tint: StoryHeaderTintPresentation,
    val lastRefreshedText: String?,
)

/** Canonical comments-header mapping; hosts inject only locale-aware clock text. */
object CommentsHeaderPresentationFactory {
    fun create(
        story: Story,
        previewResource: StoryPreviewResourceState?,
        faviconProvider: String,
        paletteTintMode: String,
        tintBaseColor: Int,
        tintStore: StoryResourceTintStore,
        userTags: UserTagsRepository,
        lastRefreshedMillis: Long,
        formatTime: (Long) -> String,
    ): CommentsHeaderPresentation = CommentsHeaderPresentation(
        posterTag = userTags.tagFor(story.by),
        tint = storyHeaderTintPresentation(
            story = story,
            previewResource = previewResource,
            faviconProvider = faviconProvider,
            paletteTintMode = paletteTintMode,
            tintBaseColor = tintBaseColor,
            tintStore = tintStore,
        ),
        lastRefreshedText = lastRefreshedMillis.takeIf { it > 0L }?.let {
            PresentationCopy.lastRefreshed(formatTime(it))
        },
    )
}
