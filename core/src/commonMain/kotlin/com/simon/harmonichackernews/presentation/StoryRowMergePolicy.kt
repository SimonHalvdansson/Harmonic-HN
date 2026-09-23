package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story

object StoryRowMergePolicy {
    fun mergeSummaryFields(
        target: Story,
        source: Story,
    ): Boolean {
        if (target.id != source.id) return false
        val changed = target.title != source.title ||
            target.descendants != source.descendants ||
            target.score != source.score ||
            target.createdAtEpochSeconds != source.createdAtEpochSeconds ||
            target.url != source.url
        if (changed) {
            target.title = source.title
            target.descendants = source.descendants
            target.score = source.score
            target.createdAtEpochSeconds = source.createdAtEpochSeconds
            target.url = source.url
        }
        return changed
    }
}
