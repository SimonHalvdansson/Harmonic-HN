package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacingSettingsPageTest {
    @Test fun pagesShowThreeSamplesAndControlsSaveTheirOwnPreferences() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SpacingSettingsPageTest")
        try {
            for (stories in listOf(true, false)) {
                val repository = bootstrap.app.settings
                val scene = ImageComposeScene(420, 1000) {
                    HarmonicTheme(HarmonicThemeCatalog.scheme("dynamic", false).colorScheme, false) {
                        CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                            if (stories) StoriesSpacingSettingsRoute(repository, bootstrap.app.articleCategorySettings, {})
                            else CommentsSpacingSettingsRoute(repository, {})
                        }
                    }
                }
                try {
                    var time = 0L
                    fun settle() = repeat(40) { scene.render(time.also { time += 16_666_667L }).close() }
                    fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
                    fun nodes() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                    settle()
                    val texts = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    val samples = if (stories) listOf(
                        "Algorithm breaks speed limit for solving linear equations",
                        "Show HN: A small tool for exploring the night sky",
                        "The quiet craft of building useful software",
                    ) else listOf("willow", "lantern", "pebble")
                    samples.forEach { sample -> assertTrue(texts.any { sample in it }, "Missing sample: $sample") }
                    assertTrue(texts.none { "Default" in it })
                    assertTrue("Dividers" in texts)
                    System.getenv("HARMONIC_SPACING_PREVIEWS")?.let { folder ->
                        scene.render(time).use { image ->
                            image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                                File(folder, if (stories) "stories-spacing.png" else "comments-spacing.png").writeBytes(data.bytes)
                            }
                        }
                    }
                    nodes().single { it.config.getOrNull(SemanticsActions.SetProgress) != null }
                        .config[SemanticsActions.SetProgress].action!!.invoke(25f)
                    assertEquals(25, if (stories) repository.snapshot().story.spacing else repository.snapshot().comments.spacing)
                    nodes().single { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertTrue(if (stories) repository.snapshot().story.showDividers else repository.snapshot().comments.showDividers)
                } finally { scene.close() }
            }
        } finally { bootstrap.close() }
    }
}
