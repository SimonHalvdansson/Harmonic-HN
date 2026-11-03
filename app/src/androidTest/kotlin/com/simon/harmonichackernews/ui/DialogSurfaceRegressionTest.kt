@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.PredictiveBackDialog
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.common.TranslucentBackButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogSurfaceRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dialogDimIsPartOfUnderlyingWindowAndIsRemovedOnDismissal() {
        var visible by mutableStateOf(false)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.fillMaxSize().background(Color.White))
                if (visible) PredictiveBackDialog(onDismissRequest = { visible = false }) {
                    Box(Modifier.size(100.dp).background(Color.Red)) { Text("Dialog") }
                }
            }
        }
        compose.waitForIdle()
        val before = activityWindowCenterRed()
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        // PixelCopy of the activity excludes WindowManager's separate dim and dialog surfaces.
        // This is the content Android can snapshot while another activity covers the app.
        compose.waitUntil(5_000) { activityWindowCenterRed() < before * 0.9 }
        val dimmed = activityWindowCenterRed()
        assertTrue("Dim must be rendered in the activity window: $dimmed vs $before", dimmed < before * 0.9)
        compose.waitUntil(5_000) { abs(activityWindowCenterRed() - before * .68f) <= 2 }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.waitUntil(5_000) { activityWindowCenterRed() == before }
        assertEquals(before, activityWindowCenterRed())
    }

    @Test fun nestedDialogDimsItsParentAtThirtyTwoPercentWithoutMultiplyingTheAppBackdrop() {
        var child by mutableStateOf(false)
        var parentWindow: android.view.Window? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, false) {
                Box(Modifier.fillMaxSize().background(Color.White))
                PredictiveBackDialog({}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    val window = (LocalView.current.parent as DialogWindowProvider).window
                    SideEffect { parentWindow = window }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(200.dp).background(Color.White))
                        if (child) PredictiveBackDialog({ child = false }) {
                            Box(Modifier.size(80.dp).background(Color.Red))
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { parentWindow != null && abs(activityWindowCenterRed() - 255 * .68f) <= 2 }
        fun outsideParentAlpha() = android.graphics.Color.alpha(windowPixel(requireNotNull(parentWindow), .25f, .25f))
        val outsideBefore = outsideParentAlpha()
        assertEquals("The parent window must be transparent outside its card", 0, outsideBefore)
        assertEquals(255, activityWindowCenterRed(requireNotNull(parentWindow)))
        compose.runOnIdle { child = true }
        compose.waitUntil(5_000) { abs(activityWindowCenterRed(requireNotNull(parentWindow)) - 255 * .68f) <= 2 }
        assertEquals("A child should dim the parent card without darkening the app backdrop again", outsideBefore, outsideParentAlpha())
    }

    @Test fun managedDialogKeepsSharedWindowMotionWhenAnEditorRequestsNoPlatformTranslation() {
        var configuredStyle: Int? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                PredictiveBackDialog(onDismissRequest = {}) {
                    PlatformDisableDialogWindowAnimations()
                    val window = (LocalView.current.parent as DialogWindowProvider).window
                    SideEffect { configuredStyle = window.attributes.windowAnimations }
                    Box(Modifier.size(100.dp).background(Color.Red)) { Text("Managed dialog") }
                }
            }
        }
        compose.waitUntil(5_000) { configuredStyle != null }
        val enabled = android.animation.ValueAnimator.areAnimatorsEnabled() && android.provider.Settings.Global.getFloat(
            compose.activity.contentResolver, android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, 1f,
        ) > 0f
        assertEquals(if (enabled) com.simon.harmonichackernews.ui.R.style.HarmonicDialogWindowAnimation else 0, configuredStyle)
    }

    @Test fun blurredBackButtonReceivesExactlyOneModalDim() = assertSingleModalDim(SurfaceEffectMode.Frosted)

    @Test fun glassBackButtonReceivesExactlyOneModalDim() = assertSingleModalDim(SurfaceEffectMode.Glass)

    @Test fun solidBackButtonReceivesExactlyOneModalDim() = assertSingleModalDim(SurfaceEffectMode.Solid)

    private fun assertSingleModalDim(mode: SurfaceEffectMode) {
        var modal by mutableStateOf(false)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(
                    LocalHazePreferences provides SurfaceEffectPreferences(mode = mode),
                    LocalHazeGlassEnabled provides (mode == SurfaceEffectMode.Glass),
                ) {
                    HazeHost {
                        Box(Modifier.size(100.dp).background(Color.White).testTag("scene")) {
                            if (modal) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
                            TranslucentBackButton(onClick = {}, modalScrimAlpha = if (modal) 0.32f else 0f)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("scene").captureToImage()
        compose.runOnIdle { modal = true }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("scene").captureToImage()
        val x = (before.width * 0.12f).toInt()
        val y = (before.height * 0.24f).toInt()
        val original = before.toPixelMap()[x, y]
        val dimmed = after.toPixelMap()[x, y]
        assertEquals(original.red * 0.68f, dimmed.red, 0.015f)
        assertEquals(original.green * 0.68f, dimmed.green, 0.015f)
        assertEquals(original.blue * 0.68f, dimmed.blue, 0.015f)
    }

    private fun activityWindowCenterRed(window: android.view.Window = compose.activity.window): Int =
        android.graphics.Color.red(windowPixel(window))

    private fun windowPixel(window: android.view.Window, x: Float = .5f, y: Float = .5f): Int {
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = -1
        PixelCopy.request(window, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val pixel = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
        bitmap.recycle()
        return pixel
    }
}
