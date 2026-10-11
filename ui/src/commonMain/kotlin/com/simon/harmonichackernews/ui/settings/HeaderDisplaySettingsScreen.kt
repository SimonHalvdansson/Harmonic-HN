package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
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
import com.simon.harmonichackernews.ui.comments.CommentsHeader
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.comments.commentsHazeSource
import com.simon.harmonichackernews.ui.comments.currentCommentsHazeState
import com.simon.harmonichackernews.ui.comments.HeaderPreviewImageVisibility
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
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val state = presenter.state(settings)
    val displaySettings = CommentDisplaySettings.from(
        preferences = settings.comments,
        showInvert = false,
        isTablet = false,
        hasAccountDetails = false,
        canProvideSummary = false,
    )

    SettingsPage(
        title = stringResource(Res.string.settings_section_header_display),
        showNavigation = true,
        onBack = onBack,
        contentVersion = settings.hashCode(),
        pinnedContent = {
            HeaderDisplayPreview(displaySettings, settings.general.bookmarksEnabled)
        },
    ) {
        item {
            SettingsCategory(stringResource(Res.string.settings_section_header_display)) {
                SwitchSettingRow(
                    title = "Back",
                    icon = Res.drawable.ic_arrow_back,
                    checked = state.showUpButton,
                    onCheckedChange = { presenter.setBoolean(CommentsBooleanSetting.ShowUpButton, it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Background tint",
                    icon = Res.drawable.ic_palette,
                    checked = state.headerTint,
                    enabled = state.storyTintEnabled,
                    summary = if (state.storyTintEnabled) null else "Disabled because story tint is off",
                    onCheckedChange = { presenter.setBoolean(CommentsBooleanSetting.HeaderTint, it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Preview image",
                    icon = Res.drawable.ic_image,
                    checked = state.headerPreviewImage,
                    enabled = state.storyPreviewEnabled,
                    onCheckedChange = { presenter.setBoolean(CommentsBooleanSetting.HeaderPreviewImage, it) },
                )
            }
        }
    }
}

@Composable
private fun HeaderDisplayPreview(settings: CommentDisplaySettings, bookmarksEnabled: Boolean) {
    val controller = remember {
        CommentsScreenController.create(
            shouldSmoothScroll = { false },
            story = StoryListItemSnapshot(
                story = StorySnapshot(
                    id = -1,
                    author = "willow",
                    title = SettingsStoryPreviewModel.title,
                    url = "https://science.org",
                    score = SettingsStoryPreviewModel.points,
                    descendantCount = SettingsStoryPreviewModel.commentCount,
                    createdAtEpochSeconds = Clock.System.now().epochSeconds.toInt() - 7200,
                ),
                presentation = StoryPresentationSnapshot(loaded = true, isLink = true),
            ),
            // A ready preview has no comment-loading or empty-thread status beneath the header.
            initialThreadCached = true,
            showWebsite = false,
            accountUser = null,
            savedItemState = HeaderPreviewActions,
            listener = HeaderPreviewActions,
        ).also { it.updateContent(it.screenState.copy(integratedWebView = true)) }
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
    CommentsHazeHost {
        val hazeState = currentCommentsHazeState()
        Box(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Header preview" },
        ) {
            Box(Modifier.commentsHazeSource(hazeState)) {
                CommentsHeader(
                    controller = controller,
                    settings = settings,
                    contentVersion = 0,
                    storyPosterTag = "",
                    tintBaseColor = tintBase,
                    initialTint = tint,
                    headerTopPadding = 24.dp,
                    bookmarksEnabled = bookmarksEnabled,
                    lastRefreshedText = null,
                    textStyle = textStyle,
                    previewPlatform = platform,
                    includeStatusBarSpacer = false,
                    headerPreviewImageDisplayed = settings.showHeaderPreviewImage,
                    faviconPreview = painterResource(Res.drawable.quanta),
                    headerPreviewImage = { _, _ ->
                        HeaderPreviewImageVisibility(visible = settings.showHeaderPreviewImage) {
                            Image(
                                painter = image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                                    .height(216.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = settings.showUpButton,
                // Fading creates an offscreen layer. Include the shadow in its bounds so it
                // doesn't get clipped during the fade and pop back at full opacity.
                modifier = Modifier.align(Alignment.TopStart).offset(y = (-12).dp).zIndex(1f),
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
            ) {
                CommentsUpButton(
                    onClick = {},
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Keep the real header's menus and actions inert, while allowing the settings list to scroll.
            Box(Modifier.matchParentSize().zIndex(2f).pointerInput(Unit) { detectTapGestures(onTap = {}) })
        }
    }
}

/** The sample header never reads or modifies the user's saved items or navigation. */
private object HeaderPreviewActions : SavedItemStateReader, CommentsScreenController.Listener {
    override fun isBookmarked(itemId: Int) = false
    override fun isFavorited(itemId: Int) = false
    override fun isUpvoted(itemId: Int, isComment: Boolean) = false
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
    override fun onHeaderAction(action: CommentsHeaderAction) = Unit
    override fun onShareAction(action: CommentsShareAction) = Unit
    override fun onMoreAction(action: CommentsMoreAction) = Unit
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
