package com.simon.harmonichackernews.ui.submissions

import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.ScreenStateViewModel
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.presentation.SubmissionFilter
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import com.simon.harmonichackernews.presentation.SubmissionsStore
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val algoliaRepository: AlgoliaRepository = NetworkComponent.algoliaRepository,
) {
    fun interface Navigator {
        fun openStory(story: Story, showWebsite: Boolean)
    }

    private val sessionState: SubmissionsSessionState =
        ViewModelProvider(activity)[ScreenStateViewModel::class.java]
            .submissionsStateFor(sessionKey, userName, algoliaRepository)
    private val store: SubmissionsStore = sessionState.submissions
    private var submissions: List<Story> = emptyList()
    private val hackerNewsRepository = NetworkComponent.hackerNewsRepository
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val composeController: SubmissionsComposeController
    private var submissionsLoadJob: Job? = null

    init {
        composeController = SubmissionsComposeController(
            userName = userName,
            initialFilter = store.state.value.filter,
            initialDisplaySettings = StoryDisplaySettings
                .from(AndroidUserSettings(activity).story)
                .withShowIndex(false),
            listener = object : SubmissionsComposeController.Listener {
                override fun onFilterSelected(filter: SubmissionFilter) {
                    store.selectFilter(filter)
                }

                override fun onRefresh() {
                    loadSubmissions(true)
                }

                override fun onStoryLinkClick(story: Story) {
                    if (story.isLink) {
                        if (SettingsUtils.shouldUseIntegratedWebView(activity)) {
                            openComments(story, true)
                        } else {
                            Utils.launchCustomTab(activity, story.url)
                        }
                    } else {
                        openComments(story, false)
                    }
                }

                override fun onStoryCommentsClick(story: Story) {
                    openComments(story, false)
                }

                override fun onCommentStoryClick(story: Story) {
                    openCommentMasterStory(story)
                }

                override fun onCommentRepliesClick(story: Story) {
                    openComments(story, false)
                }

                override fun onLoadMore() {
                    loadSubmissions(false)
                }

                override fun onScrollStateChanged(
                    firstVisibleStoryPosition: Int,
                    firstVisibleStoryTop: Int,
                    appBarCollapsed: Boolean,
                ) {
                    sessionState.firstVisibleStoryPosition = firstVisibleStoryPosition
                    sessionState.firstVisibleStoryTop = firstVisibleStoryTop
                    sessionState.appBarCollapsed = appBarCollapsed
                }
            },
        )
        composeController.updateDisplaySettings(
            StoryDisplaySettings.from(AndroidUserSettings(activity).story).withShowIndex(false)
        )
        if (sessionState.initialized) {
            composeController.restoreScrollState(
                firstVisiblePosition = sessionState.firstVisibleStoryPosition,
                firstVisibleTop = sessionState.firstVisibleStoryTop,
                appBarCollapsed = sessionState.appBarCollapsed,
            )
        } else {
            sessionState.initialized = true
        }
        coroutineScope.launch {
            store.state.collect(::render)
        }
        if (!store.state.value.loadedSuccessfully && !store.state.value.loading) {
            loadSubmissions(true)
        }
    }

    fun close() {
        cancelSubmissionsLoad()
        store.cancelLoad()
        coroutineScope.cancel()
    }

    private fun render(state: SubmissionsUiState) {
        submissions = state.items
        composeController.updateLoading(
            state.loading,
            state.showInitialLoading,
            state.refreshing,
        )
        composeController.updateContent(
            submissions,
            state.filter,
            state.hasUnfilteredItems,
            state.canLoadMore,
            state.loadedSuccessfully,
            state.emptyText,
        )
    }

    private fun openCommentMasterStory(story: Story) {
        val masterStory = story.toCommentMasterStory()
        if (masterStory == null) {
            openComments(story, false)
            return
        }
        if (masterStory.loaded) {
            openComments(masterStory, false)
            return
        }

        coroutineScope.launch {
            try {
                hackerNewsRepository.getStory(masterStory.id)
                    ?.let(story::updateCommentMasterFrom)
                if (submissions.contains(story)) composeController.refreshStoryRows()
                openComments(story.toCommentMasterStory() ?: masterStory, false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
                openComments(masterStory, false)
            }
        }
    }

    private fun openComments(story: Story, showWebsite: Boolean) {
        navigator.openStory(story, showWebsite)
    }

    private fun loadSubmissions(resetResultLimit: Boolean) {
        cancelSubmissionsLoad()
        submissionsLoadJob = coroutineScope.launch {
            if (resetResultLimit) {
                store.refresh()
            } else {
                store.loadMore()
            }
            submissionsLoadJob = null
        }
    }

    private fun cancelSubmissionsLoad() {
        submissionsLoadJob?.cancel()
        submissionsLoadJob = null
    }

}
