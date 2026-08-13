package com.simon.harmonichackernews.ui.submissions

import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.ScreenStateViewModel
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.createSubmissionsFeatureSession
import com.simon.harmonichackernews.app.SubmissionsFeatureSessionEvent
import com.simon.harmonichackernews.ui.session.SubmissionsScreenSession
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.SubmissionsPlatformDependencies
import com.simon.harmonichackernews.presentation.SubmissionFilter
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import com.simon.harmonichackernews.presentation.SubmissionsUiState
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
    private val runtime = featureSession.runtime
    private val screenSession = SubmissionsScreenSession(coroutineScope, featureSession)
    val composeController: SubmissionsComposeController

    init {
        composeController = SubmissionsComposeController(
            userName = userName,
            initialFilter = runtime.state.value.filter,
            initialDisplaySettings = StoryDisplaySettings
                .from(userSettings.story)
                .withShowIndex(false),
            listener = object : SubmissionsComposeController.Listener {
                override fun onFilterSelected(filter: SubmissionFilter) {
                    runtime.selectFilter(filter)
                }

                override fun onRefresh() {
                    runtime.refresh()
                }

                override fun onStoryLinkClick(story: Story) = runtime.openStoryLink(story)

                override fun onStoryCommentsClick(story: Story) = runtime.openStoryComments(story)

                override fun onCommentStoryClick(story: Story) = runtime.openCommentMaster(story)

                override fun onCommentRepliesClick(story: Story) = runtime.openCommentReplies(story)

                override fun onLoadMore() {
                    runtime.loadMore()
                }

                override fun onScrollStateChanged(
                    firstVisibleStoryPosition: Int,
                    firstVisibleStoryTop: Int,
                    appBarCollapsed: Boolean,
                ) {
                    runtime.recordScrollPosition(
                        firstVisibleStoryPosition,
                        firstVisibleStoryTop,
                        appBarCollapsed,
                    )
                }
            },
        )
        composeController.updateDisplaySettings(
            StoryDisplaySettings.from(userSettings.story).withShowIndex(false)
        )
        coroutineScope.launch {
            screenSession.state.collect(::render)
        }
        coroutineScope.launch {
            screenSession.effects.collect(::handleEffect)
        }
        screenSession.start()?.let { restoration ->
            composeController.restoreScrollState(
                firstVisiblePosition = restoration.firstVisibleStoryPosition,
                firstVisibleTop = restoration.firstVisibleStoryTop,
                appBarCollapsed = restoration.appBarCollapsed,
            )
        }
    }

    fun close() {
        screenSession.dispose()
        coroutineScope.cancel()
    }

    private fun render(state: SubmissionsUiState) {
        composeController.updateLoading(
            state.loading,
            state.showInitialLoading,
            state.refreshing,
        )
        composeController.updateContent(
            state.items,
            state.filter,
            state.hasUnfilteredItems,
            state.canLoadMore,
            state.loadedSuccessfully,
            state.emptyText,
            state.revision,
        )
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
