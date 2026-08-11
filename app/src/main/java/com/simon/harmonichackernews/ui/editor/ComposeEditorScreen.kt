package com.simon.harmonichackernews.ui.editor

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission

@Stable
class ComposeEditorController internal constructor(
    private val activity: ComponentActivity,
) {
    internal var submitting by mutableStateOf(false)

    fun setSubmitting(submitting: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this.submitting = submitting
        } else {
            activity.runOnUiThread { this.submitting = submitting }
        }
    }
}

/** Android lifecycle/back-dispatch adapter around the platform-neutral editor screen. */
@Composable
internal fun ComposeEditorScreen(
    type: EditorType,
    parentText: String?,
    postTitle: String?,
    user: String?,
    titleMaxLength: Int,
    submitting: Boolean,
    onClose: () -> Unit,
    onSubmit: (EditorSubmission) -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    var backRequestVersion by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { backRequestVersion++ }

    SharedEditorScreen(
        type = type,
        parentText = parentText,
        postTitle = postTitle,
        user = user,
        titleMaxLength = titleMaxLength,
        submitting = submitting,
        backRequestVersion = backRequestVersion,
        onClose = onClose,
        onSubmit = onSubmit,
        onOpenLink = onOpenLink,
    )
}
