package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.presentation.CommentMenuAction
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.presentation.CommentsShareAction
import com.simon.harmonichackernews.presentation.CommentsSheetAction
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AppSettings
import com.simon.harmonichackernews.settings.CommentsHeaderButton
import com.simon.harmonichackernews.ui.comments.icon
import com.simon.harmonichackernews.settings.CommentsHeaderImage
import com.simon.harmonichackernews.settings.CommentsHeaderEdge
import com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo
import com.simon.harmonichackernews.settings.CommentsHeaderTint
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import androidx.compose.runtime.saveable.rememberSaveable
import com.simon.harmonichackernews.ui.comments.CommentsHeader
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.summary.AiSummaryAvailabilityPolicy
import kotlinx.coroutines.delay
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.comments.commentsHazeSource
import com.simon.harmonichackernews.ui.comments.currentCommentsHazeState
import com.simon.harmonichackernews.ui.comments.headerPreviewImageGestures
import com.simon.harmonichackernews.ui.comments.ZoomableImageViewport
import com.simon.harmonichackernews.ui.comments.previewImageViewport
import com.simon.harmonichackernews.ui.common.TransformOverlay
import com.simon.harmonichackernews.ui.common.platformDialogProperties
import com.simon.harmonichackernews.ui.common.PlatformDialogBackgroundDimAmount
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.common.PlatformDialogDimHost
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeaderDisplaySettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
    statusBarProtection: @Composable (Color, Dp) -> Unit = { _, _ -> },
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val platformStyle = LocalSettingsPlatformStyle.current
    val account by dependencies.platform.accounts.accountState.collectAsStateWithLifecycle()
    val aiSettings by dependencies.aiSummarySettings.updates.collectAsStateWithLifecycle(
        initialValue = dependencies.aiSummarySettings.snapshot(),
    )
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val state = presenter.state(settings)
    val displaySettings = CommentDisplaySettings.from(
        preferences = settings.comments,
        showInvert = false,
        isTablet = false,
        hasAccountDetails = account.accountOrNull != null,
        canProvideSummary = settings.debug.mockAiAnswers || AiSummaryAvailabilityPolicy.canProvideSummary(
            explicitlyEnabled = aiSettings.explicitlyEnabled,
            mode = aiSettings.mode.storedValue,
            localAvailable = dependencies.localSummaryEngine?.isReady() == true,
            cloudApiKeyAvailable = aiSettings.apiKey.isNotBlank(),
        ),
    ).let { display ->
        display.copy(showUpButton = !platformStyle.navigationInSideRail &&
            (platformStyle.backButtonRequired || display.showUpButton))
    }

    var sampleIndex by rememberSaveable { mutableIntStateOf(0) }
    val sample = HeaderPreviewSample.entries[sampleIndex]
    val listState = rememberSettingsListState()
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val navigationBarInsets = WindowInsets.navigationBars
    var previewCanPin by remember(windowSize, density.density, density.fontScale) { mutableStateOf(true) }
    LaunchedEffect(listState, windowSize, density.density, density.fontScale) {
        snapshotFlow { listState.canScrollBackward }.collect { scrolled ->
            if (!scrolled) previewCanPin = true
        }
    }
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val background = MaterialTheme.colorScheme.pageBackground
    var headerColor by remember { mutableStateOf(background) }
    var overflowButtons by remember { mutableStateOf(emptyList<CommentsHeaderButton>()) }

    SettingsPage(
        title = stringResource(Res.string.settings_section_header_display),
        showNavigation = true,
        showTopAppBar = false,
        onBack = onBack,
        contentVersion = settings.hashCode() + sampleIndex,
        pinnedContentBackground = Color.Transparent,
        extraBottomPadding = 72.dp,
        listState = listState,
        horizontalInsets = WindowInsets(0),
        previewCanPin = previewCanPin,
        topOverlay = {
            HeaderSettingsStatusBarProtection(listState, headerColor,
                previewCanPin = previewCanPin, protection = statusBarProtection)
            HeaderButtonOverflowSnackbar(overflowButtons)
            if (platformStyle.backButtonRequired && !platformStyle.navigationInSideRail) {
                // iOS navigation must remain reachable even when a tall preview scrolls away.
                CommentsUpButton(
                    onClick = onBack,
                    modifier = Modifier
                        .windowInsetsPadding(horizontalInsets)
                        .padding(
                            start = 16.dp,
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp,
                        )
                        .zIndex(1f),
                )
            }
        },
        floatingContent = {
            Box(Modifier.windowInsetsPadding(horizontalInsets)) {
                HeaderPreviewSampleSelector(sample) {
                    previewCanPin = headerPreviewPinningAfterSampleChange(listState, previewCanPin,
                        navigationBarInsets.getBottom(density))
                    sampleIndex = it.ordinal
                }
            }
        },
        pinnedContent = {
            HeaderDisplayPreview(displaySettings, settings.general.bookmarksEnabled, onBack, sample,
                onHeaderColorChanged = { headerColor = it },
                onButtonsOverflowChanged = { overflowButtons = it },
                showBackButton = !platformStyle.backButtonRequired)
        },
    ) {
        item {
            Box(Modifier.windowInsetsPadding(horizontalInsets)) {
                SettingsCategory("Layout") {
                    SegmentedSetting(
                        title = "Preview image",
                        options = CommentsHeaderImage.entries.map { it to it.label },
                        selected = CommentsHeaderImage.from(state.headerPreviewImage, settings.comments.headerLayout),
                        enabled = state.storyPreviewEnabled,
                        onSelected = repository::setCommentsHeaderImage,
                    )
                    SettingsDivider()
                    SegmentedSetting(
                        title = "Post details",
                        options = CommentsHeaderMetaInfo.entries.map { it to it.label },
                        selected = settings.comments.headerMetaInfo,
                        disabledOptions = buildSet {
                            if (!displaySettings.hasAccountDetails) add(CommentsHeaderMetaInfo.BUTTONS)
                        },
                        onSelected = repository::setCommentsHeaderMetaInfo,
                    )
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Domain",
                        icon = Res.drawable.ic_public,
                        checked = settings.comments.showHeaderDomain,
                        onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.HEADER_DOMAIN, it) },
                    )
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Thumbnail",
                        enabled = settings.comments.showHeaderDomain,
                        summary = if (!settings.comments.showHeaderDomain) "Turn on Domain" else null,
                        icon = Res.drawable.ic_public,
                        checked = settings.comments.showFavicons,
                        onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.HEADER_FAVICON, it) },
                    )
                    if (!platformStyle.backButtonRequired) {
                        SettingsDivider()
                        SwitchSettingRow(
                            title = "Back",
                            icon = Res.drawable.ic_arrow_back,
                            checked = state.showUpButton,
                            onCheckedChange = { presenter.setBoolean(CommentsBooleanSetting.ShowUpButton, it) },
                        )
                    }

                }
            }
        }
        item {
            Box(Modifier.windowInsetsPadding(horizontalInsets)) {
                HeaderButtonSettings(settings, displaySettings,
                    repository::setCommentsHeaderButton, repository::resetCommentsHeaderButtons)
            }
        }
        item {
            Box(Modifier.windowInsetsPadding(horizontalInsets)) {
                SettingsCategory("Background") {
                    SegmentedSetting(
                        title = "Background tint",
                        options = CommentsHeaderTint.entries.map { it to it.label },
                        selected = settings.comments.headerTint,
                        onSelected = repository::setCommentsHeaderTint,
                    )
                    SettingsDivider()
                    SegmentedSetting(
                        title = "Bottom edge",
                        options = CommentsHeaderEdge.entries.map { it to it.label },
                        selected = settings.comments.headerEdge,
                        enabled = displaySettings.tintHeader,
                        onSelected = repository::setCommentsHeaderEdge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeaderButtonSettings(
    settings: AppSettings,
    displaySettings: CommentDisplaySettings,
    onButtonChanged: (CommentsHeaderButton, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val large = displaySettings.hasAccountDetails && displaySettings.combineHeaderActions
    val buttons = CommentsHeaderButton.entries.filter { it.configurable }
    val reasons = buttons.associateWith { button ->
        when {
            button in listOf(CommentsHeaderButton.COMMENT, CommentsHeaderButton.VOTE,
                CommentsHeaderButton.FAVORITE) && !displaySettings.hasAccountDetails -> "Sign in to use"
            button in listOf(CommentsHeaderButton.USER, CommentsHeaderButton.COMMENT,
                CommentsHeaderButton.VOTE) && large -> "Included in Large post details"
            button == CommentsHeaderButton.BOOKMARK && !settings.general.bookmarksEnabled -> "Turn on Bookmarks"
            button == CommentsHeaderButton.SUMMARIZE && !displaySettings.canProvideSummary -> "Set up AI summaries"
            button == CommentsHeaderButton.SIDE_BY_SIDE && !settings.appearance.sideBySideEnabled -> "Turn on side by side"
            button == CommentsHeaderButton.DISABLE_AD_BLOCK && !settings.reading.blockAds -> "Turn on ad blocking"
            else -> null
        }
    }
    fun isSelected(button: CommentsHeaderButton) =
        settings.comments.headerButtons.isShown(button, displaySettings.hasAccountDetails, large)
    SettingsCategory("Buttons") {
        buttons.forEachIndexed { index, button ->
            if (index > 0) SettingsDivider()
            val checked = isSelected(button)
            val reason = reasons[button]
            val context = when (button) {
                CommentsHeaderButton.SUMMARIZE, CommentsHeaderButton.ARCHIVE -> "On link posts"
                CommentsHeaderButton.SIDE_BY_SIDE -> "On supported layouts"
                CommentsHeaderButton.DISABLE_AD_BLOCK -> "While blocking ads"
                else -> null
            }
            SwitchSettingRow(
                title = button.label,
                summary = reason ?: context,
                icon = button.icon,
                checked = checked,
                enabled = reason == null,
                onCheckedChange = { onButtonChanged(button, it) },
            )
        }
        SettingsDivider()
        SettingRow(
            title = "Reset",
            summary = "Restore default buttons",
            icon = Res.drawable.ic_refresh,
            onClick = onReset,
        )
    }
}

@Composable
internal fun HeaderButtonOverflowSnackbar(
    overflowButtons: List<CommentsHeaderButton>,
    hostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var previousOverflow by remember { mutableStateOf(emptySet<CommentsHeaderButton>()) }
    val overflow = overflowButtons.toSet()
    LaunchedEffect(overflow) {
        val newlyHidden = overflow - previousOverflow
        previousOverflow = overflow
        if (newlyHidden.isNotEmpty()) {
            // A changed layout cancels the previous notice, keeping quick toggles and resizes
            // from queuing stale messages. Unchanged overflow does not repeat the notice.
            hostState.showSnackbar("Extra selected buttons are in More options at this width.")
        }
    }
    Box(Modifier.fillMaxSize().zIndex(1f)) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding().imePadding()
                // Clear the 56dp sample selector and its 16dp bottom margin.
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        )
    }
}

/** Matches the real post's status-bar tint as the header scrolls out of the inset. */
@Composable
internal fun HeaderSettingsStatusBarProtection(
    listState: LazyListState,
    headerColor: Color,
    statusBarHeight: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    previewCanPin: Boolean = true,
    protection: @Composable (Color, Dp) -> Unit,
) {
    if (statusBarHeight <= 0.dp) return
    val insetPx = with(LocalDensity.current) { statusBarHeight.roundToPx() }
    val navigationPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val coverage by remember(listState, insetPx, navigationPx, previewCanPin) {
        derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "settings-preview" }
            if (header == null || insetPx <= 0) 0f else {
                val offset = settingsPreviewOffset(listState, header, navigationPx, previewCanPin)
                val overlap = minOf(offset + header.size, insetPx) - maxOf(offset, 0)
                (overlap.toFloat() / insetPx).coerceIn(0f, 1f)
            }
        }
    }
    val color by animateColorAsState(
        lerp(MaterialTheme.colorScheme.pageBackground, headerColor, coverage),
        tween(90, easing = LinearEasing),
        label = "header settings status bar",
    )
    // Like an expanded real post, the unscrolled preview already fills the status bar.
    if (listState.canScrollBackward) protection(color, statusBarHeight)
}

/** Keep a preview that already scrolled away out of the settings while samples crossfade. */
internal fun headerPreviewPinningAfterSampleChange(
    state: LazyListState,
    currentlyAllowed: Boolean,
    bottomInset: Int,
): Boolean {
    if (!state.canScrollBackward) return true
    if (!currentlyAllowed) return false
    val header = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "settings-preview" }
        ?: return false
    return settingsPreviewOffset(state, header, bottomInset) >= 0
}

@Composable
internal fun HeaderDisplayPreview(
    settings: CommentDisplaySettings,
    bookmarksEnabled: Boolean,
    onBack: () -> Unit,
    sample: HeaderPreviewSample = HeaderPreviewSample.LINK_WITH_IMAGE,
    onHeaderColorChanged: (Color) -> Unit = {},
    onButtonsOverflowChanged: (List<CommentsHeaderButton>) -> Unit = {},
    showBackButton: Boolean = true,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val counterStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val textMeasurer = rememberTextMeasurer()
    // Reserve the widest counter at the current font scale, even if the font lacks tabular digits.
    val counterWidth = with(LocalDensity.current) {
        HeaderPreviewSample.entries.maxOf {
            textMeasurer.measure(
                text = "${it.ordinal + 1}/${HeaderPreviewSample.entries.size}",
                style = counterStyle,
                maxLines = 1,
            ).size.width
        }.toDp()
    }
    CommentsHazeHost {
        val hazeState = currentCommentsHazeState()
        Box(Modifier.fillMaxWidth().semantics { contentDescription = "Header preview" }) {
            // Keep the floating controls composed while only the post content crossfades.
            Box(Modifier.commentsHazeSource(hazeState)) {
                AnimatedContent(
                    targetState = sample,
                    transitionSpec = {
                        (fadeIn(tween(240)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = true) { _, _ -> tween(320) })
                    },
                    label = "header preview sample",
                ) { visibleSample ->
                    HeaderDisplayPreviewContent(settings, bookmarksEnabled, visibleSample,
                        contentWindowInsets = contentWindowInsets,
                        onHeaderColorChanged = { color ->
                            if (visibleSample == sample) onHeaderColorChanged(color)
                        },
                        onButtonsOverflowChanged = { overflow ->
                            if (visibleSample == sample) onButtonsOverflowChanged(overflow)
                        })
                }
            }
            AnimatedVisibility(
                visible = settings.showUpButton && showBackButton,
                // Fading creates an offscreen layer. Include the shadow in its bounds so it
                // doesn't get clipped during the fade and pop back at full opacity.
                modifier = Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(contentWindowInsets)
                    .offset(y = statusBarPadding - 12.dp).zIndex(1f),
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
            ) {
                CommentsUpButton(
                    onClick = onBack,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd)
                    .windowInsetsPadding(contentWindowInsets)
                    .padding(top = statusBarPadding + 8.dp, end = 16.dp)
                    .zIndex(1f)
                    .clearAndSetSemantics {
                        contentDescription = "Preview ${sample.ordinal + 1} of ${HeaderPreviewSample.entries.size}: ${sample.label}"
                    },
                shape = RoundedCornerShape(50),
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Text(
                    text = "${sample.ordinal + 1}/${HeaderPreviewSample.entries.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(counterWidth),
                    style = counterStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun HeaderDisplayPreviewContent(
    settings: CommentDisplaySettings,
    bookmarksEnabled: Boolean,
    sample: HeaderPreviewSample,
    onHeaderColorChanged: (Color) -> Unit,
    onButtonsOverflowChanged: (List<CommentsHeaderButton>) -> Unit,
    contentWindowInsets: WindowInsets,
) {
    val hasPreviewImage = sample == HeaderPreviewSample.LINK_WITH_IMAGE || sample == HeaderPreviewSample.LINK_PREVIEW
    val imageDisplayed = settings.showHeaderPreviewImage && hasPreviewImage
    var imageBounds by remember(sample) { mutableStateOf(Rect.Zero) }
    var inspectedImage by remember(sample) { mutableStateOf<Rect?>(null) }
    var imageDismissRequest by remember(sample) { mutableIntStateOf(0) }
    val actions = remember(sample) { HeaderPreviewActions() }
    val controller = remember(sample) {
        CommentsScreenController.create(
            shouldSmoothScroll = { false },
            story = StoryListItemSnapshot(
                story = StorySnapshot(
                    id = -1,
                    author = "willow",
                    title = when (sample) {
                        HeaderPreviewSample.TEXT_POST -> "Ask HN: What have you built just for yourself?"
                        HeaderPreviewSample.LINK_PREVIEW -> "Show HN: A little reading-list app"
                        else -> SettingsStoryPreviewModel.title
                    },
                    url = when (sample) {
                        HeaderPreviewSample.TEXT_POST -> null
                        HeaderPreviewSample.LINK_PREVIEW -> "https://github.com/willow/reading-list"
                        else -> "https://science.org"
                    },
                    text = if (sample == HeaderPreviewSample.TEXT_POST)
                        "I made a small tool to organize my reading list. What personal projects have become part of your everyday life?"
                        else null,
                    score = SettingsStoryPreviewModel.points,
                    descendantCount = SettingsStoryPreviewModel.commentCount,
                    createdAtEpochSeconds = Clock.System.now().epochSeconds.toInt() - 7200,
                ),
                presentation = StoryPresentationSnapshot(
                    loaded = true, isLink = sample != HeaderPreviewSample.TEXT_POST,
                    gitHubRepoInfo = if (sample == HeaderPreviewSample.LINK_PREVIEW) RepoInfo(
                        owner = "willow", name = "reading-list",
                        about = "A small, offline-first home for articles you want to come back to.",
                        stars = 128, forks = 12, language = "Kotlin", license = "MIT",
                    ) else null,
                ),
            ),
            // A ready preview has no comment-loading or empty-thread status beneath the header.
            initialThreadCached = true,
            showWebsite = false,
            accountUser = null,
            savedItemState = actions,
            listener = actions,
        ).also { it.updateContent(it.screenState.copy(integratedWebView = true)) }
    }
    LaunchedEffect(actions.refreshVersion) {
        if (actions.refreshVersion > 0) {
            delay(650)
            controller.finishHeaderRefresh()
        }
    }
    val textStyle = LocalSettingsPlatformStyle.current.textStyle
    val platform = remember(textStyle) {
        CommentsPreviewPlatform(
            textStyle = textStyle,
            openLink = {},
            downloadPdf = {},
            openCustomTab = {},
            plainText = { it },
        )
    }
    val image = painterResource(
        if (sample == HeaderPreviewSample.LINK_PREVIEW) Res.drawable.github_preview else Res.drawable.web_preview,
    )
    val tintBase = HarmonicTheme.targetColorScheme.cardBackground.toArgb()
    // Resolve before the first frame so the header and detail buttons start fully tinted.
    // As in a real thread, hiding the image does not change the story's resolved palette.
    val tint = remember(sample, tintBase, settings.paletteTintMode) {
        sample.tintPalette?.let {
            PreviewTintPolicy.calculateCardTint(tintBase, it, settings.paletteTintMode)
        }
    }
    CommentsHeader(
        controller = controller,
        settings = settings,
        contentVersion = actions.version,
        storyPosterTag = "",
        tintBaseColor = tintBase,
        initialTint = tint,
        headerTopPadding = 24.dp,
        bookmarksEnabled = bookmarksEnabled,
        lastRefreshedText = null,
        textStyle = textStyle,
        previewPlatform = platform,
        previewCommentActions = true,
        contentWindowInsets = contentWindowInsets,
        onButtonsOverflowChanged = onButtonsOverflowChanged,
        includeStatusBarSpacer = true,
        headerPreviewImageDisplayed = imageDisplayed,
        faviconPreview = when (sample) {
            HeaderPreviewSample.TEXT_POST -> null
            HeaderPreviewSample.LINK_PREVIEW -> tintedPainterResource(
                Res.drawable.ic_link_preview_github, MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> painterResource(Res.drawable.quanta)
        },
        headerPreviewImage = { imageModifier, _, _ ->
            Image(
                painter = image,
                contentDescription = "Story preview image",
                contentScale = ContentScale.Crop,
                modifier = imageModifier.onGloballyPositioned {
                    imageBounds = it.boundsInWindow()
                }
                    .headerPreviewImageGestures(onClick = {}, onInspect = {
                        imageDismissRequest = 0
                        inspectedImage = imageBounds
                    }),
            )
        },
    )
    val headerColor = controller.statusBarHeaderColor
    SideEffect { headerColor?.let(onHeaderColorChanged) }
    inspectedImage?.let { bounds ->
        val dismiss = { imageDismissRequest += 1 }
        PlatformDialogDimHost {
        Dialog(onDismissRequest = dismiss,
            properties = platformDialogProperties(true, false, false)) {
            PlatformDisableDialogWindowAnimations()
            PlatformDialogBackgroundDimAmount(0f)
            TransformOverlay(
                contentKey = bounds, sourceBounds = bounds, dismissRequestVersion = imageDismissRequest,
                predictiveBackProgress = 0f, predictiveBackEdge = 0,
                maxWidth = 640.dp, horizontalPadding = 24.dp, verticalPadding = 24.dp,
                targetCornerRadius = 8.dp, sourceCornerRadius = 8.dp,
                containerColor = Color.Transparent, shadowElevation = 0.dp,
                consumeAllGestures = false, verticalSwipeDismissEnabled = true,
                onDismissRequest = dismiss, onDismissAnimationFinished = { inspectedImage = null },
            ) {
                val ratio = image.intrinsicSize.width / image.intrinsicSize.height
                ZoomableImageViewport(ratio, dismiss,
                    Modifier.fillMaxWidth().heightIn(max = 720.dp).previewImageViewport(ratio)) { modifier ->
                    Image(image, "Inspect story image", modifier, contentScale = ContentScale.Fit)
                }
            }
        }
        }
    }
}

/** The sample header never reads or modifies the user's saved items or navigation. */
internal class HeaderPreviewActions : SavedItemStateReader, CommentsScreenController.Listener {
    var version by mutableIntStateOf(0)
        private set
    var refreshVersion by mutableIntStateOf(0)
        private set
    private var bookmarked by mutableStateOf(false)
    private var favorited by mutableStateOf(false)
    private var upvoted by mutableStateOf(false)
    override fun isBookmarked(itemId: Int) = bookmarked
    override fun isFavorited(itemId: Int) = favorited
    override fun isUpvoted(itemId: Int, isComment: Boolean) = upvoted
    override fun onToggleComment(comment: PortableCommentItem, position: Int) = Unit
    override fun onCommentAction(comment: PortableCommentItem, action: CommentMenuAction) = Unit
    override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) = Unit
    override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) = Unit
    override fun onHeaderClick() = Unit
    override fun onHeaderPreviewImageResult(imageUrl: String, success: Boolean) = Unit
    override fun onHeaderPreviewTintExtracted(
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ): Int? = null
    override fun onHeaderAction(action: CommentsHeaderAction) {
        when (action) {
            CommentsHeaderAction.BOOKMARK -> bookmarked = !bookmarked
            CommentsHeaderAction.FAVORITE -> favorited = !favorited
            CommentsHeaderAction.VOTE -> upvoted = !upvoted
            CommentsHeaderAction.REFRESH -> refreshVersion++
            CommentsHeaderAction.USER, CommentsHeaderAction.REPLY, CommentsHeaderAction.SUMMARIZE -> Unit
        }
        version++
    }
    override fun onShareAction(action: CommentsShareAction) = Unit
    override fun onMoreAction(action: CommentsMoreAction) {
        when (action) {
            CommentsMoreAction.TOGGLE_BOOKMARK -> onHeaderAction(CommentsHeaderAction.BOOKMARK)
            CommentsMoreAction.REFRESH -> onHeaderAction(CommentsHeaderAction.REFRESH)
            else -> Unit
        }
    }
    override fun onSearchResultSelected(comment: PortableCommentItem) = Unit
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onSortComments(sortType: String) = Unit
    override fun onSheetAction(action: CommentsSheetAction) = Unit
    override fun onCollapseSheetForWebsite() = Unit
    override fun onSheetProgressChanged(expandedFraction: Float) = Unit
    override fun onSheetSettled(expanded: Boolean) = Unit
    override fun onHeaderColorChanged(color: Int) = Unit
    override fun onHeaderCoverageChanged(coverage: Float) = Unit
    override fun onPollOption(optionId: Int) = Unit
}
