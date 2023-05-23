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

    public interface StoryUpdateListener {
        void callback(Story story);
    }

}
