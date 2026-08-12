package com.simon.harmonichackernews.ui.comments

import android.text.Html
import android.text.format.DateFormat
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
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.CommentTextPolicy
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.Utils
import java.util.Date
import kotlinx.coroutines.flow.distinctUntilChanged

/** Android shell for system insets, nested-scroll interop, and image/cache facilities. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsScaffold(controller: CommentsComposeController) {
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
                    CommentsScreen(controller)
                }
            },
            content = {},
        )
    }
}

@Composable
internal fun CommentsScreen(controller: CommentsComposeController) {
    val context = LocalContext.current
    val settings = controller.displaySettings
    val commentPreferences = AndroidUserSettings.get(context).comments
    val userTagsRepository = remember(context) {
        UserTagsRepository(AndroidKeyValueStore.defaults(context))
    }
    val userTags = remember(controller.contentVersion) { userTagsRepository.tags() }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    SharedCommentsScreen(
        controller = controller,
        listModifier = Modifier.nestedScroll(nestedScrollInterop),
        animateComments = commentPreferences.animateChanges,
        showScrollbar = commentPreferences.showScrollbar,
        smoothScroll = commentPreferences.smoothScroll,
        userTags = userTags,
        onOpenLink = { url -> Utils.openLinkMaybeHN(context, url) },
        headerContent = {
            settings?.let {
                CommentsHeader(
                    controller = controller,
                    settings = it,
                    contentVersion = controller.contentVersion,
                )
            }
        },
        searchDialog = {
            settings?.let {
                CommentsSearchDialog(
                    searchTerm = controller.searchQuery,
                    visibleComments = controller.searchResults,
                    settings = it,
                    storyAuthor = controller.story.by,
                    accountUser = controller.accountUser,
                    onSearchTermChanged = controller::updateSearchQuery,
                    onDismiss = controller::dismissCommentSearch,
                    onCommentSelected = controller::selectSearchResult,
                )
            }
        },
        actionOverlay = {
            settings?.let { CommentActionOverlay(controller, it) }
        },
    )
}

@Composable
private fun CommentsHeader(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    contentVersion: Int,
) {
    val context = LocalContext.current
    val story = remember(controller.story, contentVersion) { controller.story }
    val previewResource = controller.headerPreviewResource?.takeIf { it.pageUrl == story.url }
    val storyPosterTag = remember(story.by, contentVersion) {
        UserTagsRepository(AndroidKeyValueStore.defaults(context)).tagFor(story.by)
    }
    val tintBaseColor = HarmonicTheme.colors.surfaceContainerHigh.toArgb()
    val paletteTintMode = remember(context, settings.paletteTintMode) {
        PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
    }
    val faviconTintSource = remember(story.url, settings.faviconProvider) {
        runCatching { FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider) }
            .getOrNull()
    }
    val previewImageUrl = previewResource?.imageUrl ?: story.previewImageUrl
    val resourceTint = previewResource?.previewTint
    val tintStore = remember(context) { AndroidAppComposition.get(context).storyResourceTints }
    val persistedPreviewTint = previewImageUrl?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteTintMode),
        )?.tintColorArgb
    }
    val initialTint = remember(
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
        tintBaseColor,
        paletteTintMode,
        faviconTintSource,
        previewImageUrl,
        resourceTint,
        persistedPreviewTint,
    ) {
        when {
            resourceTint != null && resourceTint.sourceUrl == previewImageUrl &&
                resourceTint.baseColorArgb == tintBaseColor &&
                StoryPreviewTintState.isModeCurrent(
                    resourceTint.paletteConfigKey,
                    paletteTintMode,
                ) -> resourceTint.tintColorArgb
            persistedPreviewTint != null -> persistedPreviewTint
            StoryPreviewTintState.isPreviewCurrent(
                story,
                tintBaseColor,
                paletteTintMode,
            ) -> story.previewImageTintColor
            story.faviconTintColorLoaded &&
                story.faviconTintBaseColor == tintBaseColor &&
                StoryPreviewTintState.isModeCurrent(story.faviconTintMode, paletteTintMode) &&
                story.faviconTintSourceUrl == faviconTintSource -> story.faviconTintColor
            else -> null
        }
    }
    val previewPlatform = remember(context) {
        CommentsPreviewPlatform(
            textStyle = legacyTextStyle,
            openLink = { url -> Utils.openLinkMaybeHN(context, url) },
            downloadPdf = { url -> Utils.downloadPDF(context, url) },
            openCustomTab = { url -> AndroidExternalLinkLauncher.openCustomTab(context, url) },
            plainText = { html -> Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString() },
            annotatedHtml = ::htmlToAnnotated,
        )
    }
    val lastRefreshedText = controller.lastRefreshed
        .takeIf { it > 0L }
        ?.let { value ->
            "Last refreshed: " + DateFormat.getTimeFormat(context).format(Date(value))
        }

    SharedCommentsHeader(
        controller = controller,
        settings = settings,
        contentVersion = contentVersion,
        storyPosterTag = storyPosterTag,
        tintBaseColor = tintBaseColor,
        initialTint = initialTint,
        headerTopPadding = dimensionResource(R.dimen.comments_header_top_padding),
        actionHorizontalPadding = dimensionResource(R.dimen.comments_header_action_padding),
        bookmarksEnabled = AndroidUserSettings.get(context).general.bookmarksEnabled,
        lastRefreshedText = lastRefreshedText,
        textStyle = legacyTextStyle,
        previewPlatform = previewPlatform,
    ) { visibleBackground, onTintLoaded ->
        val previewUrl = previewResource?.imageUrl ?: story.previewImageUrl
        SharedHeaderPreviewImage(
            imageUrl = previewUrl,
            initiallyFailed = previewResource?.imageLoadFailed ?: story.previewImageLoadFailed,
            visible = settings.showHeaderPreviewImage,
            suppressed = controller.headerPreviewSuppressed,
            tintBaseColorArgb = tintBaseColor,
            paletteTintConfigKey = paletteTintMode,
            onTintExtracted = { tintColor ->
                onTintLoaded(tintColor)
                previewUrl?.let { sourceUrl ->
                    controller.listener.onHeaderPreviewTintExtracted(
                        sourceUrl,
                        tintBaseColor,
                        paletteTintMode,
                        tintColor,
                    )
                }
            },
            onImageResult = { success ->
                previewUrl?.let { controller.listener.onHeaderPreviewImageResult(it, success) }
            },
            onClick = controller.listener::onHeaderClick,
            onLongClick = { bounds ->
                previewImageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                    controller.showImagePreview(
                        imageUrl = imageUrl,
                        description = if (story.title.isNullOrBlank()) {
                            "Story preview image"
                        } else {
                            "Preview image for ${story.title}"
                        },
                        sourceBounds = bounds,
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
    AnnotatedString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
}

private val legacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun CommentsHeaderPreview() {
    val story = remember {
        Story().apply {
            id = 1
            loaded = true
            isLink = true
            title = "Nvidia RTX Spark"
            url = "https://nvidia.com"
            by = "shenli3514"
            score = 428
            descendants = 417
            time = (System.currentTimeMillis() / 1000L).toInt() - 3600
            text = "A small preview of the story text with <a href=\"https://example.com\">a link</a>."
        }
    }
    val context = LocalContext.current
    HarmonicTheme {
        Column(Modifier.background(HarmonicTheme.colors.background)) {
            HeaderMeta(
                story = story,
                settings = CommentDisplaySettings.from(
                    AndroidUserSettings(context).comments,
                    true,
                    false,
                    true,
                    false,
                ),
                textStyle = legacyTextStyle,
            )
        }
    }
}
