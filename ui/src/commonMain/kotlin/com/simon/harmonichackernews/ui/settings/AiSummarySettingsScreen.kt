package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.GeminiNanoSummaryMode

enum class AiSummarySettingsDialog { BaseUrl, ApiKey, Model, SystemPrompt }

data class AiSummarySettingsUiState(
    val enabled: Boolean,
    val configurationComplete: Boolean,
    val localSummarizationSupported: Boolean,
    val mode: AiSummaryMode,
    val baseUrl: String,
    val apiKeyPreview: String,
    val model: String,
    val systemPrompt: String,
    val streamResponses: Boolean,
    val autoSummarizeArticles: Boolean,
    val enableBoldFormatting: Boolean,
    val showAdditionalInfo: Boolean,
    val geminiNanoSelected: Boolean,
    val geminiNanoSummaryMode: GeminiNanoSummaryMode,
    val disabledReason: String? = null,
)

@Composable
fun AiSummarySettingsScreen(
    state: AiSummarySettingsUiState,
    showNavigation: Boolean,
    contentVersion: Int,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onModeSelected: (AiSummaryMode) -> Unit,
    onGeminiNanoSummaryModeSelected: (GeminiNanoSummaryMode) -> Unit,
    onStreamChanged: (Boolean) -> Unit,
    onAutoSummarizeChanged: (Boolean) -> Unit,
    onEnableBoldFormattingChanged: (Boolean) -> Unit,
    onShowAdditionalInfoChanged: (Boolean) -> Unit,
    onDialogRequested: (AiSummarySettingsDialog) -> Unit,
    localModelsContent: @Composable () -> Unit,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_ai_summary),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsMainToggle(
                title = "Use AI summarization and Ask",
                checked = state.enabled,
                enabled = state.configurationComplete,
                onCheckedChange = onEnabledChanged,
                summary = state.disabledReason.takeIf { !state.configurationComplete },
            )
            Text(
                text = "Summarize articles and use Ask to ask questions about articles and comments with your selected AI model.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }

        item {
            SettingsCategory("Model") {
                if (state.localSummarizationSupported) {
                    SegmentedSetting(
                        options = listOf(
                            AiSummaryMode.LOCAL.storedValue to "Local",
                            AiSummaryMode.CLOUD.storedValue to "Cloud",
                        ),
                        optionIcons = mapOf(
                            AiSummaryMode.LOCAL.storedValue to Res.drawable.ic_smartphone,
                            AiSummaryMode.CLOUD.storedValue to Res.drawable.ic_cloud,
                        ),
                        selected = state.mode.storedValue,
                        onSelected = { onModeSelected(AiSummaryMode.fromStored(it)) },
                    )
                    AiSummaryModeTransition(mode = state.mode) { mode ->
                        if (mode == AiSummaryMode.LOCAL) {
                            Column(Modifier.fillMaxWidth()) {
                                SettingsDivider()
                                localModelsContent()
                            }
                        } else {
                            CloudAiModelSettingsContent(
                                state = state,
                                showTopDivider = true,
                                onDialogRequested = onDialogRequested,
                            )
                        }
                    }
                } else {
                    CloudAiModelSettingsContent(
                        state = state,
                        showTopDivider = false,
                        onDialogRequested = onDialogRequested,
                    )
                }
            }
        }
        item {
            SettingsCategory("Behavior") {
                if (state.mode == AiSummaryMode.LOCAL && state.geminiNanoSelected) {
                    SegmentedSetting(
                        title = "Gemini Nano summarizer",
                        summary = when (state.geminiNanoSummaryMode) {
                            GeminiNanoSummaryMode.THREE_BULLETS ->
                                "Built in 3-bullet summarization LoRA"
                            GeminiNanoSummaryMode.SYSTEM_PROMPT ->
                                "System prompt selected below"
                        },
                        options = listOf(
                            GeminiNanoSummaryMode.THREE_BULLETS.storedValue to "3 bullets",
                            GeminiNanoSummaryMode.SYSTEM_PROMPT.storedValue to "System prompt",
                        ),
                        selected = state.geminiNanoSummaryMode.storedValue,
                        onSelected = {
                            onGeminiNanoSummaryModeSelected(GeminiNanoSummaryMode.fromStored(it))
                        },
                    )
                    SettingsDivider()
                }
                val systemPromptEnabled = !(
                    state.mode == AiSummaryMode.LOCAL &&
                        state.geminiNanoSelected &&
                        state.geminiNanoSummaryMode == GeminiNanoSummaryMode.THREE_BULLETS
                    )
                SettingRow(
                    title = "System prompt",
                    summary = state.systemPrompt,
                    icon = Res.drawable.ic_subject,
                    summaryFontSizeSp = 13f,
                    summaryLineHeightSp = 17f,
                    summaryMaxLines = 10,
                    enabled = systemPromptEnabled,
                    onClick = { onDialogRequested(AiSummarySettingsDialog.SystemPrompt) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Stream responses",
                    summary = "Show each token as it is generated",
                    icon = Res.drawable.ic_stream,
                    checked = state.streamResponses,
                    onCheckedChange = onStreamChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Automatically summarize articles",
                    summary = "Start summarizing when an article is opened",
                    icon = Res.drawable.ic_auto_awesome,
                    checked = state.autoSummarizeArticles,
                    onCheckedChange = onAutoSummarizeChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Enable bold formatting",
                    icon = Res.drawable.ic_format_bold,
                    checked = state.enableBoldFormatting,
                    onCheckedChange = onEnableBoldFormattingChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show additional info",
                    icon = Res.drawable.ic_info,
                    checked = state.showAdditionalInfo,
                    onCheckedChange = onShowAdditionalInfoChanged,
                )
            }
        }
    }
}

@Composable
private fun CloudAiModelSettingsContent(
    state: AiSummarySettingsUiState,
    showTopDivider: Boolean,
    onDialogRequested: (AiSummarySettingsDialog) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showTopDivider) SettingsDivider()
        SettingRow(
            title = "Base URL",
            summary = state.baseUrl,
            icon = Res.drawable.ic_link,
            onClick = { onDialogRequested(AiSummarySettingsDialog.BaseUrl) },
        )
        SettingsDivider()
        SettingRow(
            title = "API Key",
            summary = state.apiKeyPreview,
            icon = Res.drawable.ic_key,
            onClick = { onDialogRequested(AiSummarySettingsDialog.ApiKey) },
        )
        SettingsDivider()
        SettingRow(
            title = "Model",
            summary = state.model.ifBlank { "Finding a recommended model…" },
            icon = Res.drawable.ic_hard_drive,
            onClick = { onDialogRequested(AiSummarySettingsDialog.Model) },
        )
    }
}
