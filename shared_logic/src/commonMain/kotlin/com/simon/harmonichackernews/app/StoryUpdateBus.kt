package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Application-scoped feature-to-feature story updates without a platform/global listener. */
class StoryUpdateBus {
    private val mutableUpdates = MutableSharedFlow<Story>(extraBufferCapacity = 32)
    val updates: SharedFlow<Story> = mutableUpdates.asSharedFlow()

    fun publish(story: Story) {
        mutableUpdates.tryEmit(story)
    }
}
