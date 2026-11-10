package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.categorization.ArticleEmbeddingInput
import com.simon.harmonichackernews.categorization.NativeEmbeddingProgress
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.themeBackground

@Composable
internal fun ArticleEmbeddingProviderSettings(
    native: NativeEmbeddingProgress,
    preferSystem: Boolean,
    fallbackAvailable: Boolean,
    onPreferSystem: (Boolean) -> Unit,
    onResume: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val model = native.model
    SettingsCategory("Model", titleTopPadding = 8.dp) {
        Column(Modifier.fillMaxWidth().themeBackground { cardBackground }) {
            if (model != null) SegmentedSetting(
                options = listOf(true to "System", false to "EmbeddingGemma 2"),
                selected = preferSystem, disabledOptions = if (fallbackAvailable) emptySet() else setOf(false), onSelected = onPreferSystem)
            when {
                !native.checked && preferSystem -> SettingRow(title = "Checking model…", icon = Res.drawable.ic_info, onClick = null)
                model != null && preferSystem -> {
                    SettingRow(title = model.name, summary = model.modelName, icon = Res.drawable.ic_check, onClick = null)
                    if (native.incomplete) {
                        SettingsDivider()
                        SettingRow(title = when {
                            native.preparing -> "Preparing examples"
                            native.paused -> "Preparation paused"
                            else -> "Category examples"
                        }, icon = Res.drawable.ic_info, summary = "${native.completed} / ${native.total}", onClick = null,
                            trailing = {
                                if (!native.preparing) TextButton(onClick = onResume) {
                                    Text(if (native.completed == 0 && !native.paused) "Prepare" else "Resume",
                                        fontFamily = AppUiFontFamily, fontSize = 14.sp, lineHeight = 18.sp)
                                }
                            })
                        LinearProgressIndicator(progress = { native.completed.toFloat() / native.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
                    }
                }
                else -> fallback()
            }
        }
    }
}

@Composable
internal fun ArticleEmbeddingTokenSetting(native: NativeEmbeddingProgress, preferSystem: Boolean, selected: Int, onSelected: (Int) -> Unit) {
    val model = native.model.takeIf { preferSystem }
    SegmentedSetting(title = "Input token limit",
        summary = if (model != null) model.tokenLimit?.let { "System limit: $it tokens" } ?: "Managed by the system"
            else "Longer inputs use more time and memory.",
        options = ArticleEmbeddingInput.TOKEN_LIMITS.map { it to it.toString() },
        selected = model?.tokenLimit?.takeIf { it in ArticleEmbeddingInput.TOKEN_LIMITS } ?: selected,
        enabled = (!preferSystem || native.checked) && model == null, onSelected = onSelected)
}

@Composable
internal fun EmbeddingGemmaModelDetails(summary: String, action: @Composable () -> Unit) {
    SettingRow(title = "EmbeddingGemma 2", summary = summary, icon = Res.drawable.model_logo_google,
        onClick = null, trailing = action)
    FlowRow(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LocalModelTag("Persistent background", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        LocalModelTag("191 MB RAM", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
