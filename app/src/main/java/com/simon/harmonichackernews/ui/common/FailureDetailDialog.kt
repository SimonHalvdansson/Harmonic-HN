package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

/** Compose replacement for the selectable message in the legacy Android failure dialog. */
@Composable
internal fun FailureDetailDialog(
    title: String?,
    message: String?,
    showCopyComment: Boolean,
    onCopyComment: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { value ->
            { SettingsDialogTitle(value) }
        },
        text = message?.let { value ->
            {
                SelectionContainer(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = value,
                        color = HarmonicTheme.colors.textPrimary,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        },
        neutralButton = if (showCopyComment) {
            {
                SettingsDialogTextButton(onClick = onCopyComment) {
                    Text("Copy comment")
                }
            }
        } else {
            null
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        scrollableContent = message != null,
    )
}

@Preview(showBackground = true)
@Composable
private fun FailureDetailDialogPreview() {
    HarmonicTheme {
        FailureDetailDialog(
            title = "Couldn't post comment",
            message = "Hacker News returned an unexpected response.",
            showCopyComment = true,
            onCopyComment = {},
            onDismiss = {},
        )
    }
}
