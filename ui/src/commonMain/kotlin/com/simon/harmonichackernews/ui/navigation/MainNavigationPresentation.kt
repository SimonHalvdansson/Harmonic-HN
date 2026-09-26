package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.navigation.mainStoryStack

/**
 * The stack determines what is underneath each surface, even while another destination covers it.
 * Hosts supply window facts, never a separate Stories-visibility decision.
 */
@ConsistentCopyVisibility
data class MainNavigationScenePlan internal constructor(
    val baseUsesTwoPane: Boolean,
    val storyUsesTwoPane: Boolean,
    val submissionsInTwoPane: Boolean,
    val baseStoryRequest: MainStoryRequest?,
    val submissionsStoryRequest: MainStoryRequest?,
    val submissionsScenes: List<MainSubmissionsScene>,
    val fullScreenStories: List<MainStoryRequest>,
    val showsStoriesRoot: Boolean,
    val animateInitialStory: Boolean,
)

data class MainSubmissionsScene(
    val request: MainSubmissionsRequest,
    val storyRequest: MainStoryRequest?,
)

fun mainNavigationScenePlan(
    navigation: MainNavigationSnapshot,
    isTwoPane: Boolean,
): MainNavigationScenePlan {
    val storyStack = navigation.storyStack
    val submissionsInTwoPane = isTwoPane && navigation.submissionsRequest != null
    val submissionsIndices = if (submissionsInTwoPane) {
        navigation.destinationStack.indices.filter {
            navigation.destinationStack[it] is MainNavigationEntry.Submissions
        }
    } else emptyList()
    val submissionsScenes = submissionsIndices.mapIndexed { index, start ->
        val end = submissionsIndices.getOrNull(index + 1) ?: navigation.destinationStack.size
        val ownedStories = mainStoryStack(navigation.destinationStack.subList(start, end))
        MainSubmissionsScene(
            request = (navigation.destinationStack[start] as MainNavigationEntry.Submissions).request,
            storyRequest = ownedStories.requests.lastOrNull().takeIf {
                ownedStories.parent == MainDestination.SUBMISSIONS
            },
        )
    }
    // Submissions owns a separate list/detail scene. Opening it must not re-layout the
    // retained scene it will reveal on Back, including a full-screen story opened from Debug.
    val baseStack = if (submissionsInTwoPane) {
        // Each visit retains its own detail. The base ends at the first Submissions visit;
        // a nested visit must never move an earlier detail into the base as well.
        mainStoryStack(navigation.destinationStack.take(submissionsIndices.first()))
    } else {
        storyStack
    }
    val baseStory = baseStack.requests.lastOrNull()
    val baseUsesTwoPane = isTwoPane &&
        (baseStory == null || baseStack.immediateParent == MainDestination.STORIES)
    return MainNavigationScenePlan(
        baseUsesTwoPane = baseUsesTwoPane,
        storyUsesTwoPane = isTwoPane && (
            storyStack.immediateParent == MainDestination.STORIES ||
                storyStack.parent == MainDestination.SUBMISSIONS
            ),
        submissionsInTwoPane = submissionsInTwoPane,
        baseStoryRequest = baseStory,
        submissionsStoryRequest = submissionsScenes.lastOrNull()?.storyRequest,
        submissionsScenes = submissionsScenes,
        fullScreenStories = if (baseUsesTwoPane) emptyList() else baseStack.requests,
        showsStoriesRoot = baseStack.requests.isEmpty() || baseStack.parent == MainDestination.STORIES,
        animateInitialStory = isTwoPane && baseStory != null && !baseUsesTwoPane,
    )
}

/** The scene switch and the parent layers share the outgoing surfaces' actual lifetime. */
class MainNavigationPresentation internal constructor(
    val scene: MainNavigationScenePlan,
    val renderTwoPaneStoryScene: Boolean,
    val storyExitInProgress: Boolean,
    val onStoryLayersEmpty: () -> Unit,
)

@Composable
fun rememberMainNavigationPresentation(
    navigation: MainNavigationSnapshot,
    isTwoPane: Boolean,
    completedStoryPredictiveBack: Boolean,
): MainNavigationPresentation {
    val scene = remember(navigation, isTwoPane) { mainNavigationScenePlan(navigation, isTwoPane) }
    var retainedStoryKeys by remember { mutableStateOf(emptySet<Int>()) }
    val storyKeys = scene.fullScreenStories.map { it.serial }.toSet()
    // Popping the final story above an overlay can expose an older story run below it.
    // Drain the outgoing run before restoring that older one, exactly as when no stories
    // remain. Otherwise the compositor treats it as a replacement and drops the exit.
    val returnsToOlderRun = navigation.currentDestination != MainDestination.STORY &&
        storyKeys.isNotEmpty() && storyKeys.none { it in retainedStoryKeys }
    val exiting = retainedStoryKeys.isNotEmpty() &&
        (storyKeys.isEmpty() || returnsToOlderRun) && !completedStoryPredictiveBack
    SideEffect {
        if (!exiting) retainedStoryKeys = storyKeys
    }
    return MainNavigationPresentation(
        scene = if (exiting) scene.copy(fullScreenStories = emptyList()) else scene,
        renderTwoPaneStoryScene = scene.baseUsesTwoPane && !exiting,
        storyExitInProgress = exiting,
        onStoryLayersEmpty = { retainedStoryKeys = emptySet() },
    )
}
