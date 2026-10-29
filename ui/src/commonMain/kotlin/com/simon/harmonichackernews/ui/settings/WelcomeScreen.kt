package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_forward
import com.simon.harmonichackernews.resources.ic_check
import com.simon.harmonichackernews.resources.palette1
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.CommentPreferences
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.StoryPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
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
import com.simon.harmonichackernews.ui.navigation.LocalActivityNavigationForwardExit
import org.jetbrains.compose.resources.painterResource

@Composable
fun WelcomeScreen(
    styleChooser: Boolean,
    initialExpressive: Boolean,
    storyPreferences: StoryPreferences,
    commentPreferences: CommentPreferences,
    onApplyPreset: (expressive: Boolean) -> Unit,
    filterButtonColors: HarmonicFilterButtonColors,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    launcherIcon: @Composable () -> Unit,
) {
    val forwardExit = LocalActivityNavigationForwardExit.current
    WelcomeScreenContent(
        styleChooser = styleChooser,
        initialExpressive = initialExpressive,
        storyPreferences = storyPreferences,
        commentPreferences = commentPreferences,
        onApplyPreset = { expressive ->
            forwardExit()
            onApplyPreset(expressive)
        },
        filterButtonColors = filterButtonColors,
        contentWindowInsets = contentWindowInsets,
        launcherIcon = launcherIcon,
    )
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
    entranceTime: State<Double> = rememberWelcomeAnimationTime(),
    launcherIcon: @Composable () -> Unit,
) {
    var expressive by rememberSaveable(initialExpressive) { mutableStateOf(initialExpressive) }
    val welcomeTextStyle = LocalSettingsPlatformStyle.current.textStyle
    val layoutDirection = LocalLayoutDirection.current

    val density = LocalDensity.current
    var actionHeight by remember { mutableStateOf(56.dp) }

    Box(Modifier.fillMaxSize()) {
        WelcomeBackground(entranceTime, Modifier.matchParentSize())
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("welcome-surface"),
            contentWindowInsets = contentWindowInsets,
            containerColor = Color.Transparent,
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onApplyPreset(expressive) },
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 320.dp)
                        .onSizeChanged { actionHeight = with(density) { it.height.toDp() } }
                        .welcomeEntrance(entranceTime, order = 3)
                        .testTag("welcome-action"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    if (styleChooser) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp).size(24.dp),
                        )
                    }
                    Text(
                        text = if (styleChooser) "Apply" else "Get started",
                        fontFamily = GoogleSansFlexRoundedFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = welcomeTextStyle,
                    )
                    if (!styleChooser) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_forward),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp).size(24.dp),
                        )
                    }
                }
            },
        ) { insets ->
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val topSpacing = if (maxWidth >= 600.dp) 48.dp else 24.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .consumeWindowInsets(insets).testTag("welcome-content"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        start = insets.calculateStartPadding(layoutDirection),
                        end = insets.calculateEndPadding(layoutDirection),
                        top = insets.calculateTopPadding() + topSpacing,
                        bottom = insets.calculateBottomPadding() + actionHeight + 40.dp,
                    ),
                ) {
                    item {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .testTag("welcome-content-column").padding(horizontal = 16.dp)) {
                            WelcomeAnimatedHeader(
                                time = entranceTime,
                                title = if (styleChooser) "Choose your style" else "Welcome to Harmonic",
                                subtitle = if (styleChooser) {
                                    "Choose a preset for the app. This changes the font, " +
                                        "story images, palette tint, comment style and profile images."
                                } else {
                                    "Choose a style to get started."
                                },
                                launcherIcon = launcherIcon,
                            )
                        }
                    }
                    item {
                        WelcomePreviewArea(
                            expressive = expressive,
                            storyPreferences = storyPreferences,
                            commentPreferences = commentPreferences,
                            entranceTime = entranceTime,
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                                .padding(horizontal = 16.dp).padding(top = 16.dp)
                                .selectableGroup()
                                .welcomeEntrance(entranceTime, order = 2)
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

                }
            }
        }
    }
}

@Composable
internal fun Modifier.welcomeEntrance(time: State<Double>, order: Int): Modifier {
    val layer = rememberGraphicsLayer()
    return drawWithContent {
        val alpha = WelcomeHeaderMotion.contentAlpha(time.value, order)
        if (alpha <= 0f) return@drawWithContent
        if (alpha >= 1f) {
            drawContent()
        } else {
            // Fade the complete surface, including elevation beyond the button's layout bounds.
            val outset = 16.dp.roundToPx()
            layer.setOutsets(outset, outset, outset, outset)
            layer.record { this@drawWithContent.drawContent() }
            layer.alpha = alpha
            drawLayer(layer)
        }
    }
}

/** Measure both presets to reserve space, retaining one live preview for settings-style motion. */
@Composable
private fun WelcomePreviewArea(
    expressive: Boolean,
    storyPreferences: StoryPreferences,
    commentPreferences: CommentPreferences,
    entranceTime: State<Double>,
) {
    Layout(
        // The shared rows add 12dp around their filled surfaces, giving a total 16dp margin.
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 4.dp).padding(top = 16.dp)
            .testTag("welcome-preview"),
        content = {
            Column(Modifier.testTag("welcome-preview-content")) {
                WelcomeStoryPreview(
                    expressive, storyPreferences,
                    modifier = Modifier.testTag("welcome-story-preview").welcomeEntrance(entranceTime, order = 0),
                )
                WelcomeCommentPreview(
                    expressive, commentPreferences,
                    modifier = Modifier.testTag("welcome-comment-preview").welcomeEntrance(entranceTime, order = 1),
                )
            }
            for (previewExpressive in listOf(true, false)) {
                Column {
                    WelcomeStoryPreview(previewExpressive, storyPreferences, animateChanges = false)
                    WelcomeCommentPreview(previewExpressive, commentPreferences, animateChanges = false)
                }
            }
        },
    ) { measurables, constraints ->
        val previews = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val reservedHeight = previews.drop(1).maxOf { it.height }
        layout(constraints.maxWidth, reservedHeight) {
            val livePreview = previews.first()
            livePreview.placeRelative(0, (reservedHeight - livePreview.height) / 2)
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
    animateChanges: Boolean = true,
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
    val targetGutter = if (expressive) 4.dp else 0.dp
    val previewGutter = if (animateChanges) {
        val gutter by animateDpAsState(
            targetValue = targetGutter,
            animationSpec = contentTween(),
            label = "welcome preview gutter",
        )
        gutter
    } else {
        targetGutter
    }
    StoryRow(
        model = model,
        style = StoryDisplaySettings.from(preferences).toStoryRowStyle(
            StoryRowStyleContext(
                score = model.points,
                commentCount = model.commentCount,
                isRead = false,
                previewTextAvailable = false,
            ),
        ).copy(transparentNonCardBackground = true),
        modifier = modifier,
        animateChanges = animateChanges,
        // Both rows already have an 8dp outer margin; match CommentRow's surface gutter.
        cardPadding = PaddingValues(horizontal = previewGutter, vertical = 4.dp),
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
private fun WelcomeCommentPreview(
    expressive: Boolean,
    preferences: CommentPreferences,
    animateChanges: Boolean = true,
    modifier: Modifier = Modifier,
) {
    CommentRow(
        model = WelcomeComment,
        modifier = modifier,
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
            animateChanges = animateChanges,
            transparentNonCardBackground = true,
        ),
    )
}
