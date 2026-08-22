package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.app.CommentsFeatureHost
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createCommentsStore
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.presentation.CommentTargetResolution
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsPresentationCapabilities
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsStore
import com.simon.harmonichackernews.ui.comments.CommentLinkPreviewOverlayState
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsFeatureListener
import com.simon.harmonichackernews.ui.comments.CommentsHeaderPresentationFactory
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.comments.ReferenceSummaryUiState
import com.simon.harmonichackernews.ui.comments.SharedCommentActionOverlay
import com.simon.harmonichackernews.ui.comments.SharedCommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.SharedCommentsHeader
import com.simon.harmonichackernews.ui.comments.SharedCommentsHazeHost
import com.simon.harmonichackernews.ui.comments.SharedCommentsRoute
import com.simon.harmonichackernews.ui.comments.SharedCommentsSearchDialog
import com.simon.harmonichackernews.ui.comments.SharedCommentsUpButton
import com.simon.harmonichackernews.ui.comments.SharedHeaderPreviewImage
import com.simon.harmonichackernews.ui.comments.SharedLinkPreviewShimmer
import com.simon.harmonichackernews.ui.comments.SharedReferenceCardContent
import com.simon.harmonichackernews.ui.common.SharedHarmonicTopAppBar
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.HtmlTextUtils

private class DesktopCommentsHost(
    val store: CommentsStore,
    val controller: CommentsComposeController,
    var restoringStoredProgress: Boolean,
)

@Composable
internal fun DesktopCommentsContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainStoryRequest,
    showNavigation: Boolean,
    onClose: () -> Unit,
    onControllerChanged: (CommentsComposeController?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val host = remember(app, scene, request.serial, scope) {
        val sessionState = scene.sessions.commentsStateFor(request.serial, request.storyId)
        val restoring = sessionState.initialized
        val store = app.createCommentsStore(
            CommentsFeatureHost(
                scope = scope,
                sessionState = sessionState,
                platform = app.commentsPlatformDependencies(),
                userSettings = app.userSettings,
            ),
        )
        store.start(
            initialStory = request.destination.toStory(),
            showWebsite = false,
            scrollToCommentId = request.route.scrollToCommentId,
            restoring = restoring,
            restoredSorting = null,
        )
        lateinit var controller: CommentsComposeController
        lateinit var createdHost: DesktopCommentsHost
        val callbacks = object : CommentsFeatureListener.PlatformCallbacks {
            override fun isRestoringScroll(): Boolean = createdHost.restoringStoredProgress
            override fun canHandleCommentAction(): Boolean = true
            override fun onCommentActionOverlayVisibilityChanged() = Unit
            override fun onLinkPreviewOverlayVisibilityChanged() = Unit
            override fun scrollToSearchResult(commentId: Int) {
                controller.scrollToSearchResult(commentId)
            }
            override fun collapseSheetForWebsite() {
                controller.story.url?.let { scene.links.open(it) }
            }
            override fun onSheetProgressChanged(expandedFraction: Float) = Unit
            override fun onSheetSettled(expanded: Boolean) = Unit
            override fun onHeaderColorChanged(color: Int) = Unit
            override fun onHeaderCoverageChanged(coverage: Float) = Unit
        }
        val initialState = checkNotNull(store.state.value.story)
        controller = CommentsComposeController.create(
            shouldSmoothScroll = { store.state.value.settings?.smoothScroll ?: true },
            story = initialState,
            showWebsite = false,
            accountUser = store.state.value.accountUser,
            savedItemState = store.savedItemState,
            listener = CommentsFeatureListener(store, callbacks),
        )
        DesktopCommentsHost(store, controller, restoring).also { createdHost = it }
    }
    val featureState by host.store.state.collectAsState()

    SideEffect { onControllerChanged(host.controller) }
    DisposableEffect(host) {
        onDispose {
            onControllerChanged(null)
            host.store.captureCollapsedComments()
            host.store.close()
        }
    }
    LaunchedEffect(featureState, host.controller) {
        CommentsScreenStateFactory.create(
            featureState,
            CommentsPlatformPresentation(
                adBlockActive = false,
                readerModeAvailable = false,
                readerModeEnabled = false,
                topInsetPx = 0,
                contentInsetLeftPx = 0,
                contentInsetRightPx = 0,
            ),
        )?.let { state ->
            // The desktop host intentionally opens articles in the system browser until an
            // embeddable KMP browser backend is available.
            host.controller.updateContent(state.copy(integratedWebView = false))
        }
    }
    LaunchedEffect(host) {
        host.store.effects.collect { effect ->
            when (effect) {
                is CommentsRuntimeEffect.Platform -> handleDesktopCommentsPlatformEffect(
                    effect.effect,
                    app,
                    scene,
                    host,
                )
                is CommentsRuntimeEffect.ShowCommentActions ->
                    host.controller.showCommentActions(effect.comment)
                is CommentsRuntimeEffect.ThreadReady -> {
                    if (effect.restoreScroll && host.restoringStoredProgress) {
                        host.store.restoreScrollProgress()?.let { restoration ->
                            host.controller.scrollToComment(
                                restoration.commentId,
                                restoration.offset,
                                false,
                            )
                        }
                    }
                    host.restoringStoredProgress = false
                    when (val target = host.store.consumeCommentTarget()) {
                        is CommentTargetResolution.Found ->
                            host.controller.scrollToComment(target.commentId, 0, false)
                        is CommentTargetResolution.NotFound ->
                            scene.userMessages.show("Comment not found")
                        CommentTargetResolution.None -> Unit
                    }
                }
                is CommentsRuntimeEffect.ActionFailed -> {
                    if (effect.presentation.requestLogin) scene.navigation.showLoginDialog()
                    if (effect.presentation.showDetails) {
                        scene.navigation.showFailureDetailDialog(
                            effect.presentation.failureSummary,
                            effect.presentation.failureDetail,
                            null,
                        )
                    }
                    scene.userMessages.show(effect.presentation.message)
                }
                is CommentsRuntimeEffect.Diagnostic ->
                    effect.cause?.printStackTrace()
                is CommentsRuntimeEffect.StateChanged -> Unit
            }
        }
    }
    LaunchedEffect(host) {
        host.store.updatePresentationCapabilities(
            CommentsPresentationCapabilities(showInvertAction = false, isTablet = true),
        )
        host.store.loadInitial(restoreScrollFromCache = host.restoringStoredProgress)
    }

    val showFloatingUpButton = showNavigation &&
        host.controller.displaySettings?.showUpButton == true
    SharedCommentsHazeHost {
        Column(Modifier.fillMaxSize()) {
            if (showNavigation && !showFloatingUpButton) {
                SharedHarmonicTopAppBar(
                    title = "Comments",
                    onBack = onClose,
                    toolbarHeight = 56.dp,
                    navigationHeight = 48.dp,
                )
            }
            Box(Modifier.weight(1f)) {
                SharedCommentsRoute(
                    controller = host.controller,
                    reserveUpButtonInset = showFloatingUpButton,
                    headerContent = { settings ->
                        DesktopCommentsHeader(app, scene, host.controller, settings)
                    },
                    searchDialog = { settings ->
                        SharedCommentsSearchDialog(
                            searchTerm = host.controller.searchQuery,
                            visibleComments = host.controller.searchResults,
                            settings = settings,
                            storyAuthor = host.controller.story.by,
                            accountUser = host.controller.accountUser,
                            maxDialogHeight = 720.dp,
                            onSearchTermChanged = host.controller.listener::onSearchQueryChanged,
                            onDismiss = host.controller::dismissCommentSearch,
                            onCommentSelected = host.controller::selectSearchResult,
                            onOpenLink = { scene.links.open(it) },
                        )
                    },
                    actionOverlay = { settings ->
                        SharedCommentActionOverlay(
                            controller = host.controller,
                            settings = settings,
                            hasAccount = app.platform.accounts.load() != null,
                            bookmarksEnabled = app.userSettings.general.bookmarksEnabled,
                            textStyle = TextStyle.Default,
                            onOpenLink = { scene.links.open(it) },
                        )
                    },
                )
                if (showFloatingUpButton) {
                    SharedCommentsUpButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 8.dp)
                            .zIndex(101f),
                    )
                }
            }
        }
    }
}

private fun handleDesktopCommentsPlatformEffect(
    effect: CommentsPlatformEffect,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    host: DesktopCommentsHost,
) {
    when (effect) {
        is CommentsPlatformEffect.OpenUser -> scene.navigation.showUserDialog(effect.userName)
        is CommentsPlatformEffect.OpenEditor -> scene.navigation.openEditor(effect.destination)
        CommentsPlatformEffect.RequestLogin -> scene.navigation.showLoginDialog()
        is CommentsPlatformEffect.ShowMessage -> scene.userMessages.show(effect.message)
        is CommentsPlatformEffect.ShareText -> {
            app.platform.sharing.share(effect.text)
            scene.userMessages.show("Copied share text to clipboard")
        }
        is CommentsPlatformEffect.CopyText -> {
            app.platform.clipboard.copy(effect.label, effect.text)
            scene.userMessages.show("Text copied to clipboard")
        }
        CommentsPlatformEffect.ReloadLinkPreviews -> Unit
        CommentsPlatformEffect.Summarize -> host.store.startSummary(null)
        is CommentsPlatformEffect.OpenStory -> scene.navigation.openStory(effect.destination)
        is CommentsPlatformEffect.OpenExternalLink -> scene.links.openExternal(
            ExternalLinkRequest(effect.url, preferInApp = effect.preferInApp),
        )
        CommentsPlatformEffect.ShowSearch -> host.controller.showCommentSearch()
        CommentsPlatformEffect.DisableAdBlock ->
            scene.userMessages.show("Ad blocking applies to the Android embedded browser")
        CommentsPlatformEffect.ReloadWebsite,
        CommentsPlatformEffect.OpenWebsiteInBrowser ->
            host.controller.story.url?.let { scene.links.open(it, preferInApp = false) }
        CommentsPlatformEffect.ExpandSheet -> Unit
        CommentsPlatformEffect.ToggleReaderMode ->
            scene.userMessages.show("Reader mode requires an embedded desktop browser")
        CommentsPlatformEffect.ToggleDarkMode -> Unit
    }
}

@Composable
private fun DesktopCommentsHeader(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsComposeController,
    settings: com.simon.harmonichackernews.adapters.CommentDisplaySettings,
) {
    val colors = HarmonicTheme.colors
    val tintBase = colors.surfaceContainerHigh.toArgb()
    val presentation = remember(
        controller.story,
        controller.contentVersion,
        controller.headerPreviewResource,
        settings,
        tintBase,
    ) {
        CommentsHeaderPresentationFactory.create(
            story = controller.story,
            previewResource = controller.headerPreviewResource,
            faviconProvider = settings.faviconProvider,
            paletteTintMode = settings.paletteTintMode,
            tintBaseColor = tintBase,
            tintStore = app.storyResourceTints,
            userTags = app.userTags,
            lastRefreshedMillis = controller.lastRefreshed,
            formatTime = app.platform.timeFormatting::time,
        )
    }
    val linkColor = colors.link
    val previewPlatform = remember(scene, linkColor) {
        CommentsPreviewPlatform(
            textStyle = TextStyle.Default,
            openLink = { it?.let(scene.links::open) },
            downloadPdf = { it?.let { url -> scene.links.open(url, preferInApp = false) } },
            openCustomTab = { it?.let { url -> scene.links.open(url, preferInApp = false) } },
            plainText = HtmlTextUtils::plainText,
            annotatedHtml = { html, _, listener -> htmlAnnotatedString(html, linkColor, listener) },
        )
    }

    SharedCommentsHeader(
        controller = controller,
        settings = settings,
        contentVersion = controller.contentVersion,
        storyPosterTag = presentation.posterTag,
        tintBaseColor = tintBase,
        initialTint = presentation.tint.initialTintArgb,
        headerTopPadding = 0.dp,
        actionHorizontalPadding = 8.dp,
        bookmarksEnabled = app.userSettings.general.bookmarksEnabled,
        lastRefreshedText = presentation.lastRefreshedText,
        textStyle = TextStyle.Default,
        previewPlatform = previewPlatform,
        headerPreviewImageDisplayed = settings.showHeaderPreviewImage &&
            presentation.tint.previewImageAvailable,
        headerPreviewImage = { _, onTintLoaded ->
            val imageUrl = presentation.tint.previewImageUrl
            SharedHeaderPreviewImage(
                imageUrl = imageUrl,
                initiallyFailed = controller.headerPreviewResource?.imageLoadFailed == true,
                visible = settings.showHeaderPreviewImage,
                suppressed = controller.headerPreviewSuppressed,
                tintBaseColorArgb = tintBase,
                paletteTintConfigKey = presentation.tint.paletteMode,
                extractTint = settings.tintHeader,
                onTintExtracted = { tint ->
                    val canonical = imageUrl?.let { sourceUrl ->
                        controller.listener.onHeaderPreviewTintExtracted(
                            sourceUrl,
                            tintBase,
                            presentation.tint.paletteMode,
                            tint,
                        )
                    } ?: tint
                    onTintLoaded(canonical)
                },
                onImageResult = { success ->
                    imageUrl?.let {
                        controller.listener.onHeaderPreviewImageResult(it, success)
                    }
                },
                onClick = controller.listener::onHeaderClick,
                onLongClick = { bounds ->
                    imageUrl?.let { url ->
                        controller.showImagePreview(
                            imageUrl = url,
                            description = "Preview image for ${controller.story.title.orEmpty()}",
                            sourceBounds = bounds,
                            backgroundColor = tintBase,
                        )
                    }
                },
            )
        },
    )
}

@Composable
internal fun DesktopCommentLinkPreview(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsComposeController,
) {
    SharedCommentLinkPreviewOverlay(
        controller = controller,
        tablet = true,
        referenceContent = { state ->
            DesktopReferencePreview(app, scene, controller, state)
        },
        imageContent = { state ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(HarmonicTheme.colors.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = state.imageUrl,
                    contentDescription = state.description,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 720.dp),
                    contentScale = ContentScale.Fit,
                )
                if (state.description.isNotBlank()) Text(state.description)
            }
        },
    )
}

@Composable
private fun DesktopReferencePreview(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsComposeController,
    state: CommentLinkPreviewOverlayState.Reference,
) {
    val scope = rememberCoroutineScope()
    val runtime = remember(app, scope, state.originalUrl) {
        app.createReferenceLinkPreviewRuntime(scope)
    }
    val runtimeState by runtime.state.collectAsState()

    LaunchedEffect(runtime, state) {
        runtime.load(state.originalUrl, state.fallbackTitle, state.resolvedTitle)
    }
    DisposableEffect(runtime) { onDispose(runtime::dispose) }
    LaunchedEffect(runtimeState.url) {
        runtimeState.url.takeIf { it.isNotBlank() && it != state.originalUrl }?.let {
            controller.updateLinkPreviewVisibleUrl(state.originalUrl, it)
        }
    }

    val currentUrl = runtimeState.url.takeIf(String::isNotBlank)
        ?: controller.linkPreviewVisibleUrl
        ?: state.originalUrl
    val settings = controller.displaySettings
    val favicon = remember(currentUrl, settings?.faviconProvider) {
        runCatching {
            FaviconUrlBuilder.faviconUrl(
                currentUrl,
                settings?.faviconProvider ?: app.userSettings.story.faviconProvider,
            )
        }.getOrNull()
    }
    SharedReferenceCardContent(
        url = currentUrl,
        fallbackTitle = state.fallbackTitle,
        summary = ReferenceSummaryUiState(
            loading = runtimeState.loading,
            showFallback = runtimeState.showFallback,
            result = runtimeState.summary,
            error = runtimeState.error,
            retrying = runtimeState.retrying,
        ),
        preferredFont = settings?.font ?: app.userSettings.story.font,
        commentTextSize = settings?.preferredTextSize ?: app.userSettings.comments.textSize,
        favicon = favicon,
        offline = runtimeState.offline,
        textStyle = TextStyle.Default,
        referenceImage = { imageUrl, loading, expanded, ratio, onRatio, onClick, modifier ->
            Box(
                modifier = modifier
                    .then(
                        if (expanded) Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.45f, 3f))
                        else Modifier.size(104.dp),
                    )
                    .clip(RoundedCornerShape(if (expanded) 28.dp else 8.dp))
                    .background(HarmonicTheme.colors.surfaceContainerHighest)
                    .clickable(enabled = imageUrl != null, onClick = onClick),
            ) {
                if (loading) SharedLinkPreviewShimmer(Modifier.fillMaxSize())
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop,
                    onSuccess = { success ->
                        val size = success.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) onRatio(size.width / size.height)
                    },
                )
            }
        },
        onOpen = { scene.links.open(currentUrl) },
        onRetry = { runtime.retry(state.originalUrl, state.fallbackTitle, state.resolvedTitle) },
    )
}
