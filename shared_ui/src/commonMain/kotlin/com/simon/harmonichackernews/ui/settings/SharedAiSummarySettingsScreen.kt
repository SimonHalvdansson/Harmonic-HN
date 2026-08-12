package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AiSummaryMode

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
)

@Composable
fun SharedAiSummarySettingsScreen(
    state: AiSummarySettingsUiState,
    showNavigation: Boolean,
    contentVersion: Int,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onModeSelected: (AiSummaryMode) -> Unit,
    onStreamChanged: (Boolean) -> Unit,
    onDialogRequested: (AiSummarySettingsDialog) -> Unit,
    localModelsContent: @Composable () -> Unit,
) {
    val cloudMode = state.mode == AiSummaryMode.CLOUD
    SettingsPage(
        title = "AI summarization",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsMainToggle(
                title = "Use AI summarization",
                checked = state.enabled && state.configurationComplete,
                enabled = state.configurationComplete,
                onCheckedChange = onEnabledChanged,
            )
        }

        item {
            SettingsCard {
                if (state.localSummarizationSupported) {
                    SegmentedSetting(
                        title = "Summarization mode",
                        options = listOf(
                            AiSummaryMode.LOCAL.storedValue to "Local",
                            AiSummaryMode.CLOUD.storedValue to "Cloud",
                        ),
                        selected = state.mode.storedValue,
                        onSelected = { onModeSelected(AiSummaryMode.fromStored(it)) },
                    )
                    AnimatedVisibility(visible = state.mode == AiSummaryMode.LOCAL) {
                        localModelsContent()
                    }
                    SettingsDivider()
                }
                SettingRow(
                    title = "Base URL",
                    summary = state.baseUrl,
                    icon = Res.drawable.ic_link,
                    enabled = cloudMode,
                    onClick = { onDialogRequested(AiSummarySettingsDialog.BaseUrl) },
                )
                SettingsDivider()
                SettingRow(
                    title = "API Key",
                    summary = state.apiKeyPreview,
                    icon = Res.drawable.ic_key,
                    enabled = cloudMode,
                    onClick = { onDialogRequested(AiSummarySettingsDialog.ApiKey) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Model",
                    summary = state.model.ifBlank { "Finding a recommended model…" },
                    icon = Res.drawable.ic_hard_drive,
                    enabled = cloudMode,
                    onClick = { onDialogRequested(AiSummarySettingsDialog.Model) },
                )
                SettingsDivider()
                SettingRow(
                    title = "System prompt",
                    summary = state.systemPrompt,
                    icon = Res.drawable.ic_subject,
                    summaryFontSizeSp = 13f,
                    summaryLineHeightSp = 17f,
                    summaryMaxLines = 10,
                    enabled = cloudMode,
                    onClick = { onDialogRequested(AiSummarySettingsDialog.SystemPrompt) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Stream responses",
                    icon = Res.drawable.ic_stream,
                    checked = state.streamResponses,
                    enabled = cloudMode,
                    onCheckedChange = onStreamChanged,
                )
            }
        }
    }
}
