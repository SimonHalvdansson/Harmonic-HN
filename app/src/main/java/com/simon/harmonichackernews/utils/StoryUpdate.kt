package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Story

object StoryUpdate {
    var storyUpdateListener: StoryUpdateListener? = null

    fun updateStory(story: Story?) {
        if (storyUpdateListener != null) {
            storyUpdateListener!!.callback(story)
        }
    }

    fun setStoryUpdatedListener(storyUpdateListener: StoryUpdateListener?) {
        StoryUpdate.storyUpdateListener = storyUpdateListener
    }

    fun clearStoryUpdatedListener(storyUpdateListener: StoryUpdateListener?) {
        if (StoryUpdate.storyUpdateListener === storyUpdateListener) {
            StoryUpdate.storyUpdateListener = null
        }
    }

    fun interface StoryUpdateListener {
        fun callback(story: Story?)
    }
}
