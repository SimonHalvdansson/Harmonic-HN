package com.simon.harmonichackernews.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 31, 35], application = Application::class)
class WidgetWidthTest {
    @Test
    fun legacyWidthsFollowOrientation() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 420)
        }
        assertEquals(220, widgetWidth(options, Configuration.ORIENTATION_PORTRAIT))
        assertEquals(420, widgetWidth(options, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun missingOrientationWidthUsesTheOtherValidWidth() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
        }
        assertEquals(240, widgetWidth(options, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun absentOrInvalidSizesUseFallbackWidth() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1)
        }
        assertEquals(280, widgetWidth(options, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    @Config(sdk = [31, 35])
    fun launcherSizesTakePrecedenceOverLegacyBounds() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 600)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(
                SizeF(420f, 180f), SizeF(220f, 300f),
            ))
        }
        assertEquals(220, widgetWidth(options, Configuration.ORIENTATION_PORTRAIT))
        assertEquals(420, widgetWidth(options, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    @Config(sdk = [31, 35])
    fun singleLauncherSizeWorksInEitherOrientation() {
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(340f, 200f)))
        }
        assertEquals(340, widgetWidth(options, Configuration.ORIENTATION_PORTRAIT))
        assertEquals(340, widgetWidth(options, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    @Config(sdk = [31, 35])
    fun invalidLauncherSizesFallBackToLegacyBounds() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(0f, 200f)))
        }
        assertEquals(240, widgetWidth(options, Configuration.ORIENTATION_PORTRAIT))
    }
}
