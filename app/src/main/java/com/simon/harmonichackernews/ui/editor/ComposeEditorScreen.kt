package com.simon.harmonichackernews.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission

/** Android lifecycle/back-dispatch adapter around the platform-neutral editor screen. */
@Composable
internal fun ComposeEditorScreen(
    type: EditorType,
    parentText: String?,
    postTitle: String?,
    user: String?,
    submitting: Boolean,
    onPredictiveBackEnabledChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSubmit: (EditorSubmission) -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    var backRequestVersion by rememberSaveable { mutableIntStateOf(0) }
    var predictiveBackEnabled by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !predictiveBackEnabled) { backRequestVersion++ }

    EditorScreen(
        type = type,
        parentText = parentText,
        postTitle = postTitle,
        user = user,
        submitting = submitting,
        backRequestVersion = backRequestVersion,
        onPredictiveBackEnabledChanged = { enabled ->
            predictiveBackEnabled = enabled
            onPredictiveBackEnabledChanged(enabled)
        },
        onClose = onClose,
        onSubmit = onSubmit,
        onOpenLink = onOpenLink,
    )
}
