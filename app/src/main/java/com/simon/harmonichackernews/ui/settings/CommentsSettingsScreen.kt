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
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.utils.SettingsUtils
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun CommentsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<CommentsSettingsDialog?>(null) }
    val displayStyle = SettingsUtils.getPreferredCommentDisplayStyle(context)
    val textSize = SettingsUtils.getPreferredCommentTextSize(context)
    val textSizeOffset = TextPreferences.commentTextSizeOffset(textSize)
    val depthMode = SettingsUtils.getPreferredCommentDepthIndicatorMode(context)
    val state = CommentsSettingsUiState(
        displayStyle = displayStyle,
        cardStyleValue = SettingsUtils.COMMENT_DISPLAY_STYLE_CARD,
        standardStyleValue = SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD,
        showBorder = prefs.getBoolean(SettingsUtils.PREF_COMMENT_CARD_BORDER, true),
        textSize = textSize,
        textSizeOffset = textSizeOffset,
        minTextSizeOffset = SettingsUtils.MIN_COMMENT_TEXT_SIZE_OFFSET,
        maxTextSizeOffset = SettingsUtils.MAX_COMMENT_TEXT_SIZE_OFFSET,
        collectLinks = prefs.getBoolean(SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS, true),
        emphasizeMetadata = prefs.getBoolean(SettingsUtils.PREF_HIGHLIGHT_COMMENT_META, false),
        depthMode = depthMode,
        depthModeLabel = CommentDepthPreferences.modeLabel(depthMode),
        showDividers = prefs.getBoolean(SettingsUtils.PREF_COMMENT_DIVIDERS, false),
        preferredFont = SettingsUtils.getPreferredFont(context),
        topLevelIndicators = prefs.getBoolean("pref_top_level_thread_indicators", false),
        showScrollbar = prefs.getBoolean("pref_comments_scrollbar", false),
        animateChanges = prefs.getBoolean("pref_comments_animation", true),
        storyTintEnabled = SettingsUtils.shouldTintCardUsingPreview(context),
        headerTint = prefs.getBoolean(SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_TINT, true),
        storyPreviewEnabled = SettingsUtils.getPreferredStoryPreviewImageMode(context) !=
            SettingsUtils.STORY_PREVIEW_IMAGE_OFF,
        headerPreviewImage = prefs.getBoolean(
            SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE,
            true,
        ),
        collapseParent = prefs.getBoolean("pref_collapse_parent", false),
        collapseTopLevel = prefs.getBoolean("pref_collapse_top_level", false),
        swapTap = prefs.getBoolean("pref_comments_swap_long", false),
        sortingLabel = prefs.getString("pref_comment_sorting", "Default") ?: "Default",
        providerLabel = if (prefs.getString("pref_comments_provider", "algolia") == "official") {
            "Official Hacker News API"
        } else {
            "Algolia API"
        },
        showNavigationButtons = prefs.getBoolean("pref_scroll_navigation", false),
        volumeNavigationLabel = when (
            prefs.getString("pref_comments_volume_navigation", "disabled")
        ) {
            "top_level" -> "Top level comments"
            "all" -> "All comments"
            else -> "Disabled"
        },
        smoothScroll = prefs.getBoolean("pref_comments_animation_navigation", true),
    )
    SharedCommentsSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onDisplayStyleChanged = {
            prefs.edit().putString(SettingsUtils.PREF_COMMENT_DISPLAY_STYLE, it).apply()
        },
        onTextSizeOffsetChanged = { offset ->
            val size = TextPreferences.commentTextSizeForOffset(offset)
            val value = if (size == size.toInt().toFloat()) size.toInt().toString() else size.toString()
            prefs.edit().putString(SettingsUtils.PREF_COMMENT_TEXT_SIZE, value).apply()
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
            onSelected = { prefs.edit().putString("pref_comment_sorting", it).apply() },
        )
        CommentsSettingsDialog.Provider -> ChoiceDialog(
            title = "Comments provider",
            options = listOf("algolia" to "Algolia API", "official" to "Official Hacker News API"),
            selected = prefs.getString("pref_comments_provider", "algolia") ?: "algolia",
            onDismiss = { dialog = null },
            onSelected = { prefs.edit().putString("pref_comments_provider", it).apply() },
        )
        CommentsSettingsDialog.VolumeNavigation -> ChoiceDialog(
            title = "Volume buttons for navigation",
            options = listOf(
                "disabled" to "Disabled",
                "top_level" to "Top level comments",
                "all" to "All comments",
            ),
            selected = prefs.getString("pref_comments_volume_navigation", "disabled") ?: "disabled",
            onDismiss = { dialog = null },
            onSelected = { prefs.edit().putString("pref_comments_volume_navigation", it).apply() },
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
        CommentsBooleanSetting.Border -> SettingsUtils.PREF_COMMENT_CARD_BORDER
        CommentsBooleanSetting.CollectLinks -> SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS
        CommentsBooleanSetting.EmphasizeMetadata -> SettingsUtils.PREF_HIGHLIGHT_COMMENT_META
        CommentsBooleanSetting.Dividers -> SettingsUtils.PREF_COMMENT_DIVIDERS
        CommentsBooleanSetting.TopLevelIndicators -> "pref_top_level_thread_indicators"
        CommentsBooleanSetting.Scrollbar -> "pref_comments_scrollbar"
        CommentsBooleanSetting.AnimateChanges -> "pref_comments_animation"
        CommentsBooleanSetting.HeaderTint -> SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_TINT
        CommentsBooleanSetting.HeaderPreviewImage -> SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE
        CommentsBooleanSetting.CollapseParent -> "pref_collapse_parent"
        CommentsBooleanSetting.CollapseTopLevel -> "pref_collapse_top_level"
        CommentsBooleanSetting.SwapTap -> "pref_comments_swap_long"
        CommentsBooleanSetting.NavigationButtons -> "pref_scroll_navigation"
        CommentsBooleanSetting.SmoothScroll -> "pref_comments_animation_navigation"
    }
