package com.simon.harmonichackernews.ui.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.UserDialogFragment
import com.simon.harmonichackernews.UserTagDialogFragment
import com.simon.harmonichackernews.settings.FilterListDialogFragment
import com.simon.harmonichackernews.utils.Utils

@Composable
fun FiltersTagsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var tagRefresh by remember { mutableIntStateOf(0) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh + tagRefresh

    val tags = Utils.getUserTagsWithOriginalUsernames(context)
        .entries
        .sortedBy { it.key.lowercase() }

    SettingsPage(
        title = "Filters and tags",
        showNavigation = showNavigation,
        onBack = onBack,
    ) {
        item {
            SettingsCategory("Filters") {
                SettingRow(
                    title = "Filter by story title",
                    icon = R.drawable.ic_title,
                    onClick = {
                        activity?.supportFragmentManager?.let {
                            FilterListDialogFragment.show(
                                it,
                                "pref_filter",
                                "Filter by story title",
                                "Hide stories containing these words or phrases in the title",
                                "Word or phrase",
                                "No story title filters",
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Filter by domain",
                    icon = R.drawable.ic_public,
                    onClick = {
                        activity?.supportFragmentManager?.let {
                            FilterListDialogFragment.show(
                                it,
                                "pref_filter_domains",
                                "Filter by domain",
                                "Hide stories from these domains",
                                "Domain",
                                "No domain filters",
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Blocked users",
                    icon = R.drawable.ic_person,
                    onClick = {
                        activity?.supportFragmentManager?.let {
                            FilterListDialogFragment.show(
                                it,
                                "pref_filter_users",
                                "Blocked users",
                                "Hide stories and comments posted by these users",
                                "Username",
                                "No blocked users",
                            )
                        }
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Hide job posts",
                    summary = "Includes \"Who is hiring\" posts",
                    icon = R.drawable.ic_action_work_off,
                    checked = prefs.getBoolean("pref_hide_jobs", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_hide_jobs", it).apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Tagged users") {
                if (tags.isEmpty()) {
                    SettingRow(
                        title = "No user with tags",
                        icon = null,
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    tags.forEachIndexed { index, entry ->
                        SettingRow(
                            title = if (entry.value.isBlank()) {
                                entry.key
                            } else {
                                "${entry.key} (${entry.value})"
                            },
                            icon = R.drawable.ic_person,
                            onClick = {
                                (context as? AppCompatActivity)?.let { activity ->
                                    UserDialogFragment.showUserDialog(
                                        activity.supportFragmentManager,
                                        entry.key,
                                    )
                                }
                            },
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            activity?.supportFragmentManager?.let {
                                                UserTagDialogFragment.show(
                                                    it,
                                                    entry.key,
                                                    Utils.getUserTag(context, entry.key),
                                                ) {
                                                    tagRefresh++
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit),
                                            contentDescription = "Edit tag",
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            Utils.setUserTag(context, entry.key, "")
                                            tagRefresh++
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = "Delete tag",
                                        )
                                    }
                                }
                            },
                        )
                        if (index != tags.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}
