package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.palette1
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.CommentPreferences
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.StoryPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.common.platformDialogProperties
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowModel
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.rememberResourceTintPalette
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.ui.content.contentTween
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground

@Composable
fun WelcomeScreen(
    styleChooser: Boolean,
    initialExpressive: Boolean,
    storyPreferences: StoryPreferences,
    commentPreferences: CommentPreferences,
    onApplyPreset: (expressive: Boolean) -> Unit,
    onDismiss: () -> Unit,
    filterButtonColors: HarmonicFilterButtonColors,
    launcherIcon: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = platformDialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // The full-screen surface owns its insets through Scaffold, rather than
            // leaving a dimmed frame around the device's safe area.
            usePlatformInsets = false,
        ),
    ) {
        WelcomeScreenContent(
            styleChooser = styleChooser,
            initialExpressive = initialExpressive,
            storyPreferences = storyPreferences,
            commentPreferences = commentPreferences,
            onApplyPreset = onApplyPreset,
            filterButtonColors = filterButtonColors,
            launcherIcon = launcherIcon,
        )
    }
}

@Composable
internal fun WelcomeScreenContent(
    styleChooser: Boolean,
    initialExpressive: Boolean,
    storyPreferences: StoryPreferences,
    commentPreferences: CommentPreferences,
    onApplyPreset: (Boolean) -> Unit,
    filterButtonColors: HarmonicFilterButtonColors,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    launcherIcon: @Composable () -> Unit,
) {
    var expressive by rememberSaveable(initialExpressive) { mutableStateOf(initialExpressive) }
    val welcomeTextStyle = LocalSettingsPlatformStyle.current.textStyle
    val layoutDirection = LocalLayoutDirection.current

    val density = LocalDensity.current
    var actionHeight by remember { mutableStateOf(56.dp) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("welcome-surface"),
        contentWindowInsets = contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.pageBackground,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onApplyPreset(expressive) },
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 320.dp)
                    .onSizeChanged { actionHeight = with(density) { it.height.toDp() } }
                    .testTag("welcome-action"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = if (styleChooser) "Apply" else "Get started",
                    fontFamily = GoogleSansFlexRoundedFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = welcomeTextStyle,
                )
            }
        },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val topSpacing = if (maxWidth >= 600.dp) 48.dp else 24.dp
            LazyColumn(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()
                    .consumeWindowInsets(insets).testTag("welcome-content"),
                contentPadding = PaddingValues(
                    start = insets.calculateStartPadding(layoutDirection) + 24.dp,
                    end = insets.calculateEndPadding(layoutDirection) + 24.dp,
                    top = insets.calculateTopPadding() + topSpacing,
                    bottom = insets.calculateBottomPadding() + actionHeight + 40.dp,
                ),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!styleChooser) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                                launcherIcon()
                            }
                        }
                        Text(
                            text = if (styleChooser) "Choose your style" else "Welcome to Harmonic",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            style = welcomeTextStyle,
                        )
                    }
                }
                item {
                    Text(
                        text = if (styleChooser) {
                            "Choose a preset for the app. This changes the font, " +
                                "story images, palette tint, comment style and profile images."
                        } else {
                            "I hope you'll love the app and feel how much care was put " +
                                "into creating it. If you have an issue or want to submit " +
                                "a PR, you can visit the GitHub repository.\n\nBefore we " +
                                "get started you can select a UI preset below."
                        },
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansFlexRoundedFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        style = welcomeTextStyle,
                    )
                }
                item {
                    WelcomePreviewArea(
                        expressive = expressive,
                        storyPreferences = storyPreferences,
                        commentPreferences = commentPreferences,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 16.dp)
                            .testTag("welcome-presets"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        StylePresetButton(
                            label = "Expressive",
                            selected = expressive,
                            position = 0,
                            colors = filterButtonColors,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            onClick = { expressive = true },
                            modifier = Modifier.weight(1f),
                        )
                        StylePresetButton(
                            label = "Simple",
                            selected = !expressive,
                            position = 1,
                            colors = filterButtonColors,
                            fontFamily = ProductSansFontFamily,
                            onClick = { expressive = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!styleChooser) {
                    item {
                        Text(
                            text = "There are a ton of customization options beyond " +
                                "this available in the settings for you to explore.",
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            style = welcomeTextStyle,
                        )
                    }
                }
            }
        }
    }
}

/** Reserve the taller preset at the current width and font scale, placing only the selected one. */
@Composable
private fun WelcomePreviewArea(
    expressive: Boolean,
    storyPreferences: StoryPreferences,
    commentPreferences: CommentPreferences,
) {
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("welcome-preview"),
        content = {
            for (previewExpressive in listOf(true, false)) {
                Column {
                    WelcomeStoryPreview(previewExpressive, storyPreferences)
                    WelcomeCommentPreview(previewExpressive, commentPreferences)
                }
            }
        },
    ) { measurables, constraints ->
        val previews = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, previews.maxOf { it.height }) {
            previews[if (expressive) 0 else 1].placeRelative(0, 0)
        }
    }
}

@Composable
private fun StylePresetButton(
    label: String,
    selected: Boolean,
    position: Int,
    colors: HarmonicFilterButtonColors,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonicFilterButton(
        label = label,
        selected = selected,
        position = position,
        colors = colors,
        onClick = onClick,
        modifier = modifier,
        fontFamily = fontFamily,
        lastPosition = 1,
    )
}

@Composable
private fun WelcomeStoryPreview(
    expressive: Boolean,
    storyPreferences: StoryPreferences,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.cardBackground
    val paletteTintConfigKey = storyPreferences.paletteTintConfigKey
    val palette = rememberResourceTintPalette(Res.drawable.palette1)
    val rawTint = remember(palette, paletteTintConfigKey, baseColor) {
        PreviewTintPolicy.calculateCardTint(
            baseColor = baseColor.toArgb(),
            palette = palette,
            modeOrConfigKey = paletteTintConfigKey,
        )
    }
    val model = SettingsStoryPreviewModel.copy(
        title = "Towards More Efficient Long-Context Language Modeling",
        previewText = "",
        previewImageFallback = Res.drawable.palette1,
        previewImageTintArgb = rawTint,
        tintFallbackArgb = baseColor.toArgb(),
    )
    // Match the preferences changed by applyWelcomePreset, retaining the user's other options.
    val preferences = storyPreferences.copy(
        displayStyle = if (expressive) DisplayStyle.STANDARD else DisplayStyle.FLAT,
        tintCardsFromImages = expressive,
        font = if (expressive) "googlesansflexrounded" else "productsans",
        previewImageMode = if (expressive) StoryPreviewMode.MEDIUM else StoryPreviewMode.OFF,
    )
    val previewGutter by animateDpAsState(
        targetValue = if (expressive) 4.dp else 0.dp,
        animationSpec = contentTween(),
        label = "welcome preview gutter",
    )
    StoryRow(
        model = model,
        style = StoryDisplaySettings.from(preferences).toStoryRowStyle(
            StoryRowStyleContext(
                score = model.points,
                commentCount = model.commentCount,
                isRead = false,
                previewTextAvailable = false,
            ),
        ),
        modifier = modifier,
        // Both rows already have an 8dp outer margin; match CommentRow's surface gutter.
        cardPadding = PaddingValues(horizontal = previewGutter, vertical = 4.dp),
        pageBackground = MaterialTheme.colorScheme.pageBackground,
    )
}

private val WelcomeComment = CommentRowModel(
    author = "alex",
    age = "1h",
    body = "I'd love to see how this performs on a whole codebase. " +
        "Finding the right context is often harder than fitting it into the window.",
    referenceMarker = "",
    referenceUrl = "",
)

@Composable
private fun WelcomeCommentPreview(expressive: Boolean, preferences: CommentPreferences) {
    CommentRow(
        model = WelcomeComment,
        style = CommentRowStyle(
            displayStyle = if (expressive) DisplayStyle.STANDARD else DisplayStyle.FLAT,
            textSize = preferences.textSize,
            collectLinks = false,
            emphasizeMeta = preferences.highlightMetadata,
            depthIndicatorMode = CommentDepthPreferences.NONE,
            showDivider = false,
            preferredFont = if (expressive) "googlesansflexrounded" else "productsans",
            userAvatarsEnabled = expressive,
            userAvatarOptions = preferences.userAvatarOptions,
            markNewComments = false,
        ),
    )
}
