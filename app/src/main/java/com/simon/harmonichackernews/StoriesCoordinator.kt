package com.simon.harmonichackernews

import android.content.res.Resources
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.StoriesFeatureHost
import com.simon.harmonichackernews.app.createStoriesStore
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StoriesCoordinator(
    private val activity: MainActivity,
    savedInstanceState: Bundle?,
    private val navigation: MainNavigationController,
    private val appComposition: HarmonicAppComposition = activity.harmonicAppComposition,
    private val userSettings: UserSettings = appComposition.userSettings,
    platformDependencies: StoriesPlatformDependencies =
        appComposition.storiesPlatformDependencies(),
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionState = navigation.scene.sessions.stories
    private var destroyed = false
    var composeController: StoriesComposeController? = null
        private set
    private val storiesStore = appComposition.createStoriesStore(
        StoriesFeatureHost(
            scope = coroutineScope,
            sessionState = sessionState,
            platform = platformDependencies,
            userSettings = userSettings,
        ),
    )
    private var linkSummaryBackCallback: OnBackPressedCallback? = null
    private var hostActive = navigation.currentDestination == MainDestination.STORIES
    private var pendingLinkSummaryStoryId: Int = NO_PENDING_LINK_SUMMARY_STORY_ID

    init {
        initializeComposeController(savedInstanceState)
    }

    private val resources: Resources
        get() = activity.resources

    private fun initializeComposeController(savedInstanceState: Bundle?) {
        if (composeController != null) {
            return
        }
        if (savedInstanceState != null) {
            pendingLinkSummaryStoryId = savedInstanceState.getInt(
                STATE_LINK_SUMMARY_STORY_ID, NO_PENDING_LINK_SUMMARY_STORY_ID
            )
        }

        setupLinkSummaryBackCallback()
        coroutineScope.launch { storiesStore.effects.collect(::handleStoriesRuntimeEffect) }
        coroutineScope.launch {
            storiesStore.state.collect { state ->
                composeController?.updateContent(
                    StoriesScreenStateFactory.create(state, storiesPlatformPresentation(state)),
                )
            }
        }
        storiesStore.start()
        initializeComposeUi()

        restoreLinkSummaryAfterRecreation()
    }

    private fun initializeComposeUi() {
        if (composeController != null) return
        val platformCallbacks = object : StoriesFeatureListener.PlatformCallbacks {
            override fun onSearchStateChanged(searching: Boolean) {
                composeController?.endPredictiveBack()
                syncComposeState()
            }
            override fun showFrontDatePicker() {
                val state = storiesStore.state.value
                composeController?.showFrontDatePicker(
                    state.frontDateSelectedMillis,
                    state.frontDateEarliestMillis,
                    state.frontDateLatestMillis,
                )
            }
            override fun onStoryPreviewVisibilityChanged(showing: Boolean) {
                updateStoryPreviewBackCallback()
            }

            override fun isSplitLayout(): Boolean = isFoldableSplitLayout
        }
        composeController = StoriesComposeController.create(
            defaultStoryHeightPx = (96f * resources.displayMetrics.density).roundToInt(),
            savedItemState = storiesStore.savedItemState,
            listener = StoriesFeatureListener(storiesStore, platformCallbacks),
        )
        navigation.attachStoriesComposeController(composeController!!)
        syncComposeState()
    }

    private fun handleStoriesPlatformEffect(effect: StoriesPlatformEffect) {
        when (effect) {
            StoriesPlatformEffect.OpenSettings ->
                navigation.openSettings(null)
            StoriesPlatformEffect.RequestLogin -> navigation.showLoginDialog()
            is StoriesPlatformEffect.OpenProfile ->
                navigation.showUserDialog(effect.userName, null)
            StoriesPlatformEffect.ShowCacheDialog -> navigation.showCacheStoriesDialog()
            StoriesPlatformEffect.OpenSubmitEditor -> navigation.openEditor(
                EditorDestination(type = EditorType.POST),
            )
        }
        syncComposeState()
    }

    private fun handleStoriesRuntimeEffect(effect: StoriesRuntimeEffect) {
        when (effect) {
            is StoriesRuntimeEffect.OpenStory -> navigation.openStory(effect.destination)
            is StoriesRuntimeEffect.OpenExternalLink ->
                navigation.scene.links.openExternal(ExternalLinkRequest(effect.url))
            is StoriesRuntimeEffect.Platform -> handleStoriesPlatformEffect(effect.effect)
            is StoriesRuntimeEffect.StoryChanged -> {
                val changedStoryId = effect.storyId
                if (changedStoryId == null) syncComposeState()
                else composeController?.invalidateStory(changedStoryId)
            }
            StoriesRuntimeEffect.LoginRequired -> navigation.showLoginDialog()
            is StoriesRuntimeEffect.UserMessage ->
                navigation.showMessage(effect.message)
            is StoriesRuntimeEffect.SavedActionFailed -> {
                if (effect.presentation.showDetails) {
                    navigation.showFailureDetailDialog(
                        effect.presentation.failureSummary,
                        effect.presentation.failureDetail,
                        null,
                    )
                } else {
                    navigation.showMessage(effect.presentation.message)
                }
            }
        }
    }

    private fun syncComposeState() {
        val controller = composeController ?: return
        val state = storiesStore.state.value
        controller.updateContent(
            StoriesScreenStateFactory.create(state, storiesPlatformPresentation(state)),
        )
    }

    private fun storiesPlatformPresentation(state: StoriesState): StoriesPlatformPresentation {
        val lastUpdated = state.lastUpdatedMillis?.let { millis ->
            PresentationCopy.lastUpdated(
                appComposition.platform.timeFormatting.time(millis),
            )
        }
        return StoriesPlatformPresentation(
            lastUpdatedText = lastUpdated,
            contentInsetStartPx = splitStoriesContentPaddingStart,
        )
    }

    private fun restoreLinkSummaryAfterRecreation() {
        if (pendingLinkSummaryStoryId == NO_PENDING_LINK_SUMMARY_STORY_ID) {
            return
        }
        val storyId = pendingLinkSummaryStoryId
        pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID
        activity.window.decorView.post {
            if (composeController == null) {
                return@post
            }
            storiesStore.previewDeck(
                storyId,
                PreviewImageTintUtils.getTintBaseColor(activity),
            )?.let { composeController?.showStoryPreview(it) }
        }
    }

    private val splitStoriesContentPaddingStart: Int
        get() = resources.getDimensionPixelSize(R.dimen.extra_pane_padding)

    private fun setupLinkSummaryBackCallback() {
        if (linkSummaryBackCallback != null) {
            return
        }
        linkSummaryBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackCancelled() {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.cancelStoryPreviewPredictiveBack()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.updateStoryPreviewPredictiveBack(
                        backEvent.progress, backEvent.swipeEdge, backEvent.touchY
                    )
                }
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.startStoryPreviewPredictiveBack(
                        backEvent.progress, backEvent.swipeEdge, backEvent.touchY
                    )
                }
            }

            override fun handleOnBackPressed() {
                if (composeController == null || !composeController!!.isStoryPreviewShowing()) {
                    return
                }
                if (composeController!!.isStoryPreviewPredictiveBackActive()) {
                    composeController!!.commitStoryPreviewPredictiveBack()
                } else {
                    composeController!!.requestDismissStoryPreview()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, linkSummaryBackCallback!!)
    }

    private val isFoldableSplitLayout: Boolean
        get() = !destroyed && navigation.isAdaptiveFoldable()

    fun setHostActive(active: Boolean) {
        hostActive = active
        updateStoryPreviewBackCallback()
    }

    private fun updateStoryPreviewBackCallback() {
        linkSummaryBackCallback?.isEnabled =
            hostActive && composeController?.isStoryPreviewShowing() == true
    }

    fun onStart() {
        storiesStore.onStart()
    }

    fun onStop() {
        storiesStore.onStop()
    }

    fun onResume() {
        storiesStore.onResume()
        syncComposeState()
    }

    fun onSaveInstanceState(outState: Bundle) {
        val visibleStoryId = if (composeController == null)
            NO_PENDING_LINK_SUMMARY_STORY_ID
        else
            composeController!!.visibleStoryPreviewId
        if (visibleStoryId != NO_PENDING_LINK_SUMMARY_STORY_ID) {
            outState.putInt(STATE_LINK_SUMMARY_STORY_ID, visibleStoryId)
        }
    }

    fun onDestroy() {
        if (destroyed) return
        if (composeController != null) {
            composeController!!.completeStoryPreviewDismiss()
        }
        if (linkSummaryBackCallback != null) {
            linkSummaryBackCallback!!.remove()
            linkSummaryBackCallback = null
        }
        storiesStore.close()
        coroutineScope.cancel()
        clearControllerReferences()
        destroyed = true
    }

    private fun clearControllerReferences() {
        val controller = composeController
        if (controller != null) {
            navigation.detachStoriesComposeController(controller)
        }
        composeController = null
    }

    companion object {
        private const val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
        private const val STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID"

    }
}
