package com.simon.harmonichackernews

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.simon.harmonichackernews.presentation.StoriesSessionState

/** Android lifecycle handle for the application composition's platform-neutral story session. */
class StoriesViewModel(application: Application) : AndroidViewModel(application) {
    val state: StoriesSessionState = application.harmonicAppComposition.sessions.stories
}
