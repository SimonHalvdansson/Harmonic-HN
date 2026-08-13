package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource as androidPainterResource
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.FaviconPreferences
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
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { StoriesSettingsPresenter(app.settings) }
    val selected = presenter.snapshot.story.faviconProvider

    SharedFaviconProviderDialog(
        selected = selected,
        options = FaviconProviderOptions.map { provider ->
            FaviconProviderUiOption(
                value = provider.value,
                label = provider.label,
                urlTemplate = FaviconUrlBuilder.faviconUrlTemplate(provider.value),
                icon = androidPainterResource(
                    AndroidSettingsResources.faviconProviderIcon(provider.value),
                ),
            )
        },
        onProviderSelected = { provider ->
            presenter.setFaviconProvider(provider)
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
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { CommentsSettingsPresenter(app.settings) }
    var mode by remember {
        mutableStateOf(
            app.settings.snapshot().comments.depthIndicatorMode,
        )
    }
    val theme = ThemeUtils.getPreferredTheme(context)
    SharedThreadDepthIndicatorsDialog(
        mode = mode,
        indicatorColors = List(CommentDepthIndicatorUtils.COMMENT_DEPTH_COLOR_COUNT) { index ->
            colorResource(CommentDepthIndicatorUtils.getColorResource(context, mode, theme, index))
        },
        onModeSelected = { selectedMode ->
            presenter.setDepthIndicatorMode(selectedMode)
            mode = selectedMode
        },
        onDismiss = onDismiss,
    )
}
