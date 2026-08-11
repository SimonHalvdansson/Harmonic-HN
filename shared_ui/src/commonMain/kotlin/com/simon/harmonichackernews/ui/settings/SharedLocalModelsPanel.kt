package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelPresentation
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import org.jetbrains.compose.resources.painterResource

data class LocalModelRowUiState(
    val model: LocalModelDefinition,
    val presentation: LocalModelPresentation,
)

@Composable
fun SharedLocalModelsPanel(
    models: List<LocalModelRowUiState>,
    modelIconPainter: @Composable (LocalModelDefinition) -> Painter,
    onModelSelected: (String) -> Unit,
    onAction: (String, LocalModelPresentationAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        models.forEachIndexed { index, row ->
            if (index > 0) SettingsDivider()
            val presentation = row.presentation
            SettingRow(
                title = row.model.displayName,
                summary = presentation.summary,
                icon = null,
                iconPainter = modelIconPainter(row.model),
                enabled = presentation.enabled,
                onClick = if (presentation.selectable) {
                    { onModelSelected(row.model.id) }
                } else {
                    null
                },
                trailing = {
                    LocalModelTrailingAction(
                        row = row,
                        onAction = onAction,
                    )
                },
            )
            presentation.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalModelTrailingAction(
    row: LocalModelRowUiState,
    onAction: (String, LocalModelPresentationAction) -> Unit,
) {
    val presentation = row.presentation
    Column {
        if (presentation.selected) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = "Selected",
            )
        }
        presentation.action?.let { action ->
            val icon = when (action) {
                LocalModelPresentationAction.CANCEL_DOWNLOAD -> Res.drawable.ic_close
                LocalModelPresentationAction.DELETE_MODEL -> Res.drawable.ic_delete
                LocalModelPresentationAction.DOWNLOAD_MODEL -> Res.drawable.ic_file_download
            }
            val description = when (action) {
                LocalModelPresentationAction.CANCEL_DOWNLOAD -> "Cancel download"
                LocalModelPresentationAction.DELETE_MODEL -> "Delete model"
                LocalModelPresentationAction.DOWNLOAD_MODEL -> "Download model"
            }
            IconButton(
                onClick = { onAction(row.model.id, action) },
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description,
                )
            }
        }
    }
}
