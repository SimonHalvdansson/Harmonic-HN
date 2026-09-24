package com.simon.harmonichackernews.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.layout.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.text.FontWeight
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.CommentsIntentExtras
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.presentation.WidgetStoryFormatter
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.ui.theme.HarmonicColors
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette
import com.simon.harmonichackernews.ui.widget.WidgetTypography
import com.simon.harmonichackernews.ui.widget.WidgetDimensions
import com.simon.harmonichackernews.ui.widget.widgetMetricText
import com.simon.harmonichackernews.utils.HtmlTextUtils
import com.simon.harmonichackernews.utils.AndroidActivityTheme
import com.simon.harmonichackernews.utils.HarmonicLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class StoriesGlanceWidget : GlanceAppWidget() {
    // The fill/weight layout resizes in the launcher without duplicating every collection row
    // into separate portrait/landscape RemoteViews (which can exhaust the host's Binder buffer).
    override val sizeMode = SizeMode.Single

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        HarmonicLog.debug("Widget rendering failed widgetId=$appWidgetId: $throwable")
        // Glance cannot compose its own error UI after a composition failure. Keep this fallback
        // small, actionable, and subject to the same explicit diagnostics preference as refresh errors.
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, widgetErrorViews(context, appWidgetId, throwable))
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val app = context.harmonicAppComposition
        provideContent {
            val state = currentState<Preferences>()
            val settings by app.settings.updates.collectAsState(initial = app.settings.snapshot())
            val selection by app.appearance.selections.collectAsState(initial = app.appearance.selection())
            val colors = remember(selection, settings.appearance, settings.general.specialNighttimeTheme) {
                val now = Calendar.getInstance()
                val scheduledNight = settings.general.specialNighttimeTheme && app.appearance.schedule.containsMinutes(
                    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                )
                if (settings.appearance.followSystem && !scheduledNight) {
                    WidgetColors(
                        widgetPalette(context, selection.copy(theme = settings.appearance.lightTheme, dark = false)),
                        widgetPalette(context, selection.copy(theme = settings.appearance.darkTheme, dark = true)),
                    )
                } else WidgetColors(widgetPalette(context, selection))
            }
            val configuration = app.widgets.configuration(widgetId)
            val headlineFamily = remember { widgetHeadlineFontFamily(context) }
            val fontFamily = if (configuration.useHeadlineFont && headlineFamily != null) FontFamily(headlineFamily) else FontFamily.SansSerif
            val width = state[WidgetState.width] ?: android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetOptions(widgetId).getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            val entries = remember(state[WidgetState.content]) { decodeWidgetEntries(state[WidgetState.content]) }
            val visuals by produceState(emptyMap<Int, WidgetVisual>(), entries, state[WidgetState.revision], colors, app.userSettings.story.paletteTintConfigKey) {
                value = withContext(Dispatchers.IO) {
                    entries.associate { entry ->
                        val image = entry.imagePath?.let(BitmapFactory::decodeFile)
                        val tintImage = if (entry.tintPath == entry.imagePath) image else entry.tintPath?.let(BitmapFactory::decodeFile)
                        val palette = tintImage?.let { bitmap ->
                            val sample = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
                            val pixels = IntArray(48 * 48)
                            sample.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                            HarmonicPaletteExtractor(pixels.size).extract(pixels).toPreviewTintPalette()
                        }
                        fun tint(theme: HarmonicColors) = palette?.let {
                            val config = app.userSettings.story.paletteTintConfigKey
                            val rawTint = PreviewTintPolicy.calculateCardTint(theme.contentCardBackground.toArgb(), it, config)
                            PreviewTintPolicy.ensureCardTintContrast(rawTint, theme.background.toArgb(), config)
                        }
                        entry.destination.storyId to WidgetVisual(image?.forWidget(), tint(colors.day),
                            entry.faviconPath?.let(BitmapFactory::decodeFile)?.roundedWidgetFavicon(context)?.forWidget(), tint(colors.night))
                    }
                }
            }
            Column(
                GlanceModifier.fillMaxSize().background(colors.background).appWidgetBackground()
                    .cornerRadius(24.dp),
            ) {
                val error = state[WidgetState.error]?.let {
                    if (settings.debug.showWidgetDebugInfo) it else "Could not refresh. Tap refresh to try again."
                }
                val updated = state[WidgetState.updated] ?: 0L
                val status = when {
                    state[WidgetState.refreshing] == true -> "Refreshing…"
                    error != null -> "Refresh failed"
                    updated > 0 -> app.platform.timeFormatting.time(updated).let {
                        if (width >= 280) "Updated $it" else it
                    }
                    else -> "Loading…"
                }
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(widgetStoriesIntent(context)))
                    .padding(start = 16.dp, end = 4.dp, top = WidgetDimensions.headerTopPadding, bottom = WidgetDimensions.headerBottomPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        configuration.storyType.label,
                        GlanceModifier.defaultWeight(),
                        style = TextStyle(color = colors.textPrimary, fontSize = WidgetTypography.HEADER_SIZE.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily),
                        maxLines = 1,
                    )
                    Text(status, GlanceModifier.padding(start = 8.dp),
                        style = TextStyle(color = colors.textSecondary, fontSize = WidgetTypography.METADATA_SIZE.sp, fontFamily = fontFamily),
                        maxLines = 1)
                    CircleIconButton(
                        ImageProvider(R.drawable.ic_refresh_widget),
                        if (state[WidgetState.refreshing] == true) "Refreshing stories" else "Refresh stories",
                        onClick = actionRunCallback<RefreshStoriesAction>(),
                        backgroundColor = null,
                        contentColor = colors.textPrimary,
                    )
                }
                if (error != null) {
                    Text(if (entries.isEmpty()) error else "Showing saved stories · $error",
                        GlanceModifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        style = TextStyle(color = colors.textSecondary, fontSize = WidgetTypography.METADATA_SIZE.sp, fontFamily = fontFamily),
                        maxLines = 4)
                }
                if (settings.debug.showWidgetDebugInfo) {
                    Text("Widget $widgetId · ${state[WidgetState.debug].orEmpty()}",
                        GlanceModifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                        style = TextStyle(color = colors.textSecondary, fontSize = 10.sp, fontFamily = fontFamily), maxLines = 2)
                }
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    itemsIndexed(entries, itemId = { _, entry -> entry.destination.storyId.toLong() }) { index, entry ->
                        WidgetStoryRow(context, entry, index, configuration, colors, visuals[entry.destination.storyId], width, fontFamily,
                            isLast = index == entries.lastIndex)
                    }
                }
            }
        }
    }
}

internal data class WidgetVisual(val image: Bitmap?, val tint: Int?, val favicon: Bitmap?, val nightTint: Int? = tint)

/** Day/night colors are resolved by the launcher even while the application process is stopped. */
internal data class WidgetColors(val day: HarmonicColors, val night: HarmonicColors = day) {
    val background = ColorProvider(day.background, night.background)
    val contentCardBackground = ColorProvider(day.contentCardBackground, night.contentCardBackground)
    val textPrimary = ColorProvider(day.textPrimary, night.textPrimary)
    val contentPrimary = ColorProvider(day.contentPrimary, night.contentPrimary)
    val textSecondary = ColorProvider(day.textSecondary, night.textSecondary)
    val outlineVariant = ColorProvider(day.outlineVariant, night.outlineVariant)
    val raisedFrame = ColorProvider(day.outlineVariant.copy(alpha = 0.5f), night.outlineVariant.copy(alpha = 0.5f))
    val metricBackground = ColorProvider(day.contentCardBackground.copy(alpha = WidgetDimensions.metricBackgroundAlpha), night.contentCardBackground.copy(alpha = WidgetDimensions.metricBackgroundAlpha))
    val standaloneMetricBackground = ColorProvider(day.surfaceContainerHighest, night.surfaceContainerHighest)
}

private fun widgetPalette(context: Context, selection: ThemeSelection): HarmonicColors {
    val configuration = Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (selection.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), AndroidActivityTheme.themeResource(selection.theme, selection.dark))
    return harmonicThemePalette(themed, selection).colors
}

@Composable
internal fun WidgetStoryRow(context: Context, entry: WidgetEntry, index: Int, configuration: WidgetConfiguration, colors: WidgetColors, visual: WidgetVisual?, availableWidth: Int = 360, fontFamily: FontFamily = FontFamily.SansSerif, isLast: Boolean = false) {
    val snapshot = entry.destination.seed?.story ?: return
    val preferences = context.harmonicAppComposition.userSettings.story
    val style = configuration.displayStyle
    val background = when {
        style == DisplayStyle.FLAT -> colors.background
        configuration.tint && visual?.tint != null -> ColorProvider(Color(visual.tint), Color(visual.nightTint ?: visual.tint))
        else -> colors.contentCardBackground
    }
    val frame = when (style) {
        DisplayStyle.OUTLINED -> colors.outlineVariant
        DisplayStyle.RAISED -> colors.raisedFrame
        else -> background
    }
    val medium = configuration.previewImageMode == StoryPreviewMode.MEDIUM && availableWidth >= 280
    val hasImage = configuration.previewImageMode != StoryPreviewMode.OFF && visual?.image != null && availableWidth >= 280
    val commentsAction = actionStartActivity(widgetStoryIntent(context, entry.destination.copy(showWebsite = false)))
    val text = WidgetStoryFormatter.format(snapshot, index, preferences.includeTopLevelDomain, System.currentTimeMillis())
    val metadata = if (medium) text.metadata.substringAfter(" · ") else text.metadata.replace(" pts", " points")
    Box(GlanceModifier.fillMaxWidth().padding(horizontal = WidgetDimensions.cardHorizontalMargin)
        .padding(bottom = WidgetDimensions.cardSpacing + if (isLast) WidgetDimensions.listBottomPadding else 0.dp)) {
        Box(GlanceModifier.fillMaxWidth().background(frame).cornerRadius(8.dp)
            .padding(if (style == DisplayStyle.OUTLINED) 1.dp else 0.dp)
            .padding(bottom = if (style == DisplayStyle.RAISED) 2.dp else 0.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().background(background).cornerRadius(8.dp)
                    .clickable(actionStartActivity(widgetStoryIntent(context, entry.destination))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
                    // A match-parent comments rail does not contribute to a LinearLayout's wrap
                    // height. Reserve the image height explicitly so one-line titles cannot crop it.
                    if (medium && !snapshot.isComment) Spacer(GlanceModifier.height(
                        (if (hasImage) WidgetDimensions.mediumImageHeight else WidgetDimensions.mediumNoImageHeight) + 16.dp))
                    Column(GlanceModifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 12.dp)) {
                        Row {
                            if (preferences.showIndex && availableWidth >= 240) Text(text.index, GlanceModifier.width(WidgetDimensions.indexWidth),
                                style = TextStyle(color = colors.textSecondary, fontSize = (WidgetTypography.TITLE_SIZE - 1).sp, fontFamily = fontFamily))
                            Column(GlanceModifier.defaultWeight()) {
                                val title = if (snapshot.isComment) HtmlTextUtils.plainText(snapshot.text).take(220) else text.title
                                Text(title, style = TextStyle(color = colors.contentPrimary, fontSize = WidgetTypography.TITLE_SIZE.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily), maxLines = 4)
                                Spacer(GlanceModifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (preferences.showFavicons && !snapshot.isComment) {
                                        Image(visual?.favicon?.let(::ImageProvider) ?: ImageProvider(R.drawable.ic_public), null,
                                            GlanceModifier.size(WidgetDimensions.faviconSize).cornerRadius(WidgetDimensions.faviconCornerRadius),
                                            colorFilter = if (visual?.favicon == null) ColorFilter.tint(colors.textSecondary) else null)
                                        Spacer(GlanceModifier.width(4.dp))
                                    }
                                    Text(if (snapshot.isComment) "${snapshot.author.orEmpty()} · ${text.metadata.substringAfterLast(" · ")}" else metadata,
                                        style = TextStyle(color = colors.textSecondary, fontSize = WidgetTypography.METADATA_SIZE.sp, fontFamily = fontFamily), maxLines = 2)
                                }
                            }
                        }
                    }
                }
                if (medium && !snapshot.isComment) {
                    Box(GlanceModifier.fillMaxHeight().clickable(commentsAction).padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                        Box(GlanceModifier.width(if (hasImage) WidgetDimensions.mediumImageWidth else WidgetDimensions.mediumNoImageWidth)
                            .then(if (hasImage) GlanceModifier.height(WidgetDimensions.mediumImageHeight).cornerRadius(10.dp) else GlanceModifier),
                            contentAlignment = if (hasImage) Alignment.BottomEnd else Alignment.CenterEnd) {
                            if (hasImage) Image(ImageProvider(visual.image), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (hasImage) {
                                Row(GlanceModifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    WidgetMetric(widgetMetricText(snapshot.score), R.drawable.ic_arrow_drop_up, "${snapshot.score} points", colors, fontFamily)
                                    Spacer(GlanceModifier.width(4.dp))
                                    WidgetMetric(widgetMetricText(snapshot.descendantCount), R.drawable.ic_comment, "${snapshot.descendantCount} comments", colors, fontFamily)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.End) {
                                    WidgetMetric(widgetMetricText(snapshot.score), R.drawable.ic_arrow_drop_up, "${snapshot.score} points", colors, fontFamily, onImage = false)
                                    Spacer(GlanceModifier.height(4.dp))
                                    WidgetMetric(widgetMetricText(snapshot.descendantCount), R.drawable.ic_comment, "${snapshot.descendantCount} comments", colors, fontFamily, onImage = false)
                                }
                            }
                        }
                    }
                } else {
                    if (hasImage) {
                        Spacer(GlanceModifier.width(6.dp))
                        Image(ImageProvider(visual.image), null, GlanceModifier.width(72.dp).height(52.dp).cornerRadius(6.dp), contentScale = ContentScale.Crop)
                    }
                    if (!snapshot.isComment) {
                        Column(GlanceModifier.width(48.dp).fillMaxHeight().clickable(commentsAction).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(ImageProvider(R.drawable.ic_comment), "Open comments", GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(colors.contentPrimary))
                            Text(snapshot.descendantCount.toString(), style = TextStyle(color = colors.contentPrimary, fontSize = WidgetTypography.COMMENT_COUNT_SIZE.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetMetric(value: String, icon: Int, description: String, colors: WidgetColors, fontFamily: FontFamily, onImage: Boolean = true) {
    val points = icon == R.drawable.ic_arrow_drop_up
    Box(if (onImage) GlanceModifier else GlanceModifier.background(colors.outlineVariant).cornerRadius(20.dp).padding(1.dp)) {
        Row(GlanceModifier.background(if (onImage) colors.metricBackground else colors.standaloneMetricBackground).cornerRadius(20.dp)
            .padding(start = if (points) WidgetDimensions.pointsStartPadding else WidgetDimensions.metricStartPadding,
                end = WidgetDimensions.metricEndPadding, top = WidgetDimensions.metricVerticalPadding, bottom = WidgetDimensions.metricVerticalPadding),
            verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(icon), description, GlanceModifier.size(WidgetDimensions.metricIconSize), colorFilter = ColorFilter.tint(colors.contentPrimary))
            if (!points) Spacer(GlanceModifier.width(2.dp))
            Text(value, style = TextStyle(color = colors.contentPrimary, fontSize = WidgetTypography.COMMENT_COUNT_SIZE.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily), maxLines = 1)
        }
    }
}

internal const val ACTION_OPEN_WIDGET_STORIES = "com.simon.harmonichackernews.action.OPEN_WIDGET_STORIES"

internal fun widgetStoriesIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    action = ACTION_OPEN_WIDGET_STORIES
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}

internal fun widgetStoryIntent(context: Context, destination: StoryDestination): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // Distinct identities prevent one row's pending intent from overwriting another's.
        data = Uri.parse("harmonic-widget://story/${destination.storyId}/${destination.showWebsite}")
        // A widget needs only a compact navigation seed. Repeating full comment trees and legacy
        // bundles in every collection click can overflow the launcher's asynchronous Binder budget.
        val compact = destination.copy(seed = destination.seed?.let { seed ->
            seed.copy(story = seed.story.copy(text = null, childIds = emptyList(), pollOptionIds = emptyList()))
        })
        putExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA, AppDestinationCodec.encode(compact))
        putExtra(CommentsIntentExtras.EXTRA_ID, destination.storyId)
        putExtra(CommentsIntentExtras.EXTRA_SHOW_WEBSITE, destination.showWebsite)
    }

class RefreshStoriesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefreshWorker.enqueue(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

/** Parcel immutable images by shared-memory handle instead of repeating pixels in Binder. */
internal fun Bitmap.forWidget(): Bitmap = if (Build.VERSION.SDK_INT >= 31) asShared() else this

/** Bake the clipping into the bitmap so favicon corners also work before Android 12. */
internal fun Bitmap.roundedWidgetFavicon(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (WidgetDimensions.faviconSize.value * density).toInt().coerceAtLeast(1)
    val radius = WidgetDimensions.faviconCornerRadius.value * density
    val shader = BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(size.toFloat() / width, size.toFloat() / height) })
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
    }
}

internal fun widgetFailureDescription(error: Throwable?): String = error?.let {
    listOfNotNull(it::class.simpleName, it.message?.takeIf(String::isNotBlank)).joinToString(": ")
}?.take(300) ?: "No items returned"

internal fun widgetErrorViews(context: Context, widgetId: Int, error: Throwable): RemoteViews {
    val app = context.harmonicAppComposition
    val selection = app.appearance.selection()
    val themed = ContextThemeWrapper(context, AndroidActivityTheme.themeResource(selection.theme, selection.dark))
    val colors = harmonicThemePalette(themed, selection).colors
    val message = if (app.userSettings.debug.showWidgetDebugInfo) {
        "Widget $widgetId could not render\n${widgetFailureDescription(error)}\nTap to retry."
    } else "Could not display stories. Tap to retry."
    val retry = Intent(context, StoriesWidgetProvider::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
    return RemoteViews(context.packageName, R.layout.widget_error).apply {
        setTextViewText(R.id.widget_error_message, message)
        setTextColor(R.id.widget_error_message, colors.textPrimary.toArgb())
        setInt(R.id.widget_error_message, "setBackgroundColor", colors.background.toArgb())
        setOnClickPendingIntent(R.id.widget_error_message,
            PendingIntent.getBroadcast(context, widgetId, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}
