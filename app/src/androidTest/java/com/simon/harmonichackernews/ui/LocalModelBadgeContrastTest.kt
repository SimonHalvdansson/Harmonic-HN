package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.model_logo_google
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelPresentation
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import com.simon.harmonichackernews.summary.formatDecimalBytes
import com.simon.harmonichackernews.ui.settings.LocalModelRowUiState
import com.simon.harmonichackernews.ui.settings.LocalModelsPanel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.jetbrains.compose.resources.painterResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real model cards rendered from presentation fixtures; no inference runtime or download required. */
@RunWith(AndroidJUnit4::class)
class LocalModelBadgeContrastTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedDownloadableSizeBadgeRemainsDistinctAndReadable() = assertSelectedBadge(
        selectedId = LocalModelCatalog.MODEL_E2B,
        screenshotName = "downloadable",
    )

    @Test
    fun selectedGeminiNanoBaseModelBadgeRemainsDistinctAndReadable() = assertSelectedBadge(
        selectedId = LocalModelCatalog.MODEL_GEMINI_NANO,
        screenshotName = "nano",
    )

    private fun assertSelectedBadge(selectedId: String, screenshotName: String) {
        val models = LocalModelCatalog.models.filter {
            it.id == LocalModelCatalog.MODEL_GEMINI_NANO || it.id == LocalModelCatalog.MODEL_E2B
        }
        val selectedModel = models.single { it.id == selectedId }
        val badgeText = if (selectedModel.downloadable) formatDecimalBytes(selectedModel.sizeBytes) else "nano-v4-full"
        val theme = mutableStateOf("light")
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve(if (theme.value == "dynamic_light") "material_light" else theme.value, false)
            val colorScheme = if (theme.value == "dynamic_light" && Build.VERSION.SDK_INT >= 31) {
                dynamicLightColorScheme(compose.activity)
            } else {
                palette.colorScheme
            }
            HarmonicTheme(palette.colors, colorScheme, palette.dark) {
                Column(
                    Modifier.fillMaxWidth().background(palette.colors.settingsItemBackground)
                        .testTag("local-model-fixture").padding(vertical = 24.dp),
                ) {
                    Text("Local model badge contrast", Modifier.padding(horizontal = 24.dp))
                    Text(
                        "UI fixture: selected ${selectedModel.displayName} · ${theme.value}",
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    LocalModelsPanel(
                        models = models.map { model ->
                            LocalModelRowUiState(
                                model = model,
                                presentation = LocalModelPresentation(
                                    summary = if (model.downloadable) "Model\nDownloaded" else "Available · system managed",
                                    enabled = true, selectable = true, selected = model.id == selectedId,
                                    action = LocalModelPresentationAction.DELETE_MODEL.takeIf { model.downloadable },
                                    progress = null,
                                ),
                                baseModelName = "nano-v4-full".takeUnless { model.downloadable },
                            )
                        },
                        modelIconPainter = { painterResource(Res.drawable.model_logo_google) },
                        onModelSelected = {},
                        onAction = { _, _ -> },
                    )
                }
            }
        }
        val themes = listOf("light", "material_light") + if (Build.VERSION.SDK_INT >= 31) listOf("dynamic_light") else emptyList()
        for (themeName in themes) {
            compose.runOnIdle { theme.value = themeName }
            compose.waitForIdle()
            val badge = compose.onNodeWithText(badgeText, useUnmergedTree = true)
            val badgePixels = badge.captureToImage().toPixelMap()
            val cardPixels = compose.onNode(
                hasClickAction() and hasAnyDescendant(hasText(selectedModel.displayName)),
                useUnmergedTree = true,
            ).captureToImage().toPixelMap()
            fun dominantColor(pixels: androidx.compose.ui.graphics.PixelMap): Color {
                val counts = mutableMapOf<Int, Int>()
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y].toArgb()
                    counts[color] = (counts[color] ?: 0) + 1
                }
                return Color(counts.maxBy { it.value }.key)
            }
            val background = dominantColor(badgePixels)
            val cardBackground = dominantColor(cardPixels)
            val textLayouts = mutableListOf<TextLayoutResult>()
            badge.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            val foreground = textLayouts.single().layoutInput.style.color
            // These informational badges use soft container colors; the text needs contrast,
            // while the badge fill only needs to remain distinct from the selected card.
            assertTrue("Selected card and $badgeText badge must differ in $themeName", background != cardBackground)
            assertTrue("Small badge label must retain 4.5:1 contrast in $themeName", contrast(background, foreground) >= 4.5f)
            assertTrue(
                "The badge text must actually render with its contrasting foreground",
                (0 until badgePixels.height).any { y ->
                    (0 until badgePixels.width).any { x ->
                        val pixel = badgePixels[x, y]
                        kotlin.math.abs(pixel.red - foreground.red) < 0.05f &&
                            kotlin.math.abs(pixel.green - foreground.green) < 0.05f &&
                            kotlin.math.abs(pixel.blue - foreground.blue) < 0.05f
                    }
                },
            )
            val screenshot = compose.onNodeWithTag("local-model-fixture").captureToImage().asAndroidBitmap()
            val output = File(compose.activity.filesDir, "local-model-badge-$screenshotName-$themeName.png")
            output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
