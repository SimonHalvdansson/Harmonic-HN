package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.settings.UserAvatarStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HarmonicUiMemoryCachesTest {
    @Test
    fun trimmingReleasesCommentCachesAndRebuildsEquivalentTextReferencesAndQuotes() = runTest {
        trimHarmonicUiMemoryCaches()
        val html = "&gt; A <b>quoted</b> paragraph<p>Reply with " +
            "<a href='https://example.com/article'>an inline link</a>.<p>" +
            "<a href='https://example.org/reference'>Reference</a>"
        val model = CommentRenderModelCache.get(42, html, collectLinks = true)
        val text = CommentHtmlTextCache.getPrepared(html)
        val plain = text.plain
        val highlighted = text.highlighted
        val decorated = text.decoratedPlain
        val linkUrls = model.references?.links?.map { it.url }
        assertTrue(text.hasQuotes)
        assertEquals(1, CommentRenderModelCache.entryCountForTest())
        assertEquals(1, CommentHtmlTextCache.entryCountForTest())

        trimHarmonicUiMemoryCaches()
        trimHarmonicUiMemoryCaches()
        assertEquals(0, CommentRenderModelCache.entryCountForTest())
        assertEquals(0, CommentHtmlTextCache.entryCountForTest())
        assertNull(CommentRenderModelCache.peek(42, html, collectLinks = true))
        assertFalse(CommentHtmlTextCache.contains(html))

        // A visible row can retain these immutable values while the shared cache releases them.
        assertEquals(plain, text.text(highlightQuotes = false))
        assertEquals(highlighted, text.text(highlightQuotes = true))
        assertEquals(decorated, text.text(highlightQuotes = false, retainQuoteDecoration = true))
        val reloadedModel = CommentRenderModelCache.get(42, html, collectLinks = true)
        val reloadedText = CommentHtmlTextCache.getPrepared(html)
        assertNotSame(model, reloadedModel)
        assertNotSame(text, reloadedText)
        assertEquals(model.contentBlocks.map { it.bodyHtml }, reloadedModel.contentBlocks.map { it.bodyHtml })
        assertEquals(linkUrls, reloadedModel.references?.links?.map { it.url })
        assertEquals(plain, reloadedText.plain)
        assertEquals(highlighted, reloadedText.highlighted)
        assertEquals(decorated, reloadedText.decoratedPlain)
        for (quoteMode in listOf(false, true)) {
            val bound = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener {}, quoteMode)
            val link = bound.getLinkAnnotations(0, bound.length).first()
            assertEquals("an inline link", bound.text.substring(link.start, link.end))
        }
        assertSame(reloadedModel, CommentRenderModelCache.get(42, html, collectLinks = true))
        assertSame(reloadedText, CommentHtmlTextCache.getPrepared(html))
        trimHarmonicUiMemoryCaches()
    }

    @Test
    fun trimmingDropsCollapsedPreviewsWithoutInvalidatingAlreadyDisplayedText() = runTest {
        trimHarmonicUiMemoryCaches()
        var parses = 0
        val html = "<p>A <b>collapsed</b> comment with useful text.</p>"
        fun preview() = collapsedCommentPreview(43, html, needed = true) {
            parses++
            Ksoup.parse(it).text()
        }
        val displayed = preview()
        assertEquals(displayed, preview())
        assertEquals(1, parses)
        trimHarmonicUiMemoryCaches()
        assertEquals("A collapsed comment with useful text.", displayed)
        assertEquals(displayed, preview())
        assertEquals(2, parses)
        trimHarmonicUiMemoryCaches()
    }

    @Test
    fun resourceTrimWaitsForTheCurrentDecodeAndTheNextVisitCanReload() = runTest {
        val cache = ResourcePreviewCache<String, List<String>>(5)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val first = async {
            cache.getOrLoad("sample") {
                loads++
                started.complete(Unit)
                release.await()
                listOf("immutable decoded image")
            }
        }
        started.await()
        val trim = async { cache.clearCompleted() }
        yield()
        assertFalse(trim.isCompleted)
        release.complete(Unit)
        val displayed = first.await()
        trim.await()
        assertNull(cache["sample"])
        assertEquals(listOf("immutable decoded image"), displayed)
        val reloaded = cache.getOrLoad("sample") { loads++; listOf("immutable decoded image") }
        assertEquals(displayed, reloaded)
        assertNotSame(displayed, reloaded)
        assertSame(reloaded, cache.getOrLoad("sample") { error("Completed reload must remain cached") })
        assertEquals(2, loads)
        cache.clearCompleted()
        cache.clearCompleted()
        assertNull(cache["sample"])
    }

    @Test
    fun avatarTrimKeepsExistingArtworkValidAndRebuildsItOnlyWhenRequested() {
        var creations = 0
        val cache = AvatarArtworkCache { author: String, style: UserAvatarStyle ->
            creations++
            StringBuilder("$author/$style")
        }
        val displayed = cache.get("reader", UserAvatarStyle.MOSAIC)
        cache.clear()
        cache.clear()
        assertEquals(1, creations)
        val reloaded = cache.get("reader", UserAvatarStyle.MOSAIC)
        assertEquals(displayed.toString(), reloaded.toString())
        assertNotSame(displayed, reloaded)
        assertSame(reloaded, cache.get("reader", UserAvatarStyle.MOSAIC))
        assertEquals(2, creations)
    }
}
