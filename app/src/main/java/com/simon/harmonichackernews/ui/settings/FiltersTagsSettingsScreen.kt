package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.Utils

@Composable
fun FiltersTagsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var tagRefresh by remember { mutableIntStateOf(0) }
    var filterDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var tagDialogUser by rememberSaveable { mutableStateOf<String?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }

    val tags = Utils.getUserTagsWithOriginalUsernames(context)
        .entries
        .sortedBy { it.key.lowercase() }

    SettingsPage(
        title = "Filters and tags",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refresh + tagRefresh,
    ) {
        item {
            SettingsCategory("Filters") {
                SettingRow(
                    title = "Filter by story title",
                    icon = R.drawable.ic_title,
                    onClick = { filterDialog = "title" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Filter by domain",
                    icon = R.drawable.ic_public,
                    onClick = { filterDialog = "domain" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Blocked users",
                    icon = R.drawable.ic_person,
                    onClick = { filterDialog = "users" },
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
                            onClick = { profileUser = entry.key },
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = { tagDialogUser = entry.key },
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

    when (filterDialog) {
        "title" -> FilterListDialog(
            preferenceKey = "pref_filter",
            title = "Filter by story title",
            subtitle = "Hide stories containing these words or phrases in the title",
            inputLabel = "Word or phrase",
            emptyMessage = "No story title filters",
            onDismiss = { filterDialog = null },
        )
        "domain" -> FilterListDialog(
            preferenceKey = "pref_filter_domains",
            title = "Filter by domain",
            subtitle = "Hide stories from these domains",
            inputLabel = "Domain",
            emptyMessage = "No domain filters",
            onDismiss = { filterDialog = null },
        )
        "users" -> FilterListDialog(
            preferenceKey = "pref_filter_users",
            title = "Blocked users",
            subtitle = "Hide stories and comments posted by these users",
            inputLabel = "Username",
            emptyMessage = "No blocked users",
            onDismiss = { filterDialog = null },
        )
    }

    tagDialogUser?.let { userName ->
        UserTagDialog(
            userName = userName,
            currentTag = Utils.getUserTag(context, userName),
            onDismiss = { tagDialogUser = null },
            onSaved = {
                tagRefresh++
                tagDialogUser = null
            },
        )
    }

    profileUser?.let { userName ->
        UserSettingsDialog(
            userName = userName,
            onDismiss = { profileUser = null },
            onTagChanged = { tagRefresh++ },
        )
    }
}
