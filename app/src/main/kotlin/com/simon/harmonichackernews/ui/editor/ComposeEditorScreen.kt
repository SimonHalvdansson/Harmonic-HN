package com.simon.harmonichackernews.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.data.FileEditorDraftStorage
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import java.util.UUID
import kotlinx.io.files.Path

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
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val draftId = rememberSaveable { UUID.randomUUID().toString() }
    val draftStorage = remember(draftId) {
        FileEditorDraftStorage(Path(context.noBackupFilesDir.absolutePath, "editor-drafts", draftId))
    }
    DisposableEffect(draftStorage, lifecycle, activity) {
        onDispose {
            // Keep recovery files when Android destroys the activity; delete on editor dismissal.
            if (lifecycle.currentState != Lifecycle.State.DESTROYED || activity?.isFinishing == true) {
                draftStorage.clear()
            }
        }
    }
    val reportDraftFailure: (EditorDraftStorageFailure) -> Unit = remember {
        val reported = mutableSetOf<EditorDraftStorageFailure>()
        return@remember { failure: EditorDraftStorageFailure ->
            if (reported.add(failure)) {
                val message = when (failure) {
                    EditorDraftStorageFailure.SAVE -> "Couldn’t save this large draft for recovery. Keep a copy before leaving."
                    EditorDraftStorageFailure.RESTORE -> "Couldn’t restore the saved draft text."
                }
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }
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
        draftStorage = draftStorage,
        onDraftStorageFailure = reportDraftFailure,
    )
}
