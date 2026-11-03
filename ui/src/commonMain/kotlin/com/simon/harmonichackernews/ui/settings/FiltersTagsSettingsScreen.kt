package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.content.userTagColor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_action_work_off
import com.simon.harmonichackernews.resources.ic_delete
import com.simon.harmonichackernews.resources.ic_edit
import com.simon.harmonichackernews.resources.ic_feed_unslop
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_title
import com.simon.harmonichackernews.resources.settings_section_filters_tags
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class ContentFilterDialog { StoryTitle, Domain, User }

data class TaggedUserUi(
    val username: String,
    val tag: String,
    val color: UserTagColor = UserTagColor.Theme,
)

data class FiltersTagsSettingsUiState(
    val tags: List<TaggedUserUi>,
    val hideJobs: Boolean,
    val legacyTags: List<TaggedUserUi> = emptyList(),
    val blockedUserCount: Int = 0,
    val legacyBlockedUserCount: Int = 0,
    val titleFilterCount: Int = 0,
    val domainFilterCount: Int = 0,
)

@Composable
fun FiltersTagsSettingsScreen(
    state: FiltersTagsSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onHideJobsChanged: (Boolean) -> Unit,
    onAdditionalFrontpagesRequested: () -> Unit,
    onFilterRequested: (ContentFilterDialog) -> Unit,
    onProfileRequested: (String) -> Unit,
    onTagEditRequested: (String) -> Unit,
    onTagDeleteRequested: (String) -> Unit,
    onLegacyTagReviewRequested: (String) -> Unit = {},
    onLegacyTagDeleteRequested: (String) -> Unit = {},
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_filters_tags),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Filters") {
                SettingRow(
                    title = "Filter by story title",
                    summary = "${state.titleFilterCount} active ${if (state.titleFilterCount == 1) "filter" else "filters"}",
                    icon = Res.drawable.ic_title,
                    onClick = { onFilterRequested(ContentFilterDialog.StoryTitle) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Filter by domain",
                    summary = "${state.domainFilterCount} active ${if (state.domainFilterCount == 1) "filter" else "filters"}",
                    icon = Res.drawable.ic_public,
                    onClick = { onFilterRequested(ContentFilterDialog.Domain) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Blocked users",
                    summary = "${state.blockedUserCount} active" + if (state.legacyBlockedUserCount > 0)
                        " · ${state.legacyBlockedUserCount} ${if (state.legacyBlockedUserCount == 1) "needs" else "need"} review (inactive)" else " · Case-sensitive",
                    icon = Res.drawable.ic_person,
                    onClick = { onFilterRequested(ContentFilterDialog.User) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Hide job posts",
                    summary = "Includes \"Who is hiring\" posts",
                    icon = Res.drawable.ic_action_work_off,
                    checked = state.hideJobs,
                    onCheckedChange = onHideJobsChanged,
                )
                SettingsDivider()
                SettingRow(
                    title = "Hide AI posts",
                    summary = "Using unslop.news RSS",
                    icon = Res.drawable.ic_feed_unslop,
                    onClick = onAdditionalFrontpagesRequested,
                )
            }
        }
        item {
            SettingsCategory("Tagged users") {
                AnimatedContent(
                    targetState = state.tags,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (
                            fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(120))
                        ).using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    tween(240, easing = FastOutSlowInEasing)
                                },
                            ),
                        )
                    },
                    contentKey = { tags -> tags.joinToString { "${it.username}:${it.tag}:${it.color}" } },
                    label = "tagged users",
                ) { tags ->
                    Column(Modifier.fillMaxWidth()) {
                        if (tags.isEmpty()) {
                            SettingRow(
                                title = "No active user tags",
                                icon = null,
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            tags.forEachIndexed { index, entry ->
                                SettingRow(
                                    title = entry.username,
                                    summary = entry.tag,
                                    summaryColor = userTagColor(entry.color),
                                    icon = Res.drawable.ic_person,
                                    onClick = { onProfileRequested(entry.username) },
                                    trailing = {
                                        Row {
                                            IconButton(
                                                onClick = { onTagEditRequested(entry.username) },
                                            ) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_edit),
                                                    contentDescription = "Edit tag",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onTagDeleteRequested(entry.username) },
                                            ) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_delete),
                                                    contentDescription = "Delete tag",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                )
                                if (index != tags.lastIndex) SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
        if (state.legacyTags.isNotEmpty()) item {
            SettingsCategory("Tags needing review (${state.legacyTags.size})") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Inactive until confirmed", color = MaterialTheme.colorScheme.primary)
                    Text("Older versions discarded capitalization. These tags are saved but will not appear on profiles or comments until you confirm the exact username.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                state.legacyTags.forEachIndexed { index, entry ->
                    SettingRow(
                        title = entry.username,
                        summary = "Inactive · ${entry.tag}",
                        summaryColor = userTagColor(entry.color),
                        icon = Res.drawable.ic_person,
                        onClick = { onLegacyTagReviewRequested(entry.username) },
                        trailing = {
                            Row {
                                IconButton(onClick = { onLegacyTagReviewRequested(entry.username) }) {
                                    Icon(painterResource(Res.drawable.ic_edit),
                                        "Review saved tag for ${entry.username}")
                                }
                                IconButton(onClick = { onLegacyTagDeleteRequested(entry.username) }) {
                                    Icon(painterResource(Res.drawable.ic_delete),
                                        "Remove saved tag for ${entry.username}")
                                }
                            }
                        },
                    )
                    if (index != state.legacyTags.lastIndex) SettingsDivider()
                }
            }
        }
    }
}
