package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.summary.StorySummaryDiagnostics
import com.simon.harmonichackernews.summary.StorySummaryMode
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.math.roundToInt

@Composable
internal fun StorySummaryInfoDialog(
    summary: String,
    diagnostics: StorySummaryDiagnostics?,
    onDismiss: () -> Unit,
) {
    val backend = backendPresentation(diagnostics?.backendDetails)
    val outputCharacters = diagnostics?.outputCharacters ?: summary.length
    val estimatedTokens = diagnostics?.estimatedOutputTokens ?: estimateDisplayedTokens(summary)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("AI summary info") },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = backend.name ?: when (diagnostics?.mode) {
                                StorySummaryMode.LOCAL -> "On-device model"
                                StorySummaryMode.CLOUD -> "Cloud model"
                                null -> "Summary details unavailable"
                            },
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when (diagnostics?.mode) {
                                StorySummaryMode.LOCAL -> "Local summarization"
                                StorySummaryMode.CLOUD -> "Cloud summarization"
                                null -> "This summary may have been restored from cache"
                            },
                            modifier = Modifier.padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                    }

                    DiagnosticSection(
                        title = "Timing",
                        rows = listOf(
                            DiagnosticRowValue(
                                "Model loading",
                                diagnostics?.modelLoadMillis?.formatDurationOrUnavailable()
                                    ?: backend.loadTime
                                    ?: "Not reported",
                            ),
                            DiagnosticRowValue(
                                "Time to first token",
                                diagnostics?.timeToFirstOutputMillis.formatDurationOrUnavailable(),
                            ),
                            DiagnosticRowValue(
                                "After model load",
                                diagnostics?.postLoadToFirstOutputMillis
                                    .formatDurationOrUnavailable(),
                            ),
                            DiagnosticRowValue(
                                "Generation time",
                                diagnostics?.generationTimeMillis.formatDurationOrUnavailable(),
                            ),
                            DiagnosticRowValue(
                                "Total time",
                                diagnostics?.totalTimeMillis.formatDurationOrUnavailable(),
                            ),
                        ),
                        supportingText =
                            "Time to first token measures the first non-empty output callback. " +
                                "When model loading is reported separately, after model load is " +
                                "the inferred time spent on prompt processing and generation " +
                                "before that callback.",
                    )

                    DiagnosticSection(
                        title = "Workload",
                        rows = listOf(
                            DiagnosticRowValue(
                                "Source input",
                                diagnostics?.inputCharacters
                                    ?.takeIf { it > 0 }
                                    ?.let { "$it characters" }
                                    ?: "Not available",
                            ),
                            DiagnosticRowValue("Output size", "$outputCharacters characters"),
                            DiagnosticRowValue(
                                "Estimated output",
                                if (estimatedTokens > 0) "≈$estimatedTokens tokens" else "Not available",
                            ),
                            DiagnosticRowValue(
                                "Estimated speed",
                                diagnostics?.estimatedTokensPerSecond
                                    ?.let { "≈${formatOneDecimal(it)} tok/s" }
                                    ?: "Not available",
                            ),
                        ),
                        supportingText =
                            "Token counts and rates are estimates based on output length. " +
                                "Providers do not always expose exact token or prompt metrics.",
                    )

                    backend.details?.let { details ->
                        DiagnosticSection(
                            title = "Backend details",
                            rows = listOf(DiagnosticRowValue("Reported value", details)),
                        )
                    }
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        scrollableContent = true,
    )
}

@Composable
private fun DiagnosticSection(
    title: String,
    rows: List<DiagnosticRowValue>,
    supportingText: String? = null,
) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            color = HarmonicTheme.colors.textSecondary,
            fontFamily = ProductSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HarmonicTheme.colors.surfaceContainerHigh, RoundedCornerShape(14.dp))
                .border(1.dp, HarmonicTheme.colors.outlineVariant, RoundedCornerShape(14.dp)),
        ) {
            rows.forEachIndexed { index, row ->
                DiagnosticRow(row)
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = HarmonicTheme.colors.outlineVariant,
                    )
                }
            }
        }
        supportingText?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp),
                color = HarmonicTheme.colors.textSecondary,
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(row: DiagnosticRowValue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.textSecondary,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
        )
        Text(
            text = row.value,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class DiagnosticRowValue(val label: String, val value: String)

private data class BackendPresentation(
    val name: String?,
    val loadTime: String?,
    val details: String?,
)

private fun backendPresentation(value: String?): BackendPresentation {
    val parts = value
        ?.split(" · ", limit = 2)
        ?.map { it.trim() }
        .orEmpty()
    val name = parts.firstOrNull()?.takeIf(String::isNotBlank)
    val detail = parts.getOrNull(1)?.takeIf(String::isNotBlank)
    val loadTime = detail
        ?.takeIf { it.endsWith(" load") }
        ?.removeSuffix(" load")
        ?.takeUnless { it == "—" }
    return BackendPresentation(
        name = name,
        loadTime = loadTime,
        details = detail?.takeUnless { it.endsWith(" load") || it == "load —" },
    )
}

private fun Long?.formatDurationOrUnavailable(): String {
    val millis = this ?: return "Not available"
    return if (millis < 1_000L) "$millis ms" else "${formatOneDecimal(millis / 1_000.0)} s"
}

private fun estimateDisplayedTokens(text: String): Int =
    if (text.isBlank()) 0 else (text.length + 3) / 4

private fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
