package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.StorySummaryDiagnostics
import com.simon.harmonichackernews.summary.StorySummaryMode
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun StorySummaryInfoDialog(
    summary: String,
    diagnostics: StorySummaryDiagnostics?,
    streaming: Boolean,
    onDismiss: () -> Unit,
) {
    var liveExtraMillis by remember(streaming, diagnostics?.totalTimeMillis) {
        mutableLongStateOf(0L)
    }
    LaunchedEffect(streaming, diagnostics?.totalTimeMillis) {
        if (streaming) {
            val tickStartedAt = TimeSource.Monotonic.markNow()
            while (true) {
                delay(100L)
                liveExtraMillis = tickStartedAt.elapsedNow().inWholeMilliseconds
            }
        }
    }
    val liveDiagnostics = diagnostics?.let { current ->
        if (!streaming) {
            current
        } else {
            current.copy(
                totalTimeMillis = (current.totalTimeMillis ?: 0L) + liveExtraMillis,
                generationTimeMillis = current.generationTimeMillis?.plus(liveExtraMillis),
            )
        }
    }
    val backend = backendPresentation(diagnostics?.backendDetails)
    val outputCharacters = diagnostics?.outputCharacters ?: summary.length
    val estimatedTokens = diagnostics?.estimatedOutputTokens ?: estimateDisplayedTokens(summary)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(
                            bottom = HarmonicDimens.compose_settings_dialog_content_padding - 8.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ModelIdentityCard(liveDiagnostics?.mode, backend)
                    TimingSection(liveDiagnostics, backend)
                    WorkloadSection(
                        inputCharacters = diagnostics?.inputCharacters?.takeIf { it > 0 },
                        outputCharacters = outputCharacters,
                        estimatedTokens = estimatedTokens.takeIf { it > 0 },
                        estimatedTokensPerSecond = diagnostics?.estimatedTokensPerSecond,
                    )
                }
            }
        },
        confirmButton = {},
        showButtons = false,
        scrollableContent = true,
    )
}

@Composable
private fun ModelIdentityCard(
    mode: StorySummaryMode?,
    backend: BackendPresentation,
) {
    val identity = modelIdentity(mode, backend)
    val modelName = backend.name ?: identity.fallbackName
    val compactModelName = modelName.length > 32
    val containerColor = when (mode) {
        StorySummaryMode.LOCAL -> MaterialTheme.colorScheme.secondaryContainer
        StorySummaryMode.CLOUD -> MaterialTheme.colorScheme.tertiaryContainer
        null -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (mode) {
        StorySummaryMode.LOCAL -> MaterialTheme.colorScheme.onSecondaryContainer
        StorySummaryMode.CLOUD -> MaterialTheme.colorScheme.onTertiaryContainer
        null -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(identity.icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity.badge,
                color = contentColor.copy(alpha = 0.78f),
                fontFamily = ProductSansFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                lineHeight = 11.sp,
            )
            Text(
                text = modelName.withModelNameBreaks(),
                modifier = Modifier.semantics { contentDescription = modelName },
                color = contentColor,
                fontFamily = ProductSansFontFamily,
                fontSize = if (compactModelName) 17.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (compactModelName) 20.sp else 21.sp,
            )
        }
    }
}

@Composable
private fun TimingSection(
    diagnostics: StorySummaryDiagnostics?,
    backend: BackendPresentation,
) {
    val hasReportedLoad = diagnostics?.modelLoadMillis != null || backend.loadTime != null
    val showLoadPhase = diagnostics?.mode != StorySummaryMode.CLOUD &&
        backend.localModelRuntime() != LocalModelRuntime.GEMINI_NANO
    val phases = buildList {
        if (showLoadPhase) {
            add(
                TimingPhase(
                    label = "Load",
                    value = diagnostics?.modelLoadMillis?.formatDurationOrDash()
                        ?: backend.loadTime
                        ?: "—",
                    millis = diagnostics?.modelLoadMillis,
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        add(
            TimingPhase(
                label = if (hasReportedLoad) "Prepare" else "First output",
                value = if (hasReportedLoad) {
                    diagnostics?.postLoadToFirstOutputMillis.formatDurationOrDash()
                } else {
                    diagnostics?.timeToFirstOutputMillis.formatDurationOrDash()
                },
                millis = if (hasReportedLoad) {
                    diagnostics?.postLoadToFirstOutputMillis
                } else {
                    diagnostics?.timeToFirstOutputMillis
                },
                color = MaterialTheme.colorScheme.tertiary,
            ),
        )
        add(
            TimingPhase(
                label = "Generate",
                value = diagnostics?.generationTimeMillis.formatDurationOrDash(),
                millis = diagnostics?.generationTimeMillis,
                color = MaterialTheme.colorScheme.secondary,
            ),
        )
    }

    Column {
        SectionHeader(
            title = "Timing",
            trailing = diagnostics?.totalTimeMillis?.formatDurationOrDash()?.let { "Total  $it" },
        )
        TimingBar(phases)
        Row(
            modifier = Modifier.padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            phases.forEach { phase ->
                TimingPhaseLabel(
                    phase = phase,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TimingBar(phases: List<TimingPhase>) {
    val measuredPhases = phases.filter { it.millis != null }
    val measuredTotal = measuredPhases.sumOf { it.millis ?: 0L }.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(13.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        measuredPhases.forEach { phase ->
            val visibleFloor = measuredTotal / 16f
            val animatedWeight by animateFloatAsState(
                targetValue = maxOf(phase.millis?.toFloat() ?: 0f, visibleFloor),
                animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                label = "${phase.label} timing width",
            )
            Box(
                modifier = Modifier
                    .weight(animatedWeight)
                    .height(13.dp)
                    .background(phase.color),
            )
        }
    }
}

@Composable
private fun TimingPhaseLabel(
    phase: TimingPhase,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .background(
                    if (phase.millis == null) {
                        HarmonicTheme.colors.outlineVariant
                    } else {
                        phase.color
                    },
                    CircleShape,
                ),
        )
        Column {
            Text(
                text = phase.label,
                color = HarmonicTheme.colors.textSecondary,
                fontFamily = ProductSansFontFamily,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = phase.value,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WorkloadSection(
    inputCharacters: Int?,
    outputCharacters: Int,
    estimatedTokens: Int?,
    estimatedTokensPerSecond: Double?,
) {
    Column {
        SectionHeader(title = "Workload")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "Input characters",
                value = inputCharacters?.formatCount() ?: "—",
                icon = Res.drawable.ic_subject,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1.15f),
            )
            MetricTile(
                label = "Output characters",
                value = outputCharacters.formatCount(),
                icon = Res.drawable.ic_auto_awesome,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(0.85f),
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricTile(
                label = "Est. output tokens",
                value = estimatedTokens?.let { "≈${it.formatCount()}" } ?: "—",
                icon = Res.drawable.ic_stacks,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = HarmonicTheme.colors.textPrimary,
                modifier = Modifier.weight(0.85f),
            )
            MetricTile(
                label = "Est. tokens / second",
                value = estimatedTokensPerSecond?.let { "≈${formatOneDecimal(it)}" } ?: "—",
                icon = Res.drawable.ic_stream,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.15f),
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: DrawableResource,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(88.dp)
            .background(containerColor, RoundedCornerShape(16.dp))
            .padding(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = contentColor.copy(alpha = 0.76f),
                fontFamily = ProductSansFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
                maxLines = 2,
            )
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .background(contentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = contentColor,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = contentColor,
            fontFamily = ProductSansFontFamily,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 2.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.textSecondary,
            fontFamily = ProductSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        trailing?.let {
            Text(
                text = it,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class TimingPhase(
    val label: String,
    val value: String,
    val millis: Long?,
    val color: Color,
)

private data class ModelIdentity(
    val badge: String,
    val fallbackName: String,
    val icon: DrawableResource,
)

private data class BackendPresentation(
    val name: String?,
    val loadTime: String?,
)

private fun BackendPresentation.localModelRuntime(): LocalModelRuntime? = name?.let { modelName ->
    LocalModelCatalog.models.firstOrNull { it.displayName == modelName }?.runtime
}

private fun modelIdentity(
    mode: StorySummaryMode?,
    backend: BackendPresentation,
): ModelIdentity {
    val localRuntime = backend.localModelRuntime()
    return when (mode) {
        StorySummaryMode.LOCAL -> when (localRuntime) {
            LocalModelRuntime.GEMINI_NANO -> ModelIdentity(
                badge = "SYSTEM MODEL",
                fallbackName = "On-device model",
                icon = Res.drawable.ic_smartphone,
            )
            LocalModelRuntime.LITERT_LM -> ModelIdentity(
                badge = "LITERT-LM",
                fallbackName = "On-device model",
                icon = Res.drawable.ic_smartphone,
            )
            LocalModelRuntime.LLAMA_CPP -> ModelIdentity(
                badge = "LLAMA.CPP",
                fallbackName = "On-device model",
                icon = Res.drawable.ic_smartphone,
            )
            null -> ModelIdentity(
                badge = "ON DEVICE",
                fallbackName = "On-device model",
                icon = Res.drawable.ic_smartphone,
            )
        }
        StorySummaryMode.CLOUD -> ModelIdentity(
            badge = "CLOUD MODEL",
            fallbackName = "Cloud model",
            icon = Res.drawable.ic_cloud,
        )
        null -> ModelIdentity(
            badge = "SAVED SUMMARY",
            fallbackName = "Summary details unavailable",
            icon = Res.drawable.ic_history,
        )
    }
}

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
    )
}

private fun Long?.formatDurationOrDash(): String {
    val millis = this ?: return "—"
    return if (millis < 1_000L) "$millis ms" else "${formatOneDecimal(millis / 1_000.0)} s"
}

private fun Int.formatCount(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()

private fun String.withModelNameBreaks(): String =
    replace("/", "/\u200B").replace("-", "-\u200B")

private fun estimateDisplayedTokens(text: String): Int =
    if (text.isBlank()) 0 else (text.length + 3) / 4

private fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
