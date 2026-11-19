package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.utils.HtmlTextUtils

@Composable
fun PortableCommentsHeader(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    headerTopPadding: Dp,
    horizontalContentInsets: WindowInsets? = null,
    sheetActionsInHeader: Boolean = true,
    onBrowserBack: (() -> Unit)? = null,
) {
    val tintBase = HarmonicTheme.targetColorScheme.cardBackground.toArgb()
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
    val previewPlatform = remember(scene) {
        CommentsPreviewPlatform(
            textStyle = TextStyle.Default,
            openLink = { it?.let(scene.links::open) },
            downloadPdf = { it?.let { url -> scene.links.open(url, preferInApp = false) } },
            openCustomTab = { it?.let { url -> scene.links.open(url, preferInApp = false) } },
            plainText = HtmlTextUtils::plainText,
        )
    }

    CommentsHeader(
        controller = controller,
        settings = settings,
        contentVersion = controller.contentVersion,
        storyPosterTag = presentation.posterTag,
        storyPosterTagColor = presentation.posterTagColor,
        tintBaseColor = tintBase,
        initialTint = presentation.tint.initialTintArgb,
        headerTopPadding = headerTopPadding,
        bookmarksEnabled = app.userSettings.general.bookmarksEnabled,
        lastRefreshedText = presentation.lastRefreshedText,
        textStyle = TextStyle.Default,
        previewPlatform = previewPlatform,
        horizontalContentInsets = horizontalContentInsets,
        sheetActionsInHeader = sheetActionsInHeader,
        onBrowserBack = onBrowserBack,
        linkPreviewsEnabled = app.userSettings.reading.linkPreviewsEnabled,
        headerPreviewImageDisplayed = settings.showHeaderPreviewImage &&
            presentation.tint.previewImageAvailable,
        headerPreviewImage = { imageModifier, _, onTintLoaded ->
            val imageUrl = presentation.tint.previewImageUrl
            HeaderPreviewImage(
                modifier = imageModifier,
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
