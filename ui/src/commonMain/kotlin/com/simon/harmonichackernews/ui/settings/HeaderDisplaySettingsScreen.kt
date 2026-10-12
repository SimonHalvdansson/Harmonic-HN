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
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
import com.simon.harmonichackernews.ui.comments.HeaderPreviewImageVisibility
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
import com.simon.harmonichackernews.ui.content.rememberPainterPaletteTint
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.cardBackground
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeaderDisplaySettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
) {
    val dependencies = LocalHarmonicUiDependencies.current
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
    )

    var sampleIndex by rememberSaveable { mutableIntStateOf(0) }
    val sample = HeaderPreviewSample.entries[sampleIndex]

    SettingsPage(
        title = stringResource(Res.string.settings_section_header_display),
        showNavigation = true,
        showTopAppBar = false,
        onBack = onBack,
        contentVersion = settings.hashCode() + sampleIndex,
        pinnedContent = {
            AnimatedContent(
                targetState = sample,
                transitionSpec = {
                    (fadeIn(tween(240)) togetherWith fadeOut(tween(120)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(320) })
                },
                label = "header preview sample",
            ) { visibleSample ->
                HeaderDisplayPreview(displaySettings, settings.general.bookmarksEnabled, onBack, visibleSample)
            }
        },
    ) {
        item(key = "header-preview-controls") {
            HeaderPreviewSampleSelector(sample) { sampleIndex = it.ordinal }
        }
        item {
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
                        if (!displaySettings.showHeaderPreviewImage) add(CommentsHeaderMetaInfo.ON_IMAGE)
                    },
                    onSelected = repository::setCommentsHeaderMetaInfo,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Favicon",
                    icon = Res.drawable.ic_public,
                    checked = settings.comments.showFavicons,
                    onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.HEADER_FAVICON, it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Back",
                    icon = Res.drawable.ic_arrow_back,
                    checked = state.showUpButton,
                    onCheckedChange = { presenter.setBoolean(CommentsBooleanSetting.ShowUpButton, it) },
                )

            }
        }
        item {
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

@Composable
internal fun HeaderDisplayPreview(
    settings: CommentDisplaySettings,
    bookmarksEnabled: Boolean,
    onBack: () -> Unit,
    sample: HeaderPreviewSample = HeaderPreviewSample.LINK_WITH_IMAGE,
) {
    val imageDisplayed = settings.showHeaderPreviewImage && sample == HeaderPreviewSample.LINK_WITH_IMAGE
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
    val image = painterResource(Res.drawable.web_preview)
    val tintBase = HarmonicTheme.targetColorScheme.cardBackground.toArgb()
    // As in a real thread, hiding the image does not change the story's resolved palette.
    val tint = rememberPainterPaletteTint(
        painter = image,
        baseColorArgb = tintBase,
        paletteTintConfigKey = settings.paletteTintMode,
    )
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    CommentsHazeHost {
        val hazeState = currentCommentsHazeState()
        Box(
            Modifier.fillMaxWidth().semantics { contentDescription = "Header preview" },
        ) {
            Box(Modifier.commentsHazeSource(hazeState)) {
                CommentsHeader(
                    controller = controller,
                    settings = settings,
                    contentVersion = actions.version,
                    storyPosterTag = "",
                    tintBaseColor = tintBase,
                    initialTint = if (sample == HeaderPreviewSample.LINK_WITH_IMAGE) tint else null,
                    headerTopPadding = 24.dp,
                    bookmarksEnabled = bookmarksEnabled,
                    lastRefreshedText = null,
                    textStyle = textStyle,
                    previewPlatform = platform,
                    includeStatusBarSpacer = true,
                    headerPreviewImageDisplayed = imageDisplayed,
                    faviconPreview = if (sample == HeaderPreviewSample.LINK_PREVIEW)
                        tintedPainterResource(Res.drawable.ic_link_preview_github, MaterialTheme.colorScheme.onSurfaceVariant)
                        else painterResource(Res.drawable.quanta),
                    headerPreviewImage = { imageModifier, _, _ ->
                        HeaderPreviewImageVisibility(visible = imageDisplayed) {
                            Image(
                                painter = image,
                                contentDescription = "Story preview image",
                                contentScale = ContentScale.Crop,
                                modifier = imageModifier.onGloballyPositioned { imageBounds = it.boundsInWindow() }
                                    .headerPreviewImageGestures(onClick = {}, onInspect = {
                                        imageDismissRequest = 0
                                        inspectedImage = imageBounds
                                    }),
                            )
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = settings.showUpButton,
                // Fading creates an offscreen layer. Include the shadow in its bounds so it
                // doesn't get clipped during the fade and pop back at full opacity.
                modifier = Modifier.align(Alignment.TopStart).offset(y = statusBarPadding - 12.dp).zIndex(1f),
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
            ) {
                CommentsUpButton(
                    onClick = onBack,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
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
