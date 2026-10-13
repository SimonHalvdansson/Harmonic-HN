package com.simon.harmonichackernews.ui.session

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createSubmissionsStore
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.presentation.CommentsHostRestoration
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import kotlinx.serialization.Serializable

@Serializable
internal data class CommentsSessionRestoration(
    val host: CommentsHostRestoration,
    val scrollInitialized: Boolean,
    val topCommentId: Int,
    val topCommentOffset: Int,
    val collapsedIds: Set<Int>,
)

/** Portable comment state survives host replacement and is isolated to one navigation visit. */
class CommentsViewModel(
    scene: HarmonicSceneComposition,
    request: MainStoryRequest,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val session = scene.sessions.commentsStateFor(request.serial, request.storyId)

    init {
        savedStateHandle.get<SavedState>("comments")?.let {
            val restored = decodeFromSavedState<CommentsSessionRestoration>(it)
            session.hostRestoration = restored.host
            session.scrollProgress.apply {
                initialized = restored.scrollInitialized
                topCommentId = restored.topCommentId
                topCommentOffset = restored.topCommentOffset
                collapsedIDs = restored.collapsedIds.toMutableSet()
            }
        }
        savedStateHandle.setSavedStateProvider("comments") {
            val scroll = session.scrollProgress
            encodeToSavedState(CommentsSessionRestoration(
                session.hostRestoration, scroll.initialized, scroll.topCommentId,
                scroll.topCommentOffset, scroll.collapsedIDs.toSet(),
            ))
        }
    }
}

@Composable
fun commentsViewModel(scene: HarmonicSceneComposition, request: MainStoryRequest): CommentsViewModel =
    viewModel { CommentsViewModel(scene, request, createSavedStateHandle()) }

@Serializable
internal data class SubmissionsRestoration(val position: Int, val top: Int, val collapsed: Boolean)

/** Fetches and list state belong to the Nav3 entry, independent of the native host's composition. */
class SubmissionsViewModel(
    scene: HarmonicSceneComposition,
    request: MainSubmissionsRequest,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val session = scene.sessions.submissionsStateFor(
        request.serial, request.userName, scene.app.network.algoliaRepository,
    ).also { session ->
        savedStateHandle.get<SavedState>("submissions")?.let {
            val restored = decodeFromSavedState<SubmissionsRestoration>(it)
            session.initialized = true
            session.firstVisibleStoryPosition = restored.position
            session.firstVisibleStoryTop = restored.top
            session.appBarCollapsed = restored.collapsed
        }
    }
    val store = scene.app.createSubmissionsStore(viewModelScope, session)

    // A new native host needs the latest position, even when this ViewModel was retained.
    fun scrollRestoration() = SubmissionsScrollRestoration(
        session.firstVisibleStoryPosition, session.firstVisibleStoryTop, session.appBarCollapsed,
    )

    init {
        store.start()
        savedStateHandle.setSavedStateProvider("submissions") {
            encodeToSavedState(SubmissionsRestoration(
                session.firstVisibleStoryPosition, session.firstVisibleStoryTop, session.appBarCollapsed,
            ))
        }
    }

    override fun onCleared() = store.close()
}

@Composable
fun submissionsViewModel(scene: HarmonicSceneComposition, request: MainSubmissionsRequest): SubmissionsViewModel =
    viewModel { SubmissionsViewModel(scene, request, createSavedStateHandle()) }
