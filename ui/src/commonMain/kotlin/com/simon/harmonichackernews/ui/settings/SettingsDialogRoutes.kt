package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.font_entries
import com.simon.harmonichackernews.resources.font_values
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.painterResource
import com.simon.harmonichackernews.settings.FaviconProviderCatalog
import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.resources.ic_favicon_provider_duckduckgo
import com.simon.harmonichackernews.resources.ic_favicon_provider_google
import com.simon.harmonichackernews.resources.ic_favicon_provider_twenty
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun faviconProviderPainter(provider: String): Painter = painterResource(
    when (FaviconPreferences.sanitizeProvider(provider)) {
        FaviconPreferences.DUCK_DUCK_GO -> Res.drawable.ic_favicon_provider_duckduckgo
        FaviconPreferences.TWENTY -> Res.drawable.ic_favicon_provider_twenty
        else -> Res.drawable.ic_favicon_provider_google
    },
)

@Composable
fun FaviconProviderRoute(
    selected: String? = null,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { StoriesSettingsPresenter(app.settings) }
    val selectedValue = selected ?: presenter.snapshot.story.faviconProvider
    FaviconProviderDialog(
        selected = selectedValue,
        options = FaviconProviderCatalog.options.map { provider ->
            FaviconProviderUiOption(
                value = provider.value,
                label = provider.label,
                urlTemplate = provider.urlTemplate,
                icon = faviconProviderPainter(provider.value),
            )
        },
        onProviderSelected = { provider ->
            if (selected == null) presenter.setFaviconProvider(provider)
            onProviderSelected(provider)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun FontSelectionRoute(readerMode: Boolean, onDismiss: () -> Unit) {
    val labels = stringArrayResource(Res.array.font_entries)
    val values = stringArrayResource(Res.array.font_values)
    val app = LocalHarmonicUiDependencies.current
    val webPresenter = remember(app) { WebLinksSettingsPresenter(app.settings) }
    val appearancePresenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val snapshot = app.settings.snapshot()
    FontSelectionDialog(
        readerMode = readerMode,
        selected = if (readerMode) snapshot.reading.readerFont else snapshot.story.fontChoice,
        options = remember(labels, values) { labels.zip(values.map(AppFont::fromStored)) },
        onSelected = { value ->
            if (readerMode) webPresenter.setReaderFont(value) else appearancePresenter.setFont(value)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun UserTagRoute(
    userName: String,
    currentTag: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) {
        FiltersTagsSettingsPresenter(app.settings, app.contentFilters, app.userTags)
    }
    UserTagDialog(
        currentTag = currentTag,
        onSave = { tag ->
            presenter.setTag(userName, tag)
            onSaved(tag)
        },
        onDismiss = onDismiss,
    )
}
