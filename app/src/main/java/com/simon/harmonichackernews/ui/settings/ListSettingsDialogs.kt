package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.format.DelimitedListPolicy
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.font_entries
import com.simon.harmonichackernews.resources.font_values
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidSettingsMutator
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.utils.FontUtils
import org.jetbrains.compose.resources.stringArrayResource

private val SuggestedArchiveDomains = listOf(
    "ft.com",
    "wsj.com",
    "bloomberg.com",
    "economist.com",
    "foreignpolicy.com",
    "nytimes.com",
    "washingtonpost.com",
    "theatlantic.com",
    "newyorker.com",
    "technologyreview.com",
)

@Composable
fun FontSelectionDialog(readerMode: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val labels = stringArrayResource(Res.array.font_entries)
    val values = stringArrayResource(Res.array.font_values)
    val settings = AndroidUserSettings.get(context)
    SharedFontSelectionDialog(
        readerMode = readerMode,
        selected = if (readerMode) {
            settings.reading.readerModeFont
        } else {
            settings.story.font
        },
        options = remember(labels, values) { labels.zip(values) },
        onSelected = { value ->
            if (readerMode) {
                AndroidSettingsMutator.setReaderModeFont(context, value)
            } else {
                AndroidSettingsMutator.setFont(context, value)
                FontUtils.init(context)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun PreloadWebViewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val reading = AndroidUserSettings.get(context).reading
    SharedPreloadWebViewDialog(
        initialMode = reading.preloadWebViewMode,
        initialBattery = reading.preloadWebViewMinimumBattery,
        onSave = { mode, minimumBattery ->
            prefs.edit()
                .putString(UserPreferenceKeys.PRELOAD_WEBVIEW, mode)
                .putInt(UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY, minimumBattery)
                .apply()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun ArchiveRedirectDomainsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    SharedStringListEditorDialog(
        title = "Redirect to archive version",
        subtitle = "Choose domains where we should automatically redirect the webview " +
            "to the archive.is version. Useful for domains where paywalls are persistent.",
        inputLabel = "Domain",
        initialItems = AndroidUserSettings.get(context).reading.archiveRedirectDomains,
        emptyMessage = "No archive redirect domains",
        suggestedItems = SuggestedArchiveDomains,
        suggestionsLabel = "Suggested domains",
        parseInput = ArchiveRedirectPolicy::parseDomains,
        emptyInputError = "Enter a domain",
        onItemsChanged = { updated ->
            prefs.edit()
                .putString(UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS, updated.joinToString(","))
                .apply()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun FilterListDialog(
    preferenceKey: String,
    title: String,
    subtitle: String,
    inputLabel: String,
    emptyMessage: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    SharedStringListEditorDialog(
        title = title,
        subtitle = subtitle,
        inputLabel = inputLabel,
        initialItems = DelimitedListPolicy.parseCommaSeparated(
            prefs.getString(preferenceKey, ""),
        ),
        emptyMessage = emptyMessage,
        parseInput = DelimitedListPolicy::parseCommaSeparated,
        emptyInputError = "Enter a value",
        disableSuggestions = preferenceKey == "pref_filter_users",
        onItemsChanged = { updated ->
            prefs.edit().putString(preferenceKey, updated.joinToString(",")).apply()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun UserTagDialog(
    userName: String,
    currentTag: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val userTags = remember(context) {
        UserTagsRepository(AndroidKeyValueStore.defaults(context))
    }
    SharedUserTagDialog(
        currentTag = currentTag,
        onSave = { tag ->
            userTags.setTag(userName, tag)
            onSaved(tag)
        },
        onDismiss = onDismiss,
    )
}
