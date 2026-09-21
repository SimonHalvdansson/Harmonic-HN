package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Compact, elevated refresh disk with the same accent in every refreshable screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarmonicPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = if (HarmonicTheme.isDark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
        elevation = 4.dp,
    ) {
        Crossfade(isRefreshing) { refreshing ->
            HarmonicLoadingIndicator(
                modifier = Modifier.size(28.dp).rotate(
                    if (refreshing) 0f else (state.distanceFraction - 1f).coerceAtLeast(0f) * 180f,
                ),
                color = scheme.primary,
                progress = if (refreshing) null else ({ state.distanceFraction }),
            )
        }
    }
}
