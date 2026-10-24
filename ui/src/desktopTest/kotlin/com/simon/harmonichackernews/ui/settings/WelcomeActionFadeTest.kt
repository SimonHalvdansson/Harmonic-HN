package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class WelcomeActionFadeTest {
    @Test
    fun firstFrameIsHiddenAndTheWholeShadowFadesWithTheButton() = SwingUtilities.invokeAndWait {
        val time = mutableStateOf(0.0)
        val scene = ImageComposeScene(260, 160, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground),
                    contentAlignment = Alignment.Center) {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        modifier = Modifier.size(180.dp, 56.dp).welcomeEntrance(time, 3),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text("Get started") }
                }
            }
        }
        try {
            fun samples(frame: Long) = scene.render(frame).use {
                val pixels = it.toComposeImageBitmap().toPixelMap()
                // The 56dp button ends at y=108. Sample the shadow outside its layout bounds.
                listOf(pixels[130, 80], pixels[130, 111], pixels[0, 0])
            }
            fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
            val initial = samples(0L)
            assertTrue(distance(initial[0], initial[2]) < .001f, "The first frame must not flash the button")
            time.value = 840.0 // Halfway through the action's fade.
            val halfway = samples(16_666_667L)
            time.value = 1000.0
            val complete = samples(33_333_334L)
            val halfShadow = distance(halfway[1], halfway[2])
            val fullShadow = distance(complete[1], complete[2])
            assertTrue(halfShadow > .001f, "The shadow below the button must already be visible mid-fade")
            assertTrue(halfShadow < fullShadow, "The shadow must fade with the button")
        } finally {
            scene.close()
        }
    }
}
