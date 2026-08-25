package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.AppFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebContentPagePolicyTest {
    private val urls = WebContentPlatformUrls(
        pdfViewer = "app://pdf/index.html",
        errorPage = "app://webview_error.html",
    )

    @Test
    fun classifiesHostUrlsAndReaderEligibility() {
        assertEquals(WebContentPageKind.EMPTY, WebContentPagePolicy.classify(null, urls))
        assertEquals(
            WebContentPageKind.PDF_VIEWER,
            WebContentPagePolicy.classify(urls.pdfViewer, urls),
        )
        assertEquals(
            WebContentPageKind.ERROR_PAGE,
            WebContentPagePolicy.classify("${urls.errorPage}#offline", urls),
        )
        assertEquals(
            WebContentPageKind.CONTENT,
            WebContentPagePolicy.classify("https://example.com/article", urls),
        )
        assertTrue(WebContentPagePolicy.isReaderEligible("https://example.com/article", urls))
        assertFalse(WebContentPagePolicy.isReaderEligible(urls.pdfViewer, urls))
    }

    @Test
    fun pdfRoutesRequireAndPreserveAFileReference() {
        assertNull(WebContentPagePolicy.route(urls.pdfViewer, null, null, urls))
        assertEquals(
            WebContentRoute(
                url = urls.pdfViewer,
                kind = WebContentPageKind.PDF_VIEWER,
                pdfReference = "/cache/article.pdf",
            ),
            WebContentPagePolicy.route(
                urls.pdfViewer,
                requestedPdfReference = null,
                currentPdfReference = "/cache/article.pdf",
                platformUrls = urls,
            ),
        )
        assertNull(
            WebContentPagePolicy.route(
                "   ",
                requestedPdfReference = null,
                currentPdfReference = null,
                platformUrls = urls,
            ),
        )
    }

    @Test
    fun errorAndCachedArticleUrlsHaveOneFallbackPolicy() {
        assertEquals(
            "${urls.errorPage}#dns",
            WebContentPagePolicy.errorPageUrl(WebContentFailure.DNS, urls),
        )
        assertEquals(
            "https://stored.example/article",
            WebContentPagePolicy.cachedArticleBaseUrl(
                "https://stored.example/article",
                "https://failed.example/article",
                "https://story.example/article",
            ),
        )
        assertEquals(
            "https://story.example/article",
            WebContentPagePolicy.cachedArticleBaseUrl(null, "", "https://story.example/article"),
        )
    }

    @Test
    fun readerFontPairsArePortableResourceKeys() {
        assertEquals(
            ReaderModeFontResources(
                ReaderModeFontResource.GEORGIA_REGULAR,
                ReaderModeFontResource.GEORGIA_BOLD,
            ),
            ReaderModeFontResourcePolicy.resolve(AppFont.GEORGIA.storedValue),
        )
        assertEquals(
            ReaderModeFontResources(
                ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
                ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
            ),
            ReaderModeFontResourcePolicy.resolve(AppFont.GOOGLE_SANS_CODE.storedValue),
        )
        assertNull(ReaderModeFontResourcePolicy.resolve(AppFont.DEVICE_DEFAULT.storedValue))
    }
}
