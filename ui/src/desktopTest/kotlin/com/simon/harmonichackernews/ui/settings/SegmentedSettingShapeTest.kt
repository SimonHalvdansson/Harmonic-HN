package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class SegmentedSettingShapeTest {
    @BeforeTest
    fun initializeDesktopGraphics() {
        ImageComposeScene(1, 1).close()
    }

    @Test
    fun springOvershootDoesNotShrinkTheFixedOuterCorners() {
        for (first in listOf(true, false)) {
            for (innerRadius in listOf(20f, 22f, 20.5f, 20f)) {
                val shape = segmentedSettingShape(first, !first, 20.dp, innerRadius.dp, 40.dp)
                val outline = shape.createOutline(Size(200f, 40f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
                assertEquals(20f, outline.roundRect.topLeftCornerRadius.x)
                assertEquals(20f, outline.roundRect.topRightCornerRadius.x)
            }
        }
    }

    @Test
    fun normalPressAndSelectionMorphsKeepTheirCorners() {
        for (innerRadius in listOf(4f, 8f, 15f)) {
            val shape = segmentedSettingShape(true, false, 20.dp, innerRadius.dp, 40.dp)
            val outline = shape.createOutline(Size(200f, 40f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
            assertEquals(20f, outline.roundRect.topLeftCornerRadius.x)
            assertEquals(innerRadius, outline.roundRect.topRightCornerRadius.x)
        }
    }
}
