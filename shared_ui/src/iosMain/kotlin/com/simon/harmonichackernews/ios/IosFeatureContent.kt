package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createEditorFeatureSession
import com.simon.harmonichackernews.app.createSubmissionsFeatureSession
import com.simon.harmonichackernews.navigation.MainEditorRequest
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.presentation.EditorPresentationCopy
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.ui.common.SharedHarmonicTopAppBar
import com.simon.harmonichackernews.ui.editor.SharedEditorScreen
import com.simon.harmonichackernews.ui.session.EditorScreenSession
import com.simon.harmonichackernews.ui.session.SubmissionsScreenSession
import com.simon.harmonichackernews.ui.submissions.SharedSubmissionsRoute
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

@Composable
internal fun IosSubmissionsContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainSubmissionsRequest,
) {
    val scope = rememberCoroutineScope()
    val screenSession = remember(app, scene, request.serial, scope) {
        val state = scene.sessions.submissionsStateFor(
            request.serial,
            request.userName,
            app.network.algoliaRepository,
        )
        SubmissionsScreenSession(
            scope = scope,
            feature = app.createSubmissionsFeatureSession(scope, state),
        )
    }
    val controller = remember(screenSession, request.userName) {
        screenSession.createController(
            userName = request.userName,
            displaySettings = StoryDisplaySettings.from(app.userSettings.story)
                .withShowIndex(false),
        )
    }

    DisposableEffect(screenSession) {
        onDispose(screenSession::dispose)
    }
    LaunchedEffect(screenSession, scene) {
        screenSession.effects.collect { effect ->
            when (effect) {
                is SubmissionsRuntimeEffect.OpenStory -> {
                    scene.navigation.openStory(effect.destination)
                }
                is SubmissionsRuntimeEffect.OpenExternalLink ->
                    scene.links.openExternal(ExternalLinkRequest(effect.url))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SharedHarmonicTopAppBar(
            title = "Submissions",
            onBack = scene.navigation::closeSubmissions,
        )
        Box(Modifier.weight(1f)) {
            SharedSubmissionsRoute(
                controller = controller,
                previewService = app.previewResources,
                tintStore = app.storyResourceTints,
                includeStatusBarInset = false,
                onOpenLink = { scene.links.open(it) },
            )
        }
    }
}

@Composable
internal fun IosEditorContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainEditorRequest,
    backRequestVersion: Int,
) {
    val destination = request.destination
    val scope = rememberCoroutineScope()
    val screenSession = remember(app, request.serial, scope) {
        EditorScreenSession(
            scope = scope,
            feature = app.createEditorFeatureSession(
                scope = scope,
                type = destination.type,
                itemId = destination.itemId,
            ),
        )
    }
    val submitting by screenSession.submitting.collectAsState()

    DisposableEffect(screenSession) {
        onDispose(screenSession::dispose)
    }
    LaunchedEffect(screenSession, destination.type, scene) {
        screenSession.results.collect { result ->
            when (result) {
                EditorWorkflowResult.Success -> {
                    scene.userMessages.show(EditorPresentationCopy.successMessage(destination.type))
                    scene.navigation.closeEditor()
                }
                is EditorWorkflowResult.Failure -> scene.navigation.showFailureDetailDialog(
                    result.title,
                    result.message,
                    result.commentDraft,
                )
                is EditorWorkflowResult.Captcha -> {
                    screenSession.cancelCaptcha()
                    scene.navigation.showFailureDetailDialog(
                        title = "Hacker News CAPTCHA required",
                        message = "This action requires the Hacker News browser CAPTCHA. " +
                            "The CAPTCHA handoff is not yet connected to the iOS in-app " +
                            "browser, so complete this action on the Hacker News website.",
                        clipboardText = null,
                    )
                }
                is EditorWorkflowResult.CaptchaCancelled ->
                    scene.userMessages.show(result.message)
                EditorWorkflowResult.Ignored -> Unit
            }
        }
    }

    SharedEditorScreen(
        type = destination.type,
        parentText = destination.parentText,
        postTitle = destination.postTitle,
        user = destination.userName,
        submitting = submitting,
        backRequestVersion = backRequestVersion,
        onClose = scene.navigation::closeEditor,
        onSubmit = screenSession::submit,
        onOpenLink = { scene.links.open(it) },
    )
}
