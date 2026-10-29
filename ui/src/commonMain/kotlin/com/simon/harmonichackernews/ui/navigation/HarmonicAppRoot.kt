package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.ui.common.LocalStatusBarScrollToTopEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainEditorRequest
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainSettingsRequest
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.navigation.MainWelcomeRequest

/**
 * One owner for ordering, retention, input isolation and transition completion on every host.
 * Screens receive their own retained request, never a global "last request" from another visit.
 */
@Composable
fun HarmonicAppRoot(
    plan: MainNavigationScenePlan,
    stories: @Composable (MainStoryRequest?, @Composable (MainStoryRequest) -> Unit) -> Unit,
    comments: @Composable (MainStoryRequest, Boolean) -> Unit,
    settings: @Composable (MainSettingsRequest) -> Unit,
    submissions: @Composable (MainSubmissionsRequest, MainStoryRequest?, @Composable (MainStoryRequest) -> Unit) -> Unit,
    editor: @Composable (MainEditorRequest) -> Unit,
    immersive: @Composable () -> Unit,
    welcome: @Composable (MainWelcomeRequest) -> Unit,
    foreground: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    preview: MainNavigationBackPreview? = null,
    completedPredictiveBack: Set<MainNavigationSurfaceKey> = emptySet(),
    linkPreview: (@Composable () -> Unit)? = null,
) {
    val scopes = rememberNavigationEntryScopes(plan.surfaces.flatMap { surface ->
        listOfNotNull(surface.key, surface.detail?.let {
            MainNavigationSurfaceKey(MainDestination.STORY, it.serial)
        })
    })
    val currentComments by rememberUpdatedState(comments)
    val storyContent = remember {
        mutableMapOf<Int, @Composable (MainStoryRequest, Boolean) -> Unit>()
    }
    // A story owns its composition even when the window moves it between a pane and a
    // full-screen surface. Moving the content preserves native views and local scroll state.
    val renderStory: @Composable (MainStoryRequest, Boolean) -> Unit = { request, fullScreen ->
        val content = storyContent.getOrPut(request.serial) {
            movableContentOf { entry: MainStoryRequest, full: Boolean ->
                DisposableEffect(entry.serial) {
                    onDispose { storyContent.remove(entry.serial) }
                }
                scopes.Content(MainNavigationSurfaceKey(MainDestination.STORY, entry.serial)) {
                    currentComments(entry, full)
                }
            }
        }
        content(request, fullScreen)
    }
    val paneComments: @Composable (MainStoryRequest) -> Unit = { renderStory(it, false) }
    val render: @Composable (MainNavigationSurface) -> Unit = { surface ->
        CompositionLocalProvider(
            LocalSplitPaneAnimationEnabled provides (surface.key == plan.current.key),
            LocalStatusBarScrollToTopEnabled provides (surface.key == plan.current.key),
        ) {
            when (val entry = surface.entry) {
                MainNavigationEntry.Stories -> scopes.Content(surface.key) { stories(surface.detail, paneComments) }
                is MainNavigationEntry.Story -> renderStory(entry.request, true)
                is MainNavigationEntry.Settings -> scopes.Content(surface.key) { settings(entry.request) }
                is MainNavigationEntry.Submissions -> scopes.Content(surface.key) { submissions(entry.request, surface.detail, paneComments) }
                is MainNavigationEntry.Editor -> scopes.Content(surface.key) { editor(entry.request) }
                MainNavigationEntry.Immersive -> scopes.Content(surface.key) { immersive() }
                is MainNavigationEntry.Welcome -> scopes.Content(surface.key) { welcome(entry.request) }
            }
        }
    }
    val root = plan.surfaces.first()
    Box(modifier.fillMaxSize()) {
        key(root.key) {
            ActivityNavigationStack(
                entries = plan.surfaces.drop(1),
                entryKey = { it.key },
                completedExitKeys = completedPredictiveBack,
                preview = preview?.let {
                    ActivityBackPreview(it.source, it.parent.takeUnless { parent -> parent == root.key },
                        it.enterModifier, it.exitModifier)
                },
                fadeOnly = { it.entry == MainNavigationEntry.Immersive },
                hideCoveredRoot = true,
                reflowKey = plan.isTwoPane,
                root = { render(root) },
                content = render,
            )
        }
        linkPreview?.invoke()
        foreground()
    }
}
