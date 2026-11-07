package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.categorization.NativeEmbeddingProgress
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.platform.NativeEmbeddingModel
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.*
import com.simon.harmonichackernews.ui.theme.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

class ArticleEmbeddingProviderSettingsTest {
    private data class Preview(
        val name: String, val native: NativeEmbeddingProgress,
        val preferSystem: Boolean = true, val downloaded: Boolean = false,
        val enabled: Boolean = true, val fallbackAvailable: Boolean = true,
    )

    @Test fun providerChoiceResumeAndCompactTextRenderCorrectly() = SwingUtilities.invokeAndWait {
        val android = NativeEmbeddingModel("mock-android", "Android AICore", 768, 256, modelName = "EmbeddingGemma 2")
        val apple = NativeEmbeddingModel("mock-apple", "Apple Natural Language", 512)
        val ready = NativeEmbeddingProgress(checked = true, model = android, completed = 1199, total = 1199)
        val unavailable = NativeEmbeddingProgress(checked = true)
        val scenarios = listOf(
            Preview("system-ready", ready),
            Preview("system-preparing", ready.copy(completed = 420, preparing = true)),
            Preview("system-paused", ready.copy(completed = 72, paused = true)),
            Preview("gemma-selected", ready, preferSystem = false, downloaded = true),
            Preview("gemma-download", ready, preferSystem = false, enabled = false),
            Preview("no-system", unavailable, enabled = false),
            Preview("foss-unavailable", unavailable, enabled = false, fallbackAvailable = false),
            Preview("system-off", ready.copy(completed = 0), enabled = false),
            Preview("apple-ready", ready.copy(model = apple)),
            Preview("apple-preparing-dark", ready.copy(model = apple, completed = 870, preparing = true)),
            Preview("macos-wide", ready.copy(model = apple)),
        )
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("ArticleEmbeddingProviderSettingsTest")
        try {
            scenarios.forEach { preview ->
                val dark = preview.name.endsWith("dark")
                var native by mutableStateOf(preview.native)
                var preferSystem by mutableStateOf(preview.preferSystem)
                var selected by mutableStateOf(512)
                var resumeCalls = 0
                val scene = ImageComposeScene(if (preview.name.endsWith("wide")) 900 else 420, 1080) {
                    val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
                    HarmonicTheme(palette.colorScheme, dark) {
                        CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                            SettingsPage(title = "Article categories", showNavigation = true, onBack = {}) {
                                item { SettingsMainToggle(title = "Categorize stories", checked = preview.enabled,
                                    enabled = (preferSystem && native.model != null) || preview.downloaded,
                                    summary = if (!preview.fallbackAvailable) "Unavailable in this build" else if (!preview.enabled && native.model == null || !preferSystem && !preview.downloaded) "Download embedding model" else null,
                                    onCheckedChange = {}) }
                                item { ArticleCategoryIntroduction({}) }
                                item { ArticleEmbeddingProviderSettings(native, preferSystem, preview.fallbackAvailable,
                                    onPreferSystem = { preferSystem = it }, onResume = { resumeCalls++; native = native.copy(preparing = true, paused = false) }) {
                                    EmbeddingGemmaModelDetails("LiteRT-LM · 165 MB") {
                                        IconButton(onClick = {}, enabled = preview.fallbackAvailable) {
                                            Icon(painterResource(if (preview.downloaded) Res.drawable.ic_delete else Res.drawable.ic_file_download),
                                                if (preview.downloaded) "Remove embedding model" else "Download embedding model")
                                        }
                                    }
                                } }
                                item { SettingsCategory("Configuration") {
                                    ArticleEmbeddingTokenSetting(native, preferSystem, selected) { selected = it }
                                    SettingsDivider()
                                    CategorySimilaritySetting(value = 0.24f, onValueChange = {}, onReset = {}, resetEnabled = false)
                                    SettingsDivider()
                                    SegmentedSetting(title = "Maximum labels per story", options = listOf(1 to "1", 2 to "2", 3 to "3"), selected = 2, onSelected = {})
                                } }
                                settingsCategory("Categories") {
                                    ArticleCategories.defaults.forEach { category -> row(category.id) {
                                        NavigationSwitchSettingRow(title = category.name, summary = "36 examples",
                                            icon = com.simon.harmonichackernews.ui.content.categorySymbol(category.id),
                                            onClick = {}, checked = true, onCheckedChange = {})
                                    } }
                                }
                            }
                        }
                    }
                }
                try {
                    var time = 0L
                    fun frames() = repeat(30) { scene.render(time.also { time += 16_666_667L }).close() }
                    fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
                    fun nodes() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                    fun hasText(n: SemanticsNode, text: String) = walk(n).any { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
                    fun option(text: String) = nodes().single { it.config.getOrNull(SemanticsProperties.Selected) != null && hasText(it, text) }
                    fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    fun checkLineHeight(text: String, expected: Int) {
                        val node = nodes().single { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                        assertEquals(expected.sp, layouts.single().layoutInput.style.lineHeight)
                    }
                    frames()
                    val usingNative = native.model != null && preferSystem
                    assertEquals(usingNative, option("128").config.contains(SemanticsProperties.Disabled), preview.name)
                    assertEquals(usingNative && native.incomplete, texts().any { it == "${native.completed} / ${native.total}" }, preview.name)
                    assertFalse(texts().any { it.contains("aren't available") || it.contains("Used automatically") })
                    assertEquals(!usingNative, "Persistent background" in texts(), preview.name)
                    assertEquals(!usingNative, "191 MB RAM" in texts(), preview.name)
                    if (usingNative) {
                        checkLineHeight(native.model!!.name, 20)
                        // The model summary is distinct from the provider selector's label.
                        native.model!!.modelName?.let { name ->
                            val summaries = nodes().filter { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == name } == true }
                            assertEquals(2, summaries.size)
                            val layouts = summaries.flatMap { node ->
                                mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(it) }
                            }
                            assertTrue(layouts.any { it.layoutInput.style.fontSize == 14.sp && it.layoutInput.style.lineHeight == 18.sp })
                        }
                        checkLineHeight(native.model!!.tokenLimit?.let { "System limit: $it tokens" } ?: "Managed by the system", 18)
                        if (native.incomplete) checkLineHeight("${native.completed} / ${native.total}", 18)
                    }
                    System.getenv("HARMONIC_EMBEDDINGS_SCREENSHOTS")?.let { directory ->
                        File(directory).mkdirs()
                        scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(directory, "updated-${preview.name}.png").writeBytes(data.bytes)
                        } }
                    }
                    if (usingNative && native.incomplete && !native.preparing) {
                        val label = if (native.completed == 0 && !native.paused) "Prepare" else "Resume"
                        checkLineHeight(label, 18)
                        nodes().single { it.config.contains(SemanticsActions.OnClick) && hasText(it, label) }
                            .config[SemanticsActions.OnClick].action!!.invoke()
                        frames()
                        assertEquals(1, resumeCalls)
                        assertFalse(label in texts())
                        assertTrue("Preparing examples" in texts())
                        native = native.copy(completed = native.total, preparing = false)
                        frames()
                        assertFalse(texts().any { it == "${native.completed} / ${native.total}" })
                    }
                    if (native.model != null) {
                        option("EmbeddingGemma 2").config[SemanticsActions.OnClick].action!!.invoke()
                        frames()
                        assertFalse(preferSystem)
                        assertFalse(option("128").config.contains(SemanticsProperties.Disabled))
                        assertTrue(option("512").config[SemanticsProperties.Selected])
                        option("128").config[SemanticsActions.OnClick].action!!.invoke()
                        frames()
                        assertEquals(128, selected)
                        option("System").config[SemanticsActions.OnClick].action!!.invoke()
                        frames()
                        assertTrue(preferSystem)
                        assertTrue(option("128").config.contains(SemanticsProperties.Disabled))
                        assertEquals(128, selected, "System selection must preserve the Gemma preference")
                    } else {
                        assertFalse("System" in texts())
                    }
                } finally { scene.close() }
            }
        } finally { bootstrap.close() }
    }
}
