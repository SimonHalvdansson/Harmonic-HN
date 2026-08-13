package com.simon.harmonichackernews.ui.submissions

import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.ScreenStateViewModel
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.createSubmissionsFeatureSession
import com.simon.harmonichackernews.ui.session.SubmissionsScreenSession
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.SubmissionsPlatformDependencies
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Networking and filtering state for a Compose submissions destination.  */
class SubmissionsCoordinator(
    private val activity: MainActivity,
    sessionKey: Int,
    private val userName: String,
    private val navigator: Navigator,
    private val appComposition: HarmonicAppComposition = activity.harmonicAppComposition,
    private val algoliaRepository: AlgoliaRepository = appComposition.network.algoliaRepository,
    private val userSettings: UserSettings = appComposition.userSettings,
    private val platformDependencies: SubmissionsPlatformDependencies =
        appComposition.submissionsPlatformDependencies(),
) {
    fun interface Navigator {
        fun openStory(destination: StoryDestination)
    }

    private val sessionState: SubmissionsSessionState =
        ViewModelProvider(activity)[ScreenStateViewModel::class.java]
            .submissionsStateFor(sessionKey, userName, algoliaRepository)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val featureSession = appComposition.createSubmissionsFeatureSession(
        scope = coroutineScope,
        sessionState = sessionState,
        userSettings = userSettings,
    )
    private val screenSession = SubmissionsScreenSession(coroutineScope, featureSession)
    val composeController: SubmissionsComposeController

    init {
        composeController = screenSession.createController(
            userName = userName,
            displaySettings = StoryDisplaySettings
                .from(userSettings.story)
                .withShowIndex(false),
        )
        coroutineScope.launch {
            screenSession.effects.collect(::handleEffect)
        }
    }

    fun close() {
        screenSession.dispose()
        coroutineScope.cancel()
    }

    private fun handleEffect(effect: SubmissionsRuntimeEffect) {
        when (effect) {
            is SubmissionsRuntimeEffect.OpenStory ->
                navigator.openStory(effect.destination)
            is SubmissionsRuntimeEffect.OpenExternalLink ->
                platformDependencies.externalLinks.open(ExternalLinkRequest(effect.url))
        }
    }
}
