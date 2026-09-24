package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

/** Resource qualifiers enable the gutter only when the current window is at least 1080dp wide. */
@Composable
internal fun animatedExtraPanePadding(): Dp {
    val repository = LocalHarmonicUiDependencies.current.settings
    val initialSettings = remember(repository) { repository.snapshot() }
    val settings by repository.updates.collectAsState(initial = initialSettings)
    val standardPadding = dimensionResource(R.dimen.extra_pane_padding)
    val padding by animateDpAsState(
        targetValue = standardPadding * settings.appearance.extraSidePadding.fraction,
        animationSpec = tween(300),
        label = "Extra side padding",
    )
    return padding
}
