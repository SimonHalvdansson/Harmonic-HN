package com.simon.harmonichackernews.utils;

import com.simon.harmonichackernews.data.Story;

public class StoryUpdate {

    public static StoryUpdateListener storyUpdateListener;

    public static void updateStory(Story story) {
        if (storyUpdateListener != null) {
            storyUpdateListener.callback(story);
        }
    }

    public static void setStoryUpdatedListener(StoryUpdateListener storyUpdateListener) {
        StoryUpdate.storyUpdateListener = storyUpdateListener;
    }

    public static void clearStoryUpdatedListener(StoryUpdateListener storyUpdateListener) {
        if (StoryUpdate.storyUpdateListener == storyUpdateListener) {
            StoryUpdate.storyUpdateListener = null;
        }
    }

    public interface StoryUpdateListener {
        void callback(Story story);
    }

}
