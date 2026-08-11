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
import com.simon.harmonichackernews.settings.AndroidSettingsMutator
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.ThemeUtils

private data class AndroidFaviconProviderOption(
    val value: String,
    val label: String,
)

private val FaviconProviderOptions = listOf(
    AndroidFaviconProviderOption(FaviconPreferences.GOOGLE, "Google"),
    AndroidFaviconProviderOption(FaviconPreferences.DUCK_DUCK_GO, "DuckDuckGo"),
    AndroidFaviconProviderOption(FaviconPreferences.TWENTY, "Twenty icons"),
)

@Composable
fun FaviconProviderDialog(
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val selected = AndroidUserSettings.get(context).story.faviconProvider

    SharedFaviconProviderDialog(
        selected = selected,
        options = FaviconProviderOptions.map { provider ->
            FaviconProviderUiOption(
                value = provider.value,
                label = provider.label,
                urlTemplate = FaviconLoader.getFaviconUrlSchema(provider.value),
                icon = androidPainterResource(
                    AndroidSettingsResources.faviconProviderIcon(provider.value),
                ),
            )
        },
        onProviderSelected = { provider ->
            prefs.edit().putString(UserPreferenceKeys.FAVICON_PROVIDER, provider).apply()
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
            AndroidUserSettings.get(context).comments.depthIndicatorMode,
        )
    }
    val theme = ThemeUtils.getPreferredTheme(context)
    SharedThreadDepthIndicatorsDialog(
        mode = mode,
        indicatorColors = List(CommentDepthIndicatorUtils.COMMENT_DEPTH_COLOR_COUNT) { index ->
            colorResource(CommentDepthIndicatorUtils.getColorResource(context, mode, theme, index))
        },
        onModeSelected = { selectedMode ->
            AndroidSettingsMutator.setCommentDepthIndicatorMode(context, selectedMode)
            mode = selectedMode
        },
        onDismiss = onDismiss,
    )
}
