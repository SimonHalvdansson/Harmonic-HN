package com.simon.harmonichackernews.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterButtonLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowCommentsFilterPreservesItsLabelAndSupportsLargeText() {
        val scale = mutableFloatStateOf(1f)
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    HarmonicFilterButton("Comments", true, 2, harmonicFilterButtonColors(), { clicks++ },
                        modifier = Modifier.width(104.dp), icon = Res.drawable.ic_comment)
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { scale.floatValue = fontScale }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Comments", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("Text must not clip at font scale $fontScale: size=${layout.size}, lines=${layout.lineCount}, width=${layout.didOverflowWidth}, paragraphWidth=${layout.multiParagraph.width}, intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
            if (fontScale == 1f) assertEquals(1, layout.lineCount)
            compose.onNodeWithText("Comments").performClick()
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
