package com.simon.harmonichackernews.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.WebContentPolicy
import com.simon.harmonichackernews.ui.comments.CommentLinkPreviewOverlayState
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsFeatureBinding
import com.simon.harmonichackernews.ui.comments.CommentsHeaderPresentationFactory
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.comments.ReferenceSummaryUiState
import com.simon.harmonichackernews.ui.comments.CommentActionOverlay
import com.simon.harmonichackernews.ui.comments.CommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.CommentsHeader
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.comments.CommentsRoute
import com.simon.harmonichackernews.ui.comments.CommentsSearchDialog
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import com.simon.harmonichackernews.ui.comments.HeaderPreviewImage
import com.simon.harmonichackernews.ui.comments.LinkPreviewShimmer
import com.simon.harmonichackernews.ui.comments.ReferenceCardContent
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.HtmlTextUtils

private class IosCommentsHost(
    val binding: CommentsFeatureBinding,
    val webView: IosCommentsWebView?,
) {
    val store get() = binding.store
    val controller get() = binding.controller
}

@Composable
internal fun IosCommentsContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainStoryRequest,
    isTablet: Boolean,
    showUpButton: Boolean,
    onControllerChanged: (CommentsComposeController?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val host = remember(app, scene, request.serial, scope) {
        val binding = CommentsFeatureBinding.create(
            app = app,
            scene = scene,
            request = request,
            scope = scope,
        )
        val initialState = checkNotNull(binding.store.state.value.story)
        IosCommentsHost(
            binding = binding,
            webView = WebContentPolicy.validatedHttpUrl(initialState.url)?.let(::IosCommentsWebView),
        ).also { createdHost ->
            binding.setBeforeWebsiteCollapse { createdHost.webView?.ensureLoaded() }
        }
    }
    val featureState by host.store.state.collectAsState()

    SideEffect { onControllerChanged(host.controller) }
    DisposableEffect(host) {
        onDispose {
            onControllerChanged(null)
            host.webView?.dispose()
            host.binding.close()
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
            host.controller.updateContent(state)
            host.webView?.updateAppearance(
                dark = app.appearance.selection().dark,
                matchTheme = featureState.settings?.reading?.matchWebViewTheme == true,
            )
        }
    }
    LaunchedEffect(host) {
        host.store.effects.collect { effect ->
            host.binding.handleEffect(effect, scene) { platformEffect ->
                handleIosCommentsPlatformEffect(
                    platformEffect,
                    app,
                    scene,
                    host,
                )
            }
        }
    }
    LaunchedEffect(host, isTablet) {
        host.binding.updatePresentationCapabilities(isTablet)
    }
    LaunchedEffect(host) {
        host.binding.loadInitial()
    }

    val showFloatingUpButton = showUpButton &&
        host.controller.displaySettings?.showUpButton == true
    val accountState by app.platform.accounts.accountState.collectAsState()
    val comments: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            CommentsRoute(
                controller = host.controller,
                reserveUpButtonInset = showFloatingUpButton,
                headerContent = { settings ->
                    IosCommentsHeader(app, scene, host.controller, settings)
                },
                searchDialog = { settings ->
                    CommentsSearchDialog(
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
                        onLinkLongClick = { comment, url, title, bounds ->
                            host.controller.showReferencePreview(
                                url = url,
                                title = title,
                                sourceBounds = bounds,
                                sourceCommentId = comment.id,
                            )
                        },
                        onReferenceLongClick = { comment, link, bounds, sourceContentLayer ->
                            host.controller.showReferencePreview(
                                link = link,
                                sourceBounds = bounds,
                                sourceCommentId = comment.id,
                                sourceContentLayer = sourceContentLayer,
                            )
                        },
                        foreground = {
                            IosCommentLinkPreview(app, scene, host.controller)
                        },
                    )
                },
                // iOS hosts this above the pane-level status-bar protection and up button.
                actionOverlay = {},
            )
        }
    }
    val background = HarmonicTheme.colors.background
    val protectionColor = lerp(
        background,
        host.controller.statusBarHeaderColor ?: background,
        host.controller.statusBarHeaderCoverage,
    )
    val showStatusBarProtection = !(host.controller.integratedWebView &&
        host.controller.isScrolledToTop)
    CommentsHazeHost {
        Box(
            Modifier
                .fillMaxSize()
                .background(background),
        ) {
            val webView = host.webView
            if (host.controller.integratedWebView && webView != null) {
                IosCommentsScaffold(
                    controller = host.controller,
                    webView = webView,
                    reserveUpButtonInset = showFloatingUpButton,
                    comments = comments,
                )
            } else {
                comments()
            }
            if (showStatusBarProtection) {
                IosStatusBarProtection(protectionColor)
            }
            if (showFloatingUpButton) {
                CommentsUpButton(
                    onClick = scene.navigation::detailRemovedFromBackStack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 4.dp)
                        .zIndex(101f),
                )
            }
            host.controller.displaySettings?.let { settings ->
                Box(Modifier.fillMaxSize().zIndex(102f)) {
                    CommentActionOverlay(
                        controller = host.controller,
                        settings = settings,
                        hasAccount = accountState.accountOrNull != null,
                        bookmarksEnabled = app.userSettings.general.bookmarksEnabled,
                        textStyle = TextStyle.Default,
                        onOpenLink = { scene.links.open(it) },
                    )
                }
            }
        }
    }
}

private fun handleIosCommentsPlatformEffect(
    effect: CommentsPlatformEffect,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    host: IosCommentsHost,
) {
    when (effect) {
        is CommentsPlatformEffect.OpenUser -> scene.navigation.showUserDialog(effect.userName)
        is CommentsPlatformEffect.OpenEditor -> scene.navigation.openEditor(effect.destination)
        CommentsPlatformEffect.RequestLogin -> scene.navigation.showLoginDialog()
        is CommentsPlatformEffect.ShowMessage -> scene.userMessages.show(effect.message)
        is CommentsPlatformEffect.ShareText -> {
            app.platform.sharing.share(effect.text)
        }
        is CommentsPlatformEffect.CopyText -> {
            app.platform.clipboard.copy(effect.label, effect.text)
            scene.userMessages.show("Text copied to clipboard")
        }
        CommentsPlatformEffect.ReloadLinkPreviews -> Unit
        CommentsPlatformEffect.Summarize -> host.store.startSummary(null)
        is CommentsPlatformEffect.OpenStory -> scene.navigation.openStory(effect.destination)
        is CommentsPlatformEffect.OpenExternalLink -> {
            if (
                effect.preferInApp && host.controller.integratedWebView &&
                effect.url == host.controller.story.url && host.webView != null
            ) {
                host.controller.requestWebsite()
            } else {
                scene.links.openExternal(
                    ExternalLinkRequest(effect.url, preferInApp = effect.preferInApp),
                )
            }
        }
        CommentsPlatformEffect.ShowSearch -> host.controller.showCommentSearch()
        CommentsPlatformEffect.DisableAdBlock ->
            scene.userMessages.show("Ad blocking applies to the Android embedded browser")
        CommentsPlatformEffect.ReloadWebsite -> host.webView?.reload()
        CommentsPlatformEffect.OpenWebsiteInBrowser ->
            host.webView?.currentUrl()?.let { scene.links.open(it, preferInApp = false) }
        CommentsPlatformEffect.ExpandSheet -> host.controller.requestExpandSheet()
        CommentsPlatformEffect.ToggleReaderMode ->
            scene.userMessages.show("Reader mode isn't available in the iOS in-app browser yet")
        CommentsPlatformEffect.ToggleDarkMode -> host.webView?.toggleInversion()
    }
}

@Composable
private fun IosCommentsHeader(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsComposeController,
    settings: com.simon.harmonichackernews.adapters.CommentDisplaySettings,
) {
    val colors = HarmonicTheme.colors
    val tintBase = colors.storyCardBackground.toArgb()
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

    CommentsHeader(
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
        includeStatusBarSpacer = true,
        headerPreviewImageDisplayed = settings.showHeaderPreviewImage &&
            presentation.tint.previewImageAvailable,
        headerPreviewImage = { _, onTintLoaded ->
            val imageUrl = presentation.tint.previewImageUrl
            HeaderPreviewImage(
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
                onLongClick = { bounds, sourceContentLayer, imageAspectRatio ->
                    imageUrl?.let { url ->
                        controller.showImagePreview(
                            imageUrl = url,
                            description = "Preview image for ${controller.story.title.orEmpty()}",
                            sourceBounds = bounds,
                            sourceContentLayer = sourceContentLayer,
                            imageAspectRatio = imageAspectRatio,
                            backgroundColor = tintBase,
                        )
                    }
                },
            )
        },
    )
}

@Composable
internal fun IosCommentLinkPreview(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsComposeController,
) {
    CommentLinkPreviewOverlay(
        controller = controller,
        tablet = true,
        referenceContent = { state ->
            IosReferencePreview(app, scene, controller, state)
        },
        imageContent = { state ->
            val imageRatio = state.imageAspectRatio ?: state.sourceBounds?.let { bounds ->
                if (bounds.height > 0f) bounds.width / bounds.height else 16f / 9f
            } ?: (16f / 9f)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AsyncImage(
                    model = state.imageUrl,
                    contentDescription = state.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageRatio.coerceIn(0.35f, 4f)),
                    contentScale = ContentScale.Fit,
                )
            }
        },
    )
}

@Composable
private fun IosReferencePreview(
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
    ReferenceCardContent(
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
        referenceImage = { imageUrl, loading, _, shape, _, onRatio, onClick, modifier ->
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(HarmonicTheme.colors.surfaceContainerHighest)
                    .clickable(enabled = imageUrl != null, onClick = onClick),
            ) {
                if (loading) LinkPreviewShimmer(Modifier.fillMaxSize())
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
