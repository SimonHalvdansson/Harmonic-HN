package com.simon.harmonichackernews.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.web_preview
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.widget.WidgetPreviewStoryRow
import com.simon.harmonichackernews.ui.widget.LocalWidgetTextStyle
import com.simon.harmonichackernews.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetPreviewParityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mediumRowsMatchWithAndWithoutImagesIncludingOneLineTitles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = BitmapFactory.decodeResource(context.resources, R.drawable.widget_preview_equations)
        val configuration = WidgetConfiguration(previewImageMode = StoryPreviewMode.MEDIUM)
        val story = Story("Patterns", 1, true, false).apply {
            score = 291; descendants = 108; isLink = true; url = "https://science.org"
            time = (System.currentTimeMillis() / 1000 - 7200).toInt()
        }
        val imagePresent = mutableStateOf(true)
        val views = listOf(true, false).associateWith { hasImage ->
            object : GlanceAppWidget() {
                override suspend fun provideGlance(context: Context, id: GlanceId) {
                    provideContent {
                        WidgetStoryRow(context, WidgetEntry(story.toDestination(true), null, null, null), 0,
                            configuration, WidgetColors(HarmonicThemeCatalog.resolve("light", false).colors),
                            WidgetVisual(image.takeIf { hasImage }, null, null))
                    }
                }
            }.compose(context, size = DpSize(360.dp, 160.dp))
        }
        var nativeRoot: View? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalWidgetTextStyle provides TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))) {
                    Column(Modifier.width(360.dp).background(palette.colors.settingsPageBackground)) {
                        Text("Configuration preview")
                        WidgetPreviewStoryRow(SettingsStoryPreviewModel.copy(index = "1.", title = "Patterns", points = 291,
                            commentCount = 108, faviconFallback = Res.drawable.ic_public, tintFaviconFallback = true,
                            previewImageFallback = Res.drawable.web_preview.takeIf { imagePresent.value }), configuration)
                        Text("Actual Glance row")
                        AndroidView(factory = { object : FrameLayout(it) {
                            // A launcher collection measures each row without a fixed height.
                            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            }
                        } }, modifier = Modifier.fillMaxWidth(), update = { host ->
                            host.removeAllViews()
                            nativeRoot = views.getValue(imagePresent.value).apply(host.context, host)
                            host.addView(nativeRoot)
                        })
                    }
                }
            }
        }
        for (hasImage in listOf(true, false)) {
            compose.runOnUiThread { imagePresent.value = hasImage }
            compose.waitForIdle()
            if (hasImage) {
                val bounds = compose.onNodeWithTag("widget-preview-image").fetchSemanticsNode().boundsInRoot
                assertEquals(72f * context.resources.displayMetrics.density, bounds.height, 1f)
            }
            val points = compose.onNodeWithText("291").fetchSemanticsNode().boundsInRoot
            val comments = compose.onNodeWithText("108").fetchSemanticsNode().boundsInRoot
            val previewTitle = compose.onNodeWithText("Patterns").fetchSemanticsNode().boundsInRoot
            if (hasImage) assertEquals(points.center.y, comments.center.y, 1f)
            else {
                assertTrue("No-image pills should be stacked", points.bottom < comments.top)
                assertEquals("Stacked pills should share their trailing edge", points.right, comments.right, 1f)
            }
            if (InstrumentationRegistry.getArguments().getString("captureWidgetParity") == "true") {
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.externalCacheDir, "widget-parity-$hasImage.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.runOnIdle {
                val labels = descendants(requireNotNull(nativeRoot)).filterIsInstance<TextView>()
                val nativePoints = labels.single { it.text.toString() == "291" }
                val nativeComments = labels.single { it.text.toString() == "108" }
                fun position(view: View): IntArray = IntArray(2).also(view::getLocationOnScreen)
                if (hasImage) assertEquals(position(nativePoints)[1], position(nativeComments)[1])
                else {
                    assertTrue("Native no-image pills should be stacked",
                        position(nativePoints)[1] + nativePoints.height < position(nativeComments)[1])
                    assertEquals(position(nativePoints)[0] + nativePoints.width,
                        position(nativeComments)[0] + nativeComments.width)
                }
                val nativeTitle = labels.single { it.text.toString() == "Patterns" }
                val nativePosition = position(nativeTitle)
                assertEquals("Title x should match", previewTitle.left, nativePosition[0].toFloat(), 2f)
                val countPosition = position(nativePoints)
                assertEquals("Pill x should match", points.left, countPosition[0].toFloat(), 2f)
                assertEquals("Count font metrics should match", points.height, nativePoints.height.toFloat(), 1f)
                assertEquals("Title font metrics should match", previewTitle.height, nativeTitle.height.toFloat(), 1f)
                // Glance truncates dp insets while Compose rounds them; nested 4/6/12dp insets
                // can differ by three physical pixels at the emulator's fractional density.
                assertEquals("Count placement relative to title should match", points.top - previewTitle.top,
                    (countPosition[1] - nativePosition[1]).toFloat(), 1.5f * context.resources.displayMetrics.density)
                if (hasImage) {
                    val images = descendants(requireNotNull(nativeRoot)).filterIsInstance<android.widget.ImageView>()
                    val thumbnail = images.single { it.width == (120 * context.resources.displayMetrics.density).toInt() }
                    assertEquals("One-line title must not crop medium image", (72 * context.resources.displayMetrics.density).toInt(), thumbnail.height)
                    var parent = thumbnail.parent as? View
                    while (parent != null && parent != nativeRoot) {
                        assertTrue("Image must fit its ancestors", parent.height >= thumbnail.height)
                        parent = parent.parent as? View
                    }
                }
            }
        }
    }

    @Test
    fun pickerIndicesStartAlongsideTheTitle() {
        compose.runOnUiThread {
            val context = compose.activity
            val picker = android.view.LayoutInflater.from(context).inflate(R.layout.widget_stories_preview, FrameLayout(context), false)
            picker.measure(View.MeasureSpec.makeMeasureSpec((360 * context.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
            val labels = descendants(picker).filterIsInstance<TextView>()
            val index = labels.single { it.text.toString() == "1." }
            val title = labels.single { it.text.toString().startsWith("Algorithm breaks") }
            fun top(view: View): Int = view.top + ((view.parent as? View)?.takeUnless { it == picker }?.let(::top) ?: 0)
            assertEquals(top(title), top(index))
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
