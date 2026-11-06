package com.simon.harmonichackernews.ui.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared by Android's Stories and Settings hosts. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun calculateHarmonicPaneScaffoldDirective(
    adaptiveInfo: WindowAdaptiveInfo,
    windowWidth: Dp,
    supportsTwoPane: Boolean,
    isFoldable: Boolean,
): PaneScaffoldDirective {
    // Preserve v3.1's foldable activity-embedding threshold (issue #404). Material's default
    // waits until 840dp; enabling all medium windows would instead lower it to 600dp.
    val directive = if (supportsTwoPane && isFoldable && windowWidth >= 700.dp) {
        calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo)
    } else {
        calculatePaneScaffoldDirective(adaptiveInfo)
    }
    return if (supportsTwoPane) directive else directive.copy(maxHorizontalPartitions = 1)
}
