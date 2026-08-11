package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.comment_sorting
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.utils.SettingsUtils
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun CommentsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<CommentsSettingsDialog?>(null) }
    val settings = AndroidUserSettings.get(context)
    val commentsSettings = settings.comments
    val displayStyle = if (commentsSettings.cardStyle) {
        SettingsUtils.COMMENT_DISPLAY_STYLE_CARD
    } else {
        SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD
    }
    val textSize = commentsSettings.textSize
    val textSizeOffset = TextPreferences.commentTextSizeOffset(textSize)
    val depthMode = commentsSettings.depthIndicatorMode
    val state = CommentsSettingsUiState(
        displayStyle = displayStyle,
        cardStyleValue = SettingsUtils.COMMENT_DISPLAY_STYLE_CARD,
        standardStyleValue = SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD,
        showBorder = commentsSettings.cardBorder,
        textSize = textSize,
        textSizeOffset = textSizeOffset,
        minTextSizeOffset = SettingsUtils.MIN_COMMENT_TEXT_SIZE_OFFSET,
        maxTextSizeOffset = SettingsUtils.MAX_COMMENT_TEXT_SIZE_OFFSET,
        collectLinks = commentsSettings.collectReferenceLinks,
        emphasizeMetadata = commentsSettings.highlightMetadata,
        depthMode = depthMode,
        depthModeLabel = CommentDepthPreferences.modeLabel(depthMode),
        showDividers = commentsSettings.showDividers,
        preferredFont = commentsSettings.font,
        topLevelIndicators = commentsSettings.showTopLevelDepthIndicator,
        showScrollbar = commentsSettings.showScrollbar,
        animateChanges = commentsSettings.animateChanges,
        storyTintEnabled = settings.story.tintCardUsingPreview,
        headerTint = prefs.getBoolean(UserPreferenceKeys.COMMENTS_HEADER_TINT, true),
        storyPreviewEnabled = settings.story.previewImageMode !=
            SettingsUtils.STORY_PREVIEW_IMAGE_OFF,
        headerPreviewImage = prefs.getBoolean(
            UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE,
            true,
        ),
        collapseParent = commentsSettings.collapseParent,
        collapseTopLevel = commentsSettings.collapseTopLevel,
        swapTap = commentsSettings.swapLongPressTap,
        sortingLabel = commentsSettings.sorting,
        providerLabel = if (!settings.reading.useAlgoliaApi) {
            "Official Hacker News API"
        } else {
            "Algolia API"
        },
        showNavigationButtons = commentsSettings.showNavigationButtons,
        volumeNavigationLabel = when (commentsSettings.volumeNavigationMode) {
            "top_level" -> "Top level comments"
            "all" -> "All comments"
            else -> "Disabled"
        },
        smoothScroll = commentsSettings.smoothScroll,
    )
    SharedCommentsSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onDisplayStyleChanged = {
            prefs.edit().putString(UserPreferenceKeys.COMMENT_DISPLAY_STYLE, it).apply()
        },
        onTextSizeOffsetChanged = { offset ->
            val size = TextPreferences.commentTextSizeForOffset(offset)
            val value = if (size == size.toInt().toFloat()) size.toInt().toString() else size.toString()
            prefs.edit().putString(UserPreferenceKeys.COMMENT_TEXT_SIZE, value).apply()
        },
        onBooleanChanged = { setting, value ->
            prefs.edit().putBoolean(setting.preferenceKey, value).apply()
        },
        onDialogRequested = { dialog = it },
        contentVersion = refresh,
    )

    when (dialog) {
        CommentsSettingsDialog.Sorting -> ChoiceDialog(
            title = "Comment sorting",
            options = stringArrayResource(Res.array.comment_sorting).map { it to it },
            selected = state.sortingLabel,
            onDismiss = { dialog = null },
            onSelected = { prefs.edit().putString(UserPreferenceKeys.COMMENT_SORTING, it).apply() },
        )
        CommentsSettingsDialog.Provider -> ChoiceDialog(
            title = "Comments provider",
            options = listOf("algolia" to "Algolia API", "official" to "Official Hacker News API"),
            selected = if (settings.reading.useAlgoliaApi) "algolia" else "official",
            onDismiss = { dialog = null },
            onSelected = { prefs.edit().putString(UserPreferenceKeys.COMMENTS_PROVIDER, it).apply() },
        )
        CommentsSettingsDialog.VolumeNavigation -> ChoiceDialog(
            title = "Volume buttons for navigation",
            options = listOf(
                "disabled" to "Disabled",
                "top_level" to "Top level comments",
                "all" to "All comments",
            ),
            selected = commentsSettings.volumeNavigationMode,
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION, it).apply()
            },
        )
        CommentsSettingsDialog.ThreadDepth -> ThreadDepthIndicatorsDialog(
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) = SingleChoiceDialog(
    title = title,
    options = options,
    selected = selected,
    onDismiss = onDismiss,
    onSelected = {
        onSelected(it)
        onDismiss()
    },
)

private val CommentsBooleanSetting.preferenceKey: String
    get() = when (this) {
        CommentsBooleanSetting.Border -> UserPreferenceKeys.COMMENT_CARD_BORDER
        CommentsBooleanSetting.CollectLinks -> UserPreferenceKeys.COLLECT_LINKS_IN_COMMENTS
        CommentsBooleanSetting.EmphasizeMetadata -> UserPreferenceKeys.HIGHLIGHT_COMMENT_META
        CommentsBooleanSetting.Dividers -> UserPreferenceKeys.COMMENT_DIVIDERS
        CommentsBooleanSetting.TopLevelIndicators -> UserPreferenceKeys.TOP_LEVEL_THREAD_INDICATORS
        CommentsBooleanSetting.Scrollbar -> UserPreferenceKeys.COMMENTS_SCROLLBAR
        CommentsBooleanSetting.AnimateChanges -> UserPreferenceKeys.COMMENTS_ANIMATION
        CommentsBooleanSetting.HeaderTint -> UserPreferenceKeys.COMMENTS_HEADER_TINT
        CommentsBooleanSetting.HeaderPreviewImage -> UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE
        CommentsBooleanSetting.CollapseParent -> UserPreferenceKeys.COLLAPSE_PARENT
        CommentsBooleanSetting.CollapseTopLevel -> UserPreferenceKeys.COLLAPSE_TOP_LEVEL
        CommentsBooleanSetting.SwapTap -> UserPreferenceKeys.COMMENTS_SWAP_LONG
        CommentsBooleanSetting.NavigationButtons -> UserPreferenceKeys.SCROLL_NAVIGATION
        CommentsBooleanSetting.SmoothScroll -> UserPreferenceKeys.COMMENTS_SMOOTH_SCROLL
    }
