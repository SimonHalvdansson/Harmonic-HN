package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.UserTagsRepository

@Composable
fun FiltersTagsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val userTags = remember(context) {
        UserTagsRepository(AndroidKeyValueStore.defaults(context))
    }
    val refresh = rememberPreferenceRefresh()
    var tagRefresh by remember { mutableIntStateOf(0) }
    var filterDialog by rememberSaveable { mutableStateOf<ContentFilterDialog?>(null) }
    var tagDialogUser by rememberSaveable { mutableStateOf<String?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    val tags = userTags.tags(normalizeUsernames = false)
        .map { TaggedUserUi(it.key, it.value) }
        .sortedBy { it.username.lowercase() }

    SharedFiltersTagsSettingsScreen(
        tags = tags,
        hideJobs = prefs.getBoolean("pref_hide_jobs", false),
        showNavigation = showNavigation,
        onBack = onBack,
        onHideJobsChanged = { prefs.edit().putBoolean("pref_hide_jobs", it).apply() },
        onFilterRequested = { filterDialog = it },
        onProfileRequested = { profileUser = it },
        onTagEditRequested = { tagDialogUser = it },
        onTagDeleteRequested = {
            userTags.setTag(it, "")
            tagRefresh++
        },
        contentVersion = refresh + tagRefresh,
    )

    filterDialog?.let { type ->
        val content = type.filterDialogContent
        FilterListDialog(
            preferenceKey = content.preferenceKey,
            title = content.title,
            subtitle = content.subtitle,
            inputLabel = content.inputLabel,
            emptyMessage = content.emptyMessage,
            onDismiss = { filterDialog = null },
        )
    }
    tagDialogUser?.let { userName ->
        UserTagDialog(
            userName = userName,
            currentTag = userTags.tagFor(userName),
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

private data class FilterDialogContent(
    val preferenceKey: String,
    val title: String,
    val subtitle: String,
    val inputLabel: String,
    val emptyMessage: String,
)

private val ContentFilterDialog.filterDialogContent: FilterDialogContent
    get() = when (this) {
        ContentFilterDialog.StoryTitle -> FilterDialogContent(
            "pref_filter",
            "Filter by story title",
            "Hide stories containing these words or phrases in the title",
            "Word or phrase",
            "No story title filters",
        )
        ContentFilterDialog.Domain -> FilterDialogContent(
            "pref_filter_domains",
            "Filter by domain",
            "Hide stories from these domains",
            "Domain",
            "No domain filters",
        )
        ContentFilterDialog.User -> FilterDialogContent(
            "pref_filter_users",
            "Blocked users",
            "Hide stories and comments posted by these users",
            "Username",
            "No blocked users",
        )
    }
