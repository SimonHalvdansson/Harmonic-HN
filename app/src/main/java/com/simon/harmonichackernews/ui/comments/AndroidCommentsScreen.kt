package com.simon.harmonichackernews.ui.comments

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.CommentTextPolicy
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.AndroidPdfOpener
import com.simon.harmonichackernews.utils.HtmlTextUtils
import kotlinx.coroutines.flow.distinctUntilChanged

/** Android shell for system insets, nested-scroll interop, and image/cache facilities. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsScaffold(
    controller: CommentsComposeController,
    reserveUpButtonInset: Boolean,
) {
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = navigationBottom + if (controller.displaySettings?.isTablet == true) 81.dp else 68.dp
    val sheetState = rememberBottomSheetState(
        initialValue = if (controller.initialShowWebsite) {
            SheetValue.PartiallyExpanded
        } else {
            SheetValue.Expanded
        },
        enabledValues = setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val travelPx = with(density) { (fullHeight - peekHeight).toPx().coerceAtLeast(1f) }

        LaunchedEffect(controller.sheetRequest) {
            val request = controller.sheetRequest ?: return@LaunchedEffect
            if (request.expanded) sheetState.expand() else sheetState.partialExpand()
            controller.consumeSheetRequest(request)
        }

        LaunchedEffect(sheetState, travelPx) {
            snapshotFlow { runCatching { sheetState.requireOffset() }.getOrNull() }
                .collect { offset ->
                    val expandedFraction = offset
                        ?.let { 1f - (it / travelPx) }
                        ?.coerceIn(0f, 1f)
                        ?: if (sheetState.currentValue == SheetValue.Expanded) 1f else 0f
                    controller.updateSheet(expandedFraction, controller.topInsetPx)
                    controller.listener.onSheetProgressChanged(expandedFraction)
                }
        }

        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }
                .distinctUntilChanged()
                .collect { value ->
                    controller.listener.onSheetSettled(value == SheetValue.Expanded)
                }
        }

        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
            sheetShape = RectangleShape,
            sheetContainerColor = HarmonicTheme.colors.background,
            sheetContentColor = HarmonicTheme.colors.storyNormal,
            sheetShadowElevation = 16.dp,
            sheetDragHandle = null,
            sheetSwipeEnabled = controller.integratedWebView,
            containerColor = Color.Transparent,
            contentColor = HarmonicTheme.colors.storyNormal,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fullHeight),
                ) {
                    AndroidCommentsScreen(controller, reserveUpButtonInset)
                }
            },
            content = {},
        )
    }
}

@Composable
internal fun AndroidCommentsScreen(
    controller: CommentsComposeController,
    reserveUpButtonInset: Boolean,
) {
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    CommentsRoute(
        controller = controller,
        listModifier = Modifier.nestedScroll(nestedScrollInterop),
        reserveUpButtonInset = reserveUpButtonInset,
        headerContent = { settings ->
            AndroidCommentsHeader(
                controller = controller,
                settings = settings,
                contentVersion = controller.contentVersion,
            )
        },
        searchDialog = { settings ->
            AndroidCommentsSearchDialog(
                searchTerm = controller.searchQuery,
                visibleComments = controller.searchResults,
                settings = settings,
                storyAuthor = controller.story.by,
                accountUser = controller.accountUser,
                onSearchTermChanged = controller::updateSearchQuery,
                onDismiss = controller::dismissCommentSearch,
                onCommentSelected = controller::selectSearchResult,
            )
        },
        // Android hosts the action overlay above the pane-level status-bar protection.
        actionOverlay = {},
    )
}

@Composable
private fun AndroidCommentsHeader(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
) {
    val context = LocalContext.current
    val dependencies = LocalHarmonicUiDependencies.current
    val story = remember(controller.story, contentVersion) { controller.story }
    val previewResource = controller.headerPreviewResource?.takeIf { it.pageUrl == story.url }
    val tintBaseColor = HarmonicTheme.colors.storyCardBackground.toArgb()
    val headerPresentation = remember(
        story.id,
        story.previewImageUrl,
        story.previewImageTintColorLoaded,
        story.previewImageTintColor,
        story.previewImageTintBaseColor,
        story.previewImageTintMode,
        story.faviconTintSourceUrl,
        story.faviconTintColorLoaded,
        story.faviconTintColor,
        story.faviconTintBaseColor,
        story.faviconTintMode,
        previewResource,
        settings.faviconProvider,
        settings.paletteTintMode,
        tintBaseColor,
        controller.lastRefreshed,
        contentVersion,
    ) {
        CommentsHeaderPresentationFactory.create(
            story = story,
            previewResource = previewResource,
            faviconProvider = settings.faviconProvider,
            paletteTintMode = settings.paletteTintMode,
            tintBaseColor = tintBaseColor,
            tintStore = dependencies.storyResourceTints,
            userTags = dependencies.userTags,
            lastRefreshedMillis = controller.lastRefreshed,
            formatTime = dependencies.platform.timeFormatting::time,
        )
    }
    val tintPresentation = headerPresentation.tint
    val paletteTintMode = tintPresentation.paletteMode
    val previewImageUrl = tintPresentation.previewImageUrl
    val previewPlatform = remember(context) {
        CommentsPreviewPlatform(
            textStyle = legacyTextStyle,
            openLink = { url -> dependencies.links.open(url) },
            downloadPdf = { url -> AndroidPdfOpener.open(context, url) },
            openCustomTab = { url -> dependencies.links.open(url) },
            plainText = HtmlTextUtils::plainText,
            annotatedHtml = ::htmlToAnnotated,
        )
    }
    CommentsHeader(
        controller = controller,
        settings = settings,
        contentVersion = contentVersion,
        storyPosterTag = headerPresentation.posterTag,
        tintBaseColor = tintBaseColor,
        initialTint = tintPresentation.initialTintArgb,
        headerTopPadding = dimensionResource(R.dimen.comments_header_top_padding),
        actionHorizontalPadding = dimensionResource(R.dimen.comments_header_action_padding),
        bookmarksEnabled = dependencies.userSettings.general.bookmarksEnabled,
        lastRefreshedText = headerPresentation.lastRefreshedText,
        textStyle = legacyTextStyle,
        previewPlatform = previewPlatform,
        headerPreviewImageDisplayed = settings.showHeaderPreviewImage &&
            tintPresentation.previewImageAvailable,
    ) { visibleBackground, onTintLoaded ->
        val previewUrl = previewImageUrl
        HeaderPreviewImage(
            imageUrl = previewUrl,
            initiallyFailed = previewResource?.imageLoadFailed ?: story.previewImageLoadFailed,
            visible = settings.showHeaderPreviewImage,
            suppressed = controller.headerPreviewSuppressed,
            tintBaseColorArgb = tintBaseColor,
            paletteTintConfigKey = paletteTintMode,
            extractTint = tintPresentation.initialTintKind !=
                StoryResourceTintKind.PREVIEW_IMAGE,
            onTintExtracted = { tintColor ->
                val canonicalTint = previewUrl?.let { sourceUrl ->
                    controller.listener.onHeaderPreviewTintExtracted(
                        sourceUrl,
                        tintBaseColor,
                        paletteTintMode,
                        tintColor,
                    )
                } ?: tintColor
                onTintLoaded(canonicalTint)
            },
            onImageResult = { success ->
                previewUrl?.let { controller.listener.onHeaderPreviewImageResult(it, success) }
            },
            onClick = controller.listener::onHeaderClick,
            onLongClick = { bounds, sourceContentLayer, imageAspectRatio ->
                previewImageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                    controller.showImagePreview(
                        imageUrl = imageUrl,
                        description = if (story.title.isNullOrBlank()) {
                            "Story preview image"
                        } else {
                            "Preview image for ${story.title}"
                        },
                        sourceBounds = bounds,
                        sourceContentLayer = sourceContentLayer,
                        imageAspectRatio = imageAspectRatio,
                        backgroundColor = visibleBackground.toArgb(),
                    )
                }
            },
        )
    }
}

private fun htmlToAnnotated(
    html: String,
    linkStyles: TextLinkStyles,
    listener: LinkInteractionListener,
): AnnotatedString = runCatching {
    AnnotatedString.fromHtml(
        CommentTextPolicy.preserveLegacyParagraphSpacing(html),
        linkStyles,
        listener,
    )
}.getOrElse {
    AnnotatedString(HtmlTextUtils.plainText(html))
}

private val legacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)
