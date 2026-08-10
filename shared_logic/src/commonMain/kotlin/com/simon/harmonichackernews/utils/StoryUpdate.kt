package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Story
import kotlin.concurrent.Volatile

object StoryUpdate {
    @Volatile
    private var storyUpdateListener: StoryUpdateListener? = null

    fun updateStory(story: Story?) {
        storyUpdateListener?.callback(story)
    }

    fun setStoryUpdatedListener(storyUpdateListener: StoryUpdateListener?) {
        this.storyUpdateListener = storyUpdateListener
    }

    fun clearStoryUpdatedListener(storyUpdateListener: StoryUpdateListener?) {
        if (this.storyUpdateListener === storyUpdateListener) {
            this.storyUpdateListener = null
        }
    }

    fun interface StoryUpdateListener {
        fun callback(story: Story?)
    }
}
