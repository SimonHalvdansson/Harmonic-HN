package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_auto_awesome
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.resources.ic_delete
import com.simon.harmonichackernews.resources.ic_file_download
import com.simon.harmonichackernews.resources.model_logo_google
import com.simon.harmonichackernews.resources.model_logo_liquid
import com.simon.harmonichackernews.resources.model_logo_mistral
import com.simon.harmonichackernews.resources.model_logo_nvidia
import com.simon.harmonichackernews.resources.model_logo_prism
import com.simon.harmonichackernews.resources.model_logo_qwen
import com.simon.harmonichackernews.summary.LocalModelBrand
import com.simon.harmonichackernews.summary.LocalModelDefinition
import com.simon.harmonichackernews.summary.LocalModelManagerState
import com.simon.harmonichackernews.summary.LocalModelPresentation
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.formatDecimalBytes
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

data class LocalModelRowUiState(
    val model: LocalModelDefinition,
    val presentation: LocalModelPresentation,
)

@Composable
fun LocalModelsRoute(
    localModels: LocalModelService,
    managerState: LocalModelManagerState,
    nanoAvailabilityResolved: Boolean,
    nanoAvailable: Boolean,
    models: List<LocalModelDefinition> = localModels.catalog,
    onChanged: () -> Unit = {},
    onMessage: (String) -> Unit,
) {
    val rows = models.map { definition ->
        LocalModelRowUiState(
            model = definition,
            presentation = localModels.presentation(
                model = definition,
                nanoAvailabilityResolved = nanoAvailabilityResolved,
                nanoAvailable = nanoAvailable,
                managerState = managerState,
            ),
        )
    }
    LocalModelsPanel(
        models = rows,
        modelIconPainter = { definition ->
            painterResource(
                when (definition.brand) {
                    LocalModelBrand.GOOGLE -> Res.drawable.model_logo_google
                    LocalModelBrand.PRISM -> Res.drawable.model_logo_prism
                    LocalModelBrand.QWEN -> Res.drawable.model_logo_qwen
                    LocalModelBrand.NVIDIA -> Res.drawable.model_logo_nvidia
                    LocalModelBrand.MISTRAL -> Res.drawable.model_logo_mistral
                    LocalModelBrand.LIQUID -> Res.drawable.model_logo_liquid
                },
            )
        },
        onModelSelected = { modelId ->
            localModels.select(modelId)
            onChanged()
        },
        onAction = { modelId, action ->
            when (action) {
                LocalModelPresentationAction.CANCEL_DOWNLOAD -> localModels.cancel(modelId)
                LocalModelPresentationAction.DELETE_MODEL -> localModels.remove(modelId)
                LocalModelPresentationAction.DOWNLOAD_MODEL -> {
                    localModels.requestRuntimeAndModelDownload(modelId)
                        ?.takeIf(String::isNotBlank)
                        ?.let(onMessage)
                }
            }
            onChanged()
        },
    )
}

/** Shared rendition of the original Android Views model chooser. */
@Composable
fun LocalModelsPanel(
    models: List<LocalModelRowUiState>,
    modelIconPainter: @Composable (LocalModelDefinition) -> Painter,
    onModelSelected: (String) -> Unit,
    onAction: (String, LocalModelPresentationAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .padding(
                start = HarmonicDimens.compose_settings_row_horizontal_padding,
                top = 4.dp,
                end = HarmonicDimens.compose_settings_row_horizontal_padding,
                bottom = 8.dp,
            )
            .animateContentSize(),
    ) {
        models.forEach { row ->
            LocalModelCard(
                row = row,
                iconPainter = modelIconPainter(row.model),
                onModelSelected = onModelSelected,
                onAction = onAction,
            )
        }
        Text(
            text = "Models are loaded when the AI summarization button is clicked, leading to " +
                "a noticeable delay especially on older devices. Model weights are not kept in " +
                "memory after the summary is complete. Inference speed varies considerably by " +
                "model and device.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
}

/** A system-managed model rendered with the same visual hierarchy as downloadable model cards. */
@Composable
fun ManagedLocalModelPanel(
    title: String,
    status: String,
    available: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .padding(
                start = HarmonicDimens.compose_settings_row_horizontal_padding,
                top = 4.dp,
                end = HarmonicDimens.compose_settings_row_horizontal_padding,
                bottom = 8.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(shape)
                .background(HarmonicTheme.colors.surfaceContainerHigh)
                .border(1.dp, HarmonicTheme.colors.outlineVariant, shape)
                .alpha(if (available) 1f else 0.38f)
                .padding(start = 12.dp, top = 9.dp, end = 12.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = title,
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = status,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        Text(
            text = "This model is managed by the operating system and does not require a separate download.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun LocalModelCard(
    row: LocalModelRowUiState,
    iconPainter: Painter,
    onModelSelected: (String) -> Unit,
    onAction: (String, LocalModelPresentationAction) -> Unit,
) {
    val presentation = row.presentation
    val shape = RoundedCornerShape(14.dp)
    val backgroundColor by animateColorAsState(
        targetValue = if (presentation.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            HarmonicTheme.colors.surfaceContainerHigh
        },
        animationSpec = tween(180),
        label = "local model card background",
    )
    val outlineColor by animateColorAsState(
        targetValue = if (presentation.selected) {
            MaterialTheme.colorScheme.primary
        } else {
            HarmonicTheme.colors.outlineVariant
        },
        animationSpec = tween(180),
        label = "local model card outline",
    )
    val status = row.visibleStatus()
    val showIndeterminateProgress = status?.let {
        it.startsWith("Waiting") || it.startsWith("Preparing")
    } == true
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .clip(shape)
        .background(backgroundColor)
        .border(if (presentation.selected) 2.dp else 1.dp, outlineColor, shape)
        .then(
            if (presentation.selectable) {
                Modifier.clickable(
                    role = Role.RadioButton,
                    onClick = { onModelSelected(row.model.id) },
                )
            } else {
                Modifier
            },
        )
        .alpha(if (presentation.enabled) 1f else 0.38f)

    Column(
        modifier = cardModifier
            .padding(start = 12.dp, top = 7.dp, end = 4.dp, bottom = 7.dp)
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = row.model.displayName,
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!row.model.downloadable || !presentation.enabled) {
                    Text(
                        text = presentation.summary,
                        modifier = Modifier.padding(top = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LocalModelTag(
                            text = row.model.quantization,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            foreground = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        LocalModelTag(
                            text = formatDecimalBytes(row.model.sizeBytes),
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        LocalModelTag(
                            text = row.model.runtime.displayLabel(),
                            background = HarmonicTheme.colors.surfaceContainerHighest,
                            foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LocalModelTrailingAction(row = row, onAction = onAction)
        }

        val progress = presentation.progress
        val animatedProgress by animateFloatAsState(
            targetValue = progress ?: 0f,
            animationSpec = tween(LocalModelProgressAnimationDurationMillis),
            label = "local model download progress",
        )
        AnimatedVisibility(visible = progress != null || showIndeterminateProgress) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, top = 5.dp, end = 6.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, top = 5.dp, end = 6.dp),
                )
            }
        }
        AnimatedVisibility(visible = status != null) {
            Text(
                text = status.orEmpty(),
                modifier = Modifier.padding(start = 42.dp, top = 2.dp, end = 6.dp),
                color = HarmonicTheme.colors.textSecondary,
                fontFamily = ProductSansFontFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun LocalModelTag(
    text: String,
    background: Color,
    foreground: Color,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = foreground,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        maxLines = 1,
    )
}

@Composable
private fun LocalModelTrailingAction(
    row: LocalModelRowUiState,
    onAction: (String, LocalModelPresentationAction) -> Unit,
) {
    val action = row.presentation.action.takeIf { row.presentation.enabled }
    Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.72f)) togetherWith
                    (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f))
            },
            label = "local model action",
        ) { currentAction ->
            if (currentAction == null) {
                Spacer(modifier = Modifier.size(40.dp))
            } else {
                val icon = when (currentAction) {
                    LocalModelPresentationAction.CANCEL_DOWNLOAD -> Res.drawable.ic_close
                    LocalModelPresentationAction.DELETE_MODEL -> Res.drawable.ic_delete
                    LocalModelPresentationAction.DOWNLOAD_MODEL -> Res.drawable.ic_file_download
                }
                val description = when (currentAction) {
                    LocalModelPresentationAction.CANCEL_DOWNLOAD -> "Cancel ${row.model.displayName} download"
                    LocalModelPresentationAction.DELETE_MODEL -> "Delete ${row.model.displayName}"
                    LocalModelPresentationAction.DOWNLOAD_MODEL -> "Download ${row.model.displayName}"
                }
                IconButton(
                    onClick = { onAction(row.model.id, currentAction) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = description,
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
        }
    }
}

private fun LocalModelRowUiState.visibleStatus(): String? {
    if (!model.downloadable || !presentation.enabled) return null
    val state = presentation.summary.substringAfter('\n', missingDelimiterValue = "").trim()
    return state.takeUnless { it.isBlank() || it == "Not downloaded" || it == "Downloaded" }
}

private fun LocalModelRuntime.displayLabel(): String = when (this) {
    LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
    LocalModelRuntime.LITERT_LM -> "LiteRT-LM"
    LocalModelRuntime.LLAMA_CPP -> "llama.cpp"
}

private const val LocalModelProgressAnimationDurationMillis = 500
