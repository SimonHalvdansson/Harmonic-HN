package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.SettingsCommentPreviewModel
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils

@Composable
fun CommentsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh

    val displayStyle = SettingsUtils.getPreferredCommentDisplayStyle(context)
    val showBorder = prefs.getBoolean(SettingsUtils.PREF_COMMENT_CARD_BORDER, true)
    val textSize = SettingsUtils.getPreferredCommentTextSize(context)
    val textSizeOffset = SettingsUtils.getCommentTextSizeOffset(textSize)
    val collectLinks = prefs.getBoolean(SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS, true)
    val emphasizeMeta = prefs.getBoolean(SettingsUtils.PREF_HIGHLIGHT_COMMENT_META, false)
    val depthMode = SettingsUtils.getPreferredCommentDepthIndicatorMode(context)
    val showDividers = prefs.getBoolean(SettingsUtils.PREF_COMMENT_DIVIDERS, false)
    val preferredFont = SettingsUtils.getPreferredFont(context)
    val storyTintEnabled = SettingsUtils.shouldTintCardUsingPreview(context)
    val storyPreviewEnabled =
        SettingsUtils.getPreferredStoryPreviewImageMode(context) !=
            SettingsUtils.STORY_PREVIEW_IMAGE_OFF
    val swapTap = prefs.getBoolean("pref_comments_swap_long", false)

    SettingsPage(
        title = "Comments",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refresh,
        pinnedContent = {
            CommentItem(
                model = SettingsCommentPreviewModel,
                style = CommentItemStyle(
                    cardStyle = displayStyle == SettingsUtils.COMMENT_DISPLAY_STYLE_CARD,
                    showCardBorder = showBorder,
                    textSize = textSize,
                    collectLinks = collectLinks,
                    emphasizeMeta = emphasizeMeta,
                    depthIndicatorMode = depthMode,
                    showDivider = showDividers,
                    preferredFont = preferredFont,
                ),
            )
        },
    ) {
        item {
            SettingsCategory("Comments display") {
                SegmentedSetting(
                    title = "Display style",
                    options = listOf(
                        SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD to "Standard",
                        SettingsUtils.COMMENT_DISPLAY_STYLE_CARD to "Card",
                    ),
                    selected = displayStyle,
                    onSelected = {
                        prefs.edit()
                            .putString(SettingsUtils.PREF_COMMENT_DISPLAY_STYLE, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Border",
                    icon = R.drawable.ic_select,
                    checked = showBorder,
                    enabled = displayStyle == SettingsUtils.COMMENT_DISPLAY_STYLE_CARD,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_COMMENT_CARD_BORDER, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = if (textSizeOffset >= 0) {
                        "+$textSizeOffset"
                    } else {
                        "$textSizeOffset"
                    },
                    value = textSizeOffset.toFloat(),
                    valueRange = SettingsUtils.MIN_COMMENT_TEXT_SIZE_OFFSET.toFloat()..
                        SettingsUtils.MAX_COMMENT_TEXT_SIZE_OFFSET.toFloat(),
                    steps = SettingsUtils.MAX_COMMENT_TEXT_SIZE_OFFSET -
                        SettingsUtils.MIN_COMMENT_TEXT_SIZE_OFFSET - 1,
                    onValueChange = {
                        val size = SettingsUtils.getCommentTextSizeForOffset(it.toInt())
                        prefs.edit()
                            .putString(
                                SettingsUtils.PREF_COMMENT_TEXT_SIZE,
                                if (size == size.toInt().toFloat()) {
                                    size.toInt().toString()
                                } else {
                                    size.toString()
                                },
                            )
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Collect links in comments",
                    icon = R.drawable.ic_link,
                    checked = prefs.getBoolean(
                        SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS,
                        true,
                    ),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_COLLECT_LINKS_IN_COMMENTS, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Emphasize meta",
                    icon = R.drawable.ic_dropdown_menu,
                    checked = emphasizeMeta,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_HIGHLIGHT_COMMENT_META, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Thread depth indicators",
                    summary = CommentDepthIndicatorUtils.getModeLabel(depthMode),
                    icon = R.drawable.ic_palette,
                    onClick = { dialog = "thread_depth" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Dividers",
                    icon = R.drawable.ic_horizontal_rule,
                    checked = prefs.getBoolean(SettingsUtils.PREF_COMMENT_DIVIDERS, false),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_COMMENT_DIVIDERS, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show top level thread indicators",
                    summary = "Makes it easier to separate top level comments",
                    icon = R.drawable.ic_format_align_left,
                    checked = prefs.getBoolean("pref_top_level_thread_indicators", false),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_top_level_thread_indicators", it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show scrollbar",
                    icon = R.drawable.ic_swipe_vertical,
                    checked = prefs.getBoolean("pref_comments_scrollbar", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_comments_scrollbar", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Animate comment expand/collapse",
                    icon = R.drawable.ic_animation,
                    checked = prefs.getBoolean("pref_comments_animation", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_comments_animation", it).apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Header display") {
                SwitchSettingRow(
                    title = "Background tint",
                    summary = if (storyTintEnabled) null else "Disabled because story tint is off",
                    icon = R.drawable.ic_palette,
                    checked = prefs.getBoolean(
                        SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_TINT,
                        true,
                    ),
                    enabled = storyTintEnabled,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_TINT, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Preview image",
                    icon = R.drawable.ic_image,
                    checked = prefs.getBoolean(
                        SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE,
                        true,
                    ),
                    enabled = storyPreviewEnabled,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(
                                SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE,
                                it,
                            )
                            .apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Interactions") {
                SwitchSettingRow(
                    title = "Hide text of collapsed comments",
                    icon = R.drawable.ic_comment,
                    checked = prefs.getBoolean("pref_collapse_parent", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_collapse_parent", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Auto-collapse top level comments",
                    icon = R.drawable.ic_minimize,
                    checked = prefs.getBoolean("pref_collapse_top_level", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_collapse_top_level", it).apply()
                    },
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Comment tap action",
                    summary = if (swapTap) {
                        "Long press: Toggle visibility"
                    } else {
                        "Long press: Details"
                    },
                    options = listOf(
                        "visibility" to "Toggle visibility",
                        "details" to "Details",
                    ),
                    selected = if (swapTap) "details" else "visibility",
                    buttonHeightRes = R.dimen.compose_settings_comment_tap_action_button_height,
                    onSelected = {
                        prefs.edit()
                            .putBoolean("pref_comments_swap_long", it == "details")
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Disable swipe back from comments",
                    icon = R.drawable.ic_swipe,
                    checked = prefs.getBoolean("pref_comments_disable_swipeback", true),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_comments_disable_swipeback", it)
                            .apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Comment sorting",
                    summary = prefs.getString("pref_comment_sorting", "Default"),
                    icon = R.drawable.ic_filter_list,
                    onClick = { dialog = "sorting" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Comments provider",
                    summary = when (
                        prefs.getString("pref_comments_provider", "algolia")
                    ) {
                        "official" -> "Official Hacker News API"
                        else -> "Algolia API"
                    },
                    icon = R.drawable.ic_api,
                    onClick = { dialog = "provider" },
                )
            }
        }

        item {
            SettingsCategory("Navigation") {
                SwitchSettingRow(
                    title = "Show navigation buttons",
                    summary = "Navigate between top level comments",
                    icon = R.drawable.ic_explore,
                    checked = prefs.getBoolean("pref_scroll_navigation", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_scroll_navigation", it).apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Volume buttons for navigation",
                    summary = when (
                        prefs.getString("pref_comments_volume_navigation", "disabled")
                    ) {
                        "top_level" -> "Top level comments"
                        "all" -> "All comments"
                        else -> "Disabled"
                    },
                    icon = R.drawable.ic_swipe_vertical,
                    onClick = { dialog = "volume" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Smooth scroll comments",
                    icon = R.drawable.ic_comments_animation_navigation,
                    checked = prefs.getBoolean(
                        "pref_comments_animation_navigation",
                        true,
                    ),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_comments_animation_navigation", it)
                            .apply()
                    },
                )
            }
        }
    }

    when (dialog) {
        "sorting" -> SingleChoiceDialog(
            title = "Comment sorting",
            options = resources.getStringArray(R.array.comment_sorting)
                .map { it to it },
            selected = prefs.getString("pref_comment_sorting", "Default") ?: "Default",
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString("pref_comment_sorting", it).apply()
                dialog = null
            },
        )

        "provider" -> SingleChoiceDialog(
            title = "Comments provider",
            options = listOf(
                "algolia" to "Algolia API",
                "official" to "Official Hacker News API",
            ),
            selected = prefs.getString("pref_comments_provider", "algolia") ?: "algolia",
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString("pref_comments_provider", it).apply()
                dialog = null
            },
        )

        "volume" -> SingleChoiceDialog(
            title = "Volume buttons for navigation",
            options = listOf(
                "disabled" to "Disabled",
                "top_level" to "Top level comments",
                "all" to "All comments",
            ),
            selected = prefs.getString(
                "pref_comments_volume_navigation",
                "disabled",
            ) ?: "disabled",
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString("pref_comments_volume_navigation", it).apply()
                dialog = null
            },
        )

        "thread_depth" -> ThreadDepthIndicatorsDialog(
            onDismiss = { dialog = null },
        )
    }
}
