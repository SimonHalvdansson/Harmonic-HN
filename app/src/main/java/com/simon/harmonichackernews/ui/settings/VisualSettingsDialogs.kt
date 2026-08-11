package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource as androidPainterResource
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils

private data class AndroidFaviconProviderOption(
    val value: String,
    val label: String,
)

private val FaviconProviderOptions = listOf(
    AndroidFaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_GOOGLE, "Google"),
    AndroidFaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO, "DuckDuckGo"),
    AndroidFaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_TWENTY, "Twenty icons"),
)

@Composable
fun FaviconProviderDialog(
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val selected = SettingsUtils.getPreferredFaviconProvider(context)

    SharedFaviconProviderDialog(
        selected = selected,
        options = FaviconProviderOptions.map { provider ->
            FaviconProviderUiOption(
                value = provider.value,
                label = provider.label,
                urlTemplate = FaviconLoader.getFaviconUrlSchema(provider.value),
                icon = androidPainterResource(
                    SettingsUtils.getFaviconProviderIconResource(provider.value),
                ),
            )
        },
        onProviderSelected = { provider ->
            prefs.edit().putString(SettingsUtils.PREF_FAVICON_PROVIDER, provider).apply()
            onProviderSelected(provider)
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun ThreadDepthIndicatorsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            SettingsUtils.getPreferredCommentDepthIndicatorMode(context),
        )
    }
    val theme = ThemeUtils.getPreferredTheme(context)
    SharedThreadDepthIndicatorsDialog(
        mode = mode,
        indicatorColors = List(CommentDepthIndicatorUtils.COMMENT_DEPTH_COLOR_COUNT) { index ->
            colorResource(CommentDepthIndicatorUtils.getColorResource(context, mode, theme, index))
        },
        onModeSelected = { selectedMode ->
            SettingsUtils.setPreferredCommentDepthIndicatorMode(context, selectedMode)
            mode = selectedMode
        },
        onDismiss = onDismiss,
    )
}
