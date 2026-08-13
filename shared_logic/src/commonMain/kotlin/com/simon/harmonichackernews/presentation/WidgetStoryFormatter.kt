package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.utils.DomainNamePolicy

data class WidgetStoryText(
    val index: String,
    val title: String,
    val metadata: String,
)

/** Text policy shared by Android widgets and future desktop/iOS compact story surfaces. */
object WidgetStoryFormatter {
    fun format(
        story: StorySnapshot,
        position: Int,
        includeTopLevelDomain: Boolean,
        nowMillis: Long,
    ): WidgetStoryText {
        val score = "${story.score} ${if (story.score == 1) "pt" else "pts"}"
        val domain = story.url
            ?.takeIf { it.isNotBlank() && !story.isComment }
            ?.let(DomainNamePolicy::fromUrl)
            ?.let { DomainNamePolicy.formatForDisplay(it, includeTopLevelDomain) }
            ?.takeIf(String::isNotBlank)
        val age = ItemTimeFormatter.format(story.createdAtEpochSeconds, nowMillis)
        return WidgetStoryText(
            index = "${position + 1}.",
            title = story.title.orEmpty(),
            metadata = listOfNotNull(score, domain, age).joinToString(" · "),
        )
    }
}
