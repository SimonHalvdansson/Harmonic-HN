package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog

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
        indicatorColors = CommentDepthPaletteCatalog.colors(
            mode = mode,
            theme = theme,
            darkTheme = ThemeUtils.isDarkMode(context, theme),
        ),
        onModeSelected = { selectedMode ->
            presenter.setDepthIndicatorMode(selectedMode)
            mode = selectedMode
        },
        onDismiss = onDismiss,
    )
}
