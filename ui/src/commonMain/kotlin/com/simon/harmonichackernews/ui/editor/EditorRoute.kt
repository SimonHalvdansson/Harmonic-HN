package com.simon.harmonichackernews.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createEditorFeatureSession
import com.simon.harmonichackernews.navigation.MainEditorRequest
import com.simon.harmonichackernews.presentation.EditorPresentationCopy
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.ui.session.EditorScreenSession

@Composable
fun EditorRoute(
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
                rootStoryId = destination.rootStoryId,
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
                    val response = scene.captchaRequests.awaitResponse(result.challenge)
                    if (response == null) screenSession.cancelCaptcha()
                    else screenSession.respondToCaptcha(result.challenge, response)
                }
                is EditorWorkflowResult.CaptchaCancelled ->
                    scene.userMessages.show(result.message)
                EditorWorkflowResult.Ignored -> Unit
            }
        }
    }

    EditorScreen(
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
