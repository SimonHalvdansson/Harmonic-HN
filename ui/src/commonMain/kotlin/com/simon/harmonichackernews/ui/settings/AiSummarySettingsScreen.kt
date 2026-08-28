package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
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
    val geminiNanoSelected: Boolean,
    val geminiNanoSummaryMode: GeminiNanoSummaryMode,
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
                title = "Use AI summarization",
                checked = state.enabled,
                enabled = state.configurationComplete,
                onCheckedChange = onEnabledChanged,
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
                    AnimatedContent(
                        targetState = state.mode,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            val direction = if (targetState == AiSummaryMode.CLOUD) 1 else -1
                            val enter = slideInHorizontally(
                                animationSpec = tween(
                                    AiModeTransitionDurationMillis,
                                    easing = FastOutSlowInEasing,
                                ),
                                initialOffsetX = { width ->
                                    direction * (width / AiModeEnterOffsetDivisor).coerceAtLeast(1)
                                },
                            ) + fadeIn(
                                tween(
                                    AiModeEnterFadeDurationMillis,
                                    delayMillis = AiModeEnterFadeDelayMillis,
                                    easing = LinearEasing,
                                ),
                            )
                            val exit = slideOutHorizontally(
                                animationSpec = tween(
                                    AiModeExitDurationMillis,
                                    easing = FastOutSlowInEasing,
                                ),
                                targetOffsetX = { width ->
                                    -direction * (width / AiModeExitOffsetDivisor).coerceAtLeast(1)
                                },
                            ) + fadeOut(
                                tween(AiModeExitFadeDurationMillis, easing = LinearEasing),
                            )
                            (enter togetherWith exit).using(
                                SizeTransform(clip = true) { _, _ ->
                                    tween(
                                        AiModeTransitionDurationMillis,
                                        easing = FastOutSlowInEasing,
                                    )
                                },
                            )
                        },
                        label = "AI summary mode content",
                    ) { mode ->
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

private const val AiModeTransitionDurationMillis = 240
private const val AiModeExitDurationMillis = 170
private const val AiModeEnterFadeDurationMillis = 175
private const val AiModeEnterFadeDelayMillis = 25
private const val AiModeExitFadeDurationMillis = 125
private const val AiModeEnterOffsetDivisor = 8
private const val AiModeExitOffsetDivisor = 10
