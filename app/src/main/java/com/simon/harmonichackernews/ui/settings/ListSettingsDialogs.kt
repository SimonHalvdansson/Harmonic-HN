package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.format.DelimitedListPolicy
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.font_entries
import com.simon.harmonichackernews.resources.font_values
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.ContentFilterType
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
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
    val labels = stringArrayResource(Res.array.font_entries)
    val values = stringArrayResource(Res.array.font_values)
    val app = LocalHarmonicUiDependencies.current
    val webPresenter = remember(app) { WebLinksSettingsPresenter(app.settings) }
    val appearancePresenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val snapshot = app.settings.snapshot()
    SharedFontSelectionDialog(
        readerMode = readerMode,
        selected = if (readerMode) {
            snapshot.reading.readerFont
        } else {
            snapshot.story.fontChoice
        },
        options = remember(labels, values) {
            labels.zip(values.map { AppFont.fromStored(it) })
        },
        onSelected = { value ->
            if (readerMode) {
                webPresenter.setReaderFont(value)
            } else {
                appearancePresenter.setFont(value)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun PreloadWebViewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { WebLinksSettingsPresenter(app.settings) }
    val reading = presenter.snapshot.reading
    SharedPreloadWebViewDialog(
        initialMode = reading.preloadMode,
        initialBattery = reading.preloadWebViewMinimumBattery,
        onSave = { mode, minimumBattery ->
            presenter.setPreload(mode, minimumBattery)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun ArchiveRedirectDomainsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { WebLinksSettingsPresenter(app.settings) }
    SharedStringListEditorDialog(
        title = "Redirect to archive version",
        subtitle = "Choose domains where we should automatically redirect the webview " +
            "to the archive.is version. Useful for domains where paywalls are persistent.",
        inputLabel = "Domain",
        initialItems = presenter.snapshot.reading.archiveRedirectDomains,
        emptyMessage = "No archive redirect domains",
        suggestedItems = SuggestedArchiveDomains,
        suggestionsLabel = "Suggested domains",
        parseInput = ArchiveRedirectPolicy::parseDomains,
        emptyInputError = "Enter a domain",
        onItemsChanged = { updated ->
            presenter.setArchiveDomains(updated)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun FilterListDialog(
    type: ContentFilterType,
    title: String,
    subtitle: String,
    inputLabel: String,
    emptyMessage: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) {
        FiltersTagsSettingsPresenter(app.settings, app.contentFilters, app.userTags)
    }
    SharedStringListEditorDialog(
        title = title,
        subtitle = subtitle,
        inputLabel = inputLabel,
        initialItems = presenter.filterItems(type),
        emptyMessage = emptyMessage,
        parseInput = DelimitedListPolicy::parseCommaSeparated,
        emptyInputError = "Enter a value",
        disableSuggestions = type == ContentFilterType.USER,
        onItemsChanged = { updated ->
            presenter.setFilterItems(type, updated)
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
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) {
        FiltersTagsSettingsPresenter(app.settings, app.contentFilters, app.userTags)
    }
    SharedUserTagDialog(
        currentTag = currentTag,
        onSave = { tag ->
            presenter.setTag(userName, tag)
            onSaved(tag)
        },
        onDismiss = onDismiss,
    )
}
