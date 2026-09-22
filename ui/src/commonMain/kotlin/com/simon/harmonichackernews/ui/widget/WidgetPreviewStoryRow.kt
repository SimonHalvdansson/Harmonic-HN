package com.simon.harmonichackernews.ui.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.rememberResourceTintPalette
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Android supplies TextView-compatible font padding for the Glance preview. */
val LocalWidgetTextStyle = staticCompositionLocalOf { TextStyle.Default }

/** Mirrors Glance's rows, including its solid metric pills and simulated raised edge. */
@Composable
fun WidgetPreviewStoryRow(
    model: StoryItemUiModel,
    configuration: WidgetConfiguration,
    fontFamily: FontFamily = FontFamily.SansSerif,
    paletteTintConfigKey: String = PaletteTintPreferences.DEFAULT,
) {
    val colors = HarmonicTheme.colors
    // RemoteViews truncates padding to physical pixels. Match thin Glance borders on devices
    // with fractional density instead of Compose's usual rounding to the nearest pixel.
    val outlineWidth = with(LocalDensity.current) { 1.dp.toPx().toInt().toDp() }
    val palette = rememberResourceTintPalette(model.previewImageFallback ?: model.faviconFallback)
    val targetBackground = when {
        configuration.displayStyle == DisplayStyle.FLAT -> colors.settingsPageBackground
        configuration.tint && palette != null -> Color(PreviewTintPolicy.calculateCardTint(colors.storyCardBackground.toArgb(), palette, paletteTintConfigKey))
        else -> colors.storyCardBackground
    }
    val background by animateColorAsState(targetBackground, tween(220), label = "Widget card tint")
    val frame by animateColorAsState(when (configuration.displayStyle) {
        DisplayStyle.OUTLINED -> colors.outlineVariant
        DisplayStyle.RAISED -> colors.outlineVariant.copy(alpha = 0.5f)
        else -> targetBackground
    }, tween(220), label = "Widget card frame")
    val outline by animateDpAsState(if (configuration.displayStyle == DisplayStyle.OUTLINED) outlineWidth else 0.dp, tween(220), label = "Widget outline")
    val raised by animateDpAsState(if (configuration.displayStyle == DisplayStyle.RAISED) 2.dp else 0.dp, tween(220), label = "Widget raised edge")
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wideEnoughForImages = maxWidth >= 280.dp
        val showIndex = maxWidth >= 240.dp
        Box(Modifier.fillMaxWidth().padding(horizontal = WidgetDimensions.cardHorizontalMargin)
            .padding(bottom = WidgetDimensions.cardSpacing).clip(shape).background(frame)
            .padding(outline).padding(bottom = raised)) {
            AnimatedContent(
                configuration.previewImageMode,
                modifier = Modifier.fillMaxWidth().clip(shape).background(background),
                transitionSpec = { (fadeIn(tween(220)) togetherWith fadeOut(tween(220))).using(SizeTransform(clip = false)) },
                label = "Widget image mode",
            ) { mode ->
                val medium = mode == StoryPreviewMode.MEDIUM && wideEnoughForImages
                val hasImage = mode != StoryPreviewMode.OFF && model.previewImageFallback != null && wideEnoughForImages
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = if (medium)
                        (if (hasImage) WidgetDimensions.mediumImageHeight else WidgetDimensions.mediumNoImageHeight) + 16.dp else 0.dp)) {
                        Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp, top = 12.dp, bottom = 12.dp)) {
                            if (showIndex) WidgetPreviewText(model.index, fontFamily, WidgetTypography.TITLE_SIZE - 1,
                                colors.textSecondary, Modifier.width(WidgetDimensions.indexWidth).testTag("widget-preview-index"))
                            Column(Modifier.weight(1f)) {
                                WidgetPreviewText(model.title, fontFamily, WidgetTypography.TITLE_SIZE, colors.storyNormal,
                                    Modifier.testTag("widget-preview-title"), bold = true, maxLines = 4)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(model.faviconFallback), null, Modifier.size(WidgetDimensions.faviconSize)
                                        .clip(RoundedCornerShape(WidgetDimensions.faviconCornerRadius)).alpha(if (model.tintFaviconFallback) 0.8f else 1f),
                                        tint = if (model.tintFaviconFallback) colors.textSecondary else Color.Unspecified)
                                    Spacer(Modifier.width(4.dp))
                                    WidgetPreviewText(listOfNotNull("${model.points} points".takeUnless { medium }, model.domain, model.age).joinToString(" · "),
                                        fontFamily, WidgetTypography.METADATA_SIZE, colors.textSecondary, maxLines = 2)
                                }
                            }
                        }
                    }
                    if (medium) {
                        Box(Modifier.fillMaxHeight().padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(if (hasImage) WidgetDimensions.mediumImageWidth else WidgetDimensions.mediumNoImageWidth)
                                .then(if (hasImage) Modifier.height(WidgetDimensions.mediumImageHeight).clip(RoundedCornerShape(10.dp)) else Modifier),
                                contentAlignment = if (hasImage) Alignment.BottomEnd else Alignment.CenterEnd) {
                                if (hasImage) Image(painterResource(model.previewImageFallback), null,
                                    Modifier.fillMaxSize().testTag("widget-preview-image"), contentScale = ContentScale.Crop)
                                if (hasImage) {
                                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        WidgetPreviewMetric(widgetMetricText(model.points), Res.drawable.ic_arrow_drop_up, fontFamily)
                                        Spacer(Modifier.width(4.dp))
                                        WidgetPreviewMetric(widgetMetricText(model.commentCount), Res.drawable.ic_comment, fontFamily)
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.End) {
                                        WidgetPreviewMetric(widgetMetricText(model.points), Res.drawable.ic_arrow_drop_up, fontFamily, onImage = false)
                                        Spacer(Modifier.height(4.dp))
                                        WidgetPreviewMetric(widgetMetricText(model.commentCount), Res.drawable.ic_comment, fontFamily, onImage = false)
                                    }
                                }
                            }
                        }
                    } else {
                        if (hasImage) {
                            Spacer(Modifier.width(6.dp))
                            Image(painterResource(model.previewImageFallback), null,
                                Modifier.width(72.dp).height(52.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.width(48.dp).fillMaxHeight().padding(horizontal = 6.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(Res.drawable.ic_comment), null, Modifier.size(18.dp).alpha(0.8f), tint = colors.storyNormal)
                            WidgetPreviewText(model.commentCount.toString(), fontFamily, WidgetTypography.COMMENT_COUNT_SIZE, colors.storyNormal, bold = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewMetric(value: String, icon: DrawableResource, family: FontFamily, onImage: Boolean = true) {
    val colors = HarmonicTheme.colors
    val points = icon == Res.drawable.ic_arrow_drop_up
    val outlineWidth = with(LocalDensity.current) { 1.dp.toPx().toInt().toDp() }
    Box(if (onImage) Modifier else Modifier.clip(RoundedCornerShape(20.dp)).background(colors.outlineVariant).padding(outlineWidth)) {
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(
            if (onImage) colors.storyCardBackground.copy(alpha = WidgetDimensions.metricBackgroundAlpha) else colors.surfaceContainerHighest)
            .padding(start = if (points) WidgetDimensions.pointsStartPadding else WidgetDimensions.metricStartPadding,
                end = WidgetDimensions.metricEndPadding, top = WidgetDimensions.metricVerticalPadding, bottom = WidgetDimensions.metricVerticalPadding),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(WidgetDimensions.metricIconSize).alpha(0.8f), tint = colors.storyNormal)
            if (!points) Spacer(Modifier.width(2.dp))
            WidgetPreviewText(value, family, WidgetTypography.COMMENT_COUNT_SIZE, colors.storyNormal, bold = true)
        }
    }
}

@Composable
private fun WidgetPreviewText(text: String, family: FontFamily, size: Float, color: Color, modifier: Modifier = Modifier, bold: Boolean = false, maxLines: Int = 1) {
    Text(text, modifier, color = color, fontFamily = family, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, style = LocalWidgetTextStyle.current)
}

fun widgetMetricText(value: Int): String = when {
    value >= 1_000_000 -> "${(value.toLong() + 500_000) / 1_000_000}m"
    value >= 10_000 -> "${(value + 500) / 1_000}k"
    value >= 1_000 -> "${value / 1_000}" + (value % 1_000 / 100).let { if (it == 0) "k" else ".$it" + "k" }
    else -> value.coerceAtLeast(0).toString()
}
