package com.simon.harmonichackernews

import androidx.lifecycle.ViewModel
import com.simon.harmonichackernews.presentation.StoriesSessionState

/** Android lifecycle holder for platform-neutral story session state. */
class StoriesViewModel : ViewModel() {
    val state = StoriesSessionState()
}
