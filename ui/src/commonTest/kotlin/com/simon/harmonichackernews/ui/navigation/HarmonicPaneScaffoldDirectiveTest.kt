package com.simon.harmonichackernews.ui.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class HarmonicPaneScaffoldDirectiveTest {
    @Test
    fun foldablesSplitAt700DpIncludingThePreviouslyRegressedMediumWidths() {
        for ((width, panes) in listOf(699 to 1, 700 to 2, 701 to 2, 839 to 2, 840 to 2)) {
            assertEquals(panes, directive(width, isFoldable = true).maxHorizontalPartitions, "$width dp")
        }
    }

    @Test
    fun narrowFoldableWindowsRemainSinglePane() {
        for (width in listOf(400, 599, 600, 650)) {
            assertEquals(1, directive(width, isFoldable = true).maxHorizontalPartitions, "$width dp")
        }
    }

    @Test
    fun nonFoldablesKeepTheMaterialDirectiveIncludingThe840DpBreakpoint() {
        for (width in listOf(400, 600, 699, 700, 839, 840, 1200, 1600)) {
            assertEquals(
                calculatePaneScaffoldDirective(adaptiveInfo(width)),
                directive(width, isFoldable = false),
                "$width dp",
            )
        }
        assertEquals(1, directive(839, isFoldable = false).maxHorizontalPartitions)
        assertEquals(2, directive(840, isFoldable = false).maxHorizontalPartitions)
    }

    @Test
    fun hostsWithoutTwoPaneSupportRemainSinglePane() {
        for (foldable in listOf(false, true)) for (width in listOf(700, 839, 840, 1200)) {
            assertEquals(
                1,
                directive(width, isFoldable = foldable, supportsTwoPane = false).maxHorizontalPartitions,
                "$width dp, foldable=$foldable",
            )
        }
    }

    @Test
    fun mediumFoldableUsesHorizontalPanesWhilePreservingTabletopPolicy() {
        for (tabletop in listOf(false, true)) {
            val directive = calculateHarmonicPaneScaffoldDirective(
                adaptiveInfo = adaptiveInfo(700, Posture(isTabletop = tabletop)),
                windowWidth = 700.dp,
                supportsTwoPane = true,
                isFoldable = true,
            )
            assertEquals(2, directive.maxHorizontalPartitions)
            assertEquals(if (tabletop) 2 else 1, directive.maxVerticalPartitions)
        }
    }

    private fun directive(width: Int, isFoldable: Boolean, supportsTwoPane: Boolean = true) =
        calculateHarmonicPaneScaffoldDirective(
            adaptiveInfo = adaptiveInfo(width),
            windowWidth = width.dp,
            supportsTwoPane = supportsTwoPane,
            isFoldable = isFoldable,
        )

    private fun adaptiveInfo(width: Int, posture: Posture = Posture()) = WindowAdaptiveInfo(
        windowSizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(width, 900),
        windowPosture = posture,
    )
}
