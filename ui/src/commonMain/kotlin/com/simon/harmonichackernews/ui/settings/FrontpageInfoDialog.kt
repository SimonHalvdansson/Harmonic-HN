package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_info
import com.simon.harmonichackernews.ui.common.PlatformDialogBackgroundDimAmount
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.common.PlatformDialogDimHost
import com.simon.harmonichackernews.ui.common.PlatformDialogPredictiveBackHandler
import com.simon.harmonichackernews.ui.common.TransformOverlay
import com.simon.harmonichackernews.ui.common.platformDialogPredictiveBackSupported
import com.simon.harmonichackernews.ui.common.platformDialogProperties
import com.simon.harmonichackernews.ui.stories.menuIcon
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FrontpageInfoButton(
    type: StoryType,
    tint: Color = HarmonicTheme.colors.iconTint,
    isTransformSource: Boolean = false,
    onClick: () -> Unit,
) {
    // The live button keeps normal press feedback. Once its row becomes a morph source,
    // dispose that ripple before capture so the closing snapshot cannot replay a frozen press.
    CompositionLocalProvider(
        LocalRippleConfiguration provides if (isTransformSource) null else LocalRippleConfiguration.current,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painterResource(Res.drawable.ic_info),
                contentDescription = "About ${type.label}",
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

internal data class FrontpageInfoSource(
    val type: StoryType,
    val bounds: Rect?,
    val color: Color,
    val layer: GraphicsLayer?,
    val borderColor: Color,
)

@Composable
internal fun FrontpageInfoDialog(
    source: FrontpageInfoSource,
    onSourceReadyToCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val type = source.type
    // HN's list definitions: https://news.ycombinator.com/lists
    val description = when (type) {
        StoryType.CLASSIC -> "An alternative Hacker News frontpage based on votes from its oldest accounts."
        StoryType.BEST_COMMENTS -> "The most-upvoted Hacker News comments from the last 48 hours."
        StoryType.HIGHLIGHTS -> "A curated collection of standout Hacker News comments and discussions from over the years."
        StoryType.ACTIVE -> "Stories with the most active discussions on Hacker News right now."
        StoryType.FRONT -> "Stories that appeared on the Hacker News frontpage on a particular day. Use the date controls to browse past days."
        StoryType.UNSLOP -> "Hacker News stories with AI-related posts filtered out by unslop.news."
        else -> return
    }
    var dismissRequest by remember(source) { mutableIntStateOf(0) }
    val backProgress = remember(source) { Animatable(0f) }
    var backEdge by remember(source) { mutableIntStateOf(0) }
    val requestDismiss = { dismissRequest += 1 }
    PlatformDialogDimHost {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = platformDialogProperties(
                dismissOnBackPress = !platformDialogPredictiveBackSupported,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            PlatformDisableDialogWindowAnimations()
            // The transform draws its own animated scrim.
            PlatformDialogBackgroundDimAmount(0f)
            PlatformDialogPredictiveBackHandler(
                enabled = platformDialogPredictiveBackSupported,
                onProgress = {
                    backEdge = if (it.swipeDirection < 0f) 1 else 0
                    backProgress.snapTo(it.progress)
                },
                onCancelled = { backProgress.animateTo(0f, tween(180)) },
                onCommitted = { requestDismiss() },
            )
            BoxWithConstraints(Modifier.semantics { paneTitle = type.label }) {
                val shortEdge = minOf(maxWidth, maxHeight)
                val longEdge = maxOf(maxWidth, maxHeight)
                TransformOverlay(
                    contentKey = source,
                    sourceBounds = source.bounds,
                    sourceContentLayer = source.layer,
                    dismissRequestVersion = dismissRequest,
                    predictiveBackProgress = backProgress.value,
                    predictiveBackEdge = backEdge,
                    maxWidth = if (shortEdge >= 600.dp && longEdge >= shortEdge * 1.3f) {
                        HarmonicDimens.compose_settings_dialog_tablet_max_width
                    } else {
                        HarmonicDimens.compose_settings_dialog_max_width
                    },
                    horizontalPadding = 24.dp,
                    verticalPadding = 24.dp,
                    targetCornerRadius = 28.dp,
                    sourceCornerRadius = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    sourceContainerColor = source.color,
                    sourceBorderColor = source.borderColor,
                    sourceBorderWidth = 1.dp,
                    onSourceReadyToCover = onSourceReadyToCover,
                    onDismissRequest = requestDismiss,
                    onDismissAnimationFinished = onDismiss,
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.semantics { heading() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painterResource(type.menuIcon),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = HarmonicTheme.colors.iconTint,
                            )
                            SettingsDialogTitle(type.label)
                        }
                        Text(
                            text = description,
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        )
                        SettingsDialogTextButton(
                            onClick = requestDismiss,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("OK") }
                    }
                }
            }
        }
    }
}
