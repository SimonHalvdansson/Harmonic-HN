package com.simon.harmonichackernews.ui.submissions

import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.ui.session.SubmissionsViewModel
import com.simon.harmonichackernews.presentation.SubmissionsFeatureEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Networking and filtering state for a Compose submissions destination.  */
class AndroidSubmissionsCoordinator(
    private val activity: MainActivity,
    private val viewModel: SubmissionsViewModel,
    val userName: String,
    private val navigator: Navigator,
    private val scene: HarmonicSceneComposition,
    private val appComposition: HarmonicAppComposition = activity.harmonicAppComposition,
    private val userSettings: UserSettings = appComposition.userSettings,
) {
    fun interface Navigator {
        fun openStory(destination: StoryDestination)
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = viewModel.store
    val initialScrollRestoration = viewModel.scrollRestoration()
    val displaySettings = StoryDisplaySettings
        .from(userSettings.story)
        .withShowIndex(false)

    init {
        coroutineScope.launch {
            store.effects.collect(::handleEffect)
        }
    }

    fun close() {
        coroutineScope.cancel()
    }

    private fun handleEffect(effect: SubmissionsFeatureEffect) {
        when (effect) {
            is SubmissionsFeatureEffect.OpenStory ->
                navigator.openStory(effect.destination)
            is SubmissionsFeatureEffect.OpenExternalLink ->
                scene.links.openExternal(ExternalLinkRequest(effect.url))
        }
    }
}
