package com.simon.harmonichackernews

import android.content.Context
import android.os.Build
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Path
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.text.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.network.WidgetConfigurationService
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetFontTest {
    @Test
    fun missingFamiliesAndAliasesDoNotOfferDuplicateFonts() {
        assertNull(distinctWidgetFontFamily(null))
        assertNull(distinctWidgetFontFamily("not-an-installed-widget-font"))
        assertNull(distinctWidgetFontFamily("sans-serif-medium"))
        assertNull(distinctWidgetFontFamily("arial"))
        assertEquals("serif", distinctWidgetFontFamily("serif"))
    }

    @Test
    fun prefersVerifiedRoundedFlexAndFallsBackToPublicDeviceTextAppearance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val style = if (Build.VERSION.SDK_INT >= 33) android.R.style.TextAppearance_DeviceDefault_Headline
            else android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title
        val attributes = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.fontFamily))
        val configured = try { attributes.getString(0) } finally { attributes.recycle() }
        val resolved = widgetHeadlineFontFamily(context)
        android.util.Log.i("WidgetFontTest", "Device headline=$configured; distinct widget family=$resolved")
        if (Build.VERSION.SDK_INT >= 34) {
            android.util.Log.i("WidgetFontTest", "Installed family=${Typeface.create(resolved ?: configured, Typeface.NORMAL).systemFontFamilyName}")
            if (resolved != null) assertEquals(resolved, Typeface.create(resolved, Typeface.NORMAL).systemFontFamilyName)
        }
        val rounded = if (isRoundedGoogleSansFlex(ROUNDED_WIDGET_FONT_FAMILY)) ROUNDED_WIDGET_FONT_FAMILY else null
        assertEquals(rounded ?: distinctWidgetFontFamily("google-sans-flex") ?: distinctWidgetFontFamily(configured), resolved)
        assertFalse(isRoundedGoogleSansFlex("sans-serif"))
        assertFalse(isRoundedGoogleSansFlex("google-sans-flex"))
        if (rounded != null) {
            assertEquals("Google Sans Flex Rounded", widgetFontDisplayName(resolved!!))
            android.util.Log.i("WidgetFontTest", "Verified GoogleSansFlex with ROND=100 for regular and bold")
        }
    }

    @Test
    fun headlineIsDefaultAndSelectionIsStoredPerWidget() {
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int): Story? = null
            override suspend fun getComment(id: Int): Comment? = null
            override suspend fun getStoryIds(type: StoryType) = emptyList<Int>()
        }
        val widgets = WidgetConfigurationService(InMemoryKeyValueStore(), InMemoryKeyValueStore(), repository)
        assertTrue(widgets.configuration(1).useHeadlineFont)
        widgets.save(1, WidgetConfiguration(useHeadlineFont = false))
        assertFalse(widgets.configuration(1).useHeadlineFont)
        assertTrue(widgets.configuration(2).useHeadlineFont)
        widgets.clear(1)
        assertTrue(widgets.configuration(1).useHeadlineFont)
    }

    @Test
    fun previewResolvesTheSameRegularAndBoldWeightsAsTheLauncher() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val family = widgetHeadlineFontFamily(context) ?: "serif"
        instrumentation.runOnMainSync {
            val resolver = createFontFamilyResolver(context)
            listOf(FontWeight.Normal, FontWeight.Bold).forEach { weight ->
                val actual = resolver.resolve(widgetPreviewFontFamily(family), fontWeight = weight).value as Typeface
                val expected = Typeface.create(family, if (weight == FontWeight.Bold) Typeface.BOLD else Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= 28) assertEquals(weight.weight, actual.weight)
                if (Build.VERSION.SDK_INT >= 34) assertEquals(expected.systemFontFamilyName, actual.systemFontFamilyName)
                fun outlines(face: Typeface): FloatArray {
                    val paint = Paint().apply { typeface = face; textSize = 32f }
                    val path = Path()
                    val sample = "Top Stories 123 AaGgQq"
                    paint.getTextPath(sample, 0, sample.length, 0f, 0f, path)
                    return path.approximate(0.01f)
                }
                // Independently created Typeface objects can have different native identities.
                assertArrayEquals(outlines(expected), outlines(actual), 0.01f)
            }
        }
    }

    @Test
    fun installedNamedFamilySurvivesGlanceRemoteViewsForEveryStoryLabel() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Use the real headline font where available and an installed alternative on AOSP.
        val family = widgetHeadlineFontFamily(context) ?: "serif"
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) {
                provideContent {
                    WidgetStoryRow(
                        context, WidgetEntry(Story("Named font story", 71, true, false).toDestination(false), null, null, null),
                        0, WidgetConfiguration(), WidgetColors(HarmonicThemeCatalog.resolve("light", false).colors),
                        null, fontFamily = FontFamily(family),
                    )
                }
            }
        }
        val views = widget.compose(context, size = DpSize(360.dp, 120.dp))
        instrumentation.runOnMainSync {
            fun labels(view: View): List<TextView> = when (view) {
                is TextView -> listOf(view)
                is ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
                else -> emptyList()
            }
            val texts = labels(views.apply(context, FrameLayout(context))).filter { it.text.isNotEmpty() }
            assertTrue(texts.any { it.text.toString() == "Named font story" })
            texts.forEach { view ->
                val text = view.text as Spanned
                assertEquals(view.text.toString(), family, text.getSpans(0, text.length, TypefaceSpan::class.java).single().family)
            }
        }
    }
}
