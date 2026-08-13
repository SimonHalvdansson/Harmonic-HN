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
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.FaviconProviderCatalog
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.ThemeUtils

@Composable
fun FaviconProviderDialog(
    selected: String? = null,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { StoriesSettingsPresenter(app.settings) }
    val selectedValue = selected ?: presenter.snapshot.story.faviconProvider

    SharedFaviconProviderDialog(
        selected = selectedValue,
        options = FaviconProviderCatalog.options.map { provider ->
            FaviconProviderUiOption(
                value = provider.value,
                label = provider.label,
                urlTemplate = provider.urlTemplate,
                icon = androidPainterResource(
                    AndroidSettingsResources.faviconProviderIcon(provider.value),
                ),
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
