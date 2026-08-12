package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_action_work_off
import com.simon.harmonichackernews.resources.ic_delete
import com.simon.harmonichackernews.resources.ic_edit
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_title
import org.jetbrains.compose.resources.painterResource

enum class ContentFilterDialog { StoryTitle, Domain, User }

data class TaggedUserUi(val username: String, val tag: String)

data class FiltersTagsSettingsUiState(
    val tags: List<TaggedUserUi>,
    val hideJobs: Boolean,
)

@Composable
fun SharedFiltersTagsSettingsScreen(
    state: FiltersTagsSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onHideJobsChanged: (Boolean) -> Unit,
    onFilterRequested: (ContentFilterDialog) -> Unit,
    onProfileRequested: (String) -> Unit,
    onTagEditRequested: (String) -> Unit,
    onTagDeleteRequested: (String) -> Unit,
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = "Filters and tags",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Filters") {
                SettingRow(
                    title = "Filter by story title",
                    icon = Res.drawable.ic_title,
                    onClick = { onFilterRequested(ContentFilterDialog.StoryTitle) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Filter by domain",
                    icon = Res.drawable.ic_public,
                    onClick = { onFilterRequested(ContentFilterDialog.Domain) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Blocked users",
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
            }
        }
        item {
            SettingsCategory("Tagged users") {
                if (state.tags.isEmpty()) {
                    SettingRow(
                        title = "No user with tags",
                        icon = null,
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    state.tags.forEachIndexed { index, entry ->
                        SettingRow(
                            title = if (entry.tag.isBlank()) {
                                entry.username
                            } else {
                                "${entry.username} (${entry.tag})"
                            },
                            icon = Res.drawable.ic_person,
                            onClick = { onProfileRequested(entry.username) },
                            trailing = {
                                Row {
                                    IconButton(onClick = { onTagEditRequested(entry.username) }) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_edit),
                                            contentDescription = "Edit tag",
                                        )
                                    }
                                    IconButton(onClick = { onTagDeleteRequested(entry.username) }) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_delete),
                                            contentDescription = "Delete tag",
                                        )
                                    }
                                }
                            },
                        )
                        if (index != state.tags.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}
