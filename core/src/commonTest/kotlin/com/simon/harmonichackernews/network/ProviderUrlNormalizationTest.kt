package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProviderUrlNormalizationTest {
    @Test
    fun githubClassifierAndLoaderUseTheSameRepositoryIdentity() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.github.com", request.url.host)
            assertEquals("/repos/torvalds/linux", request.url.encodedPath)
            respond("""{"name":"linux","owner":{"login":"torvalds"}}""")
        })
        try {
            for (url in listOf(
                "https://github.com/torvalds/linux#readme",
                "https://github.com/torvalds/linux?tab=readme-ov-file",
                "https://www.github.com/torvalds/linux",
                "https://github.com/torvalds/linux.git",
            )) {
                assertEquals(LinkPreviewType.GITHUB_REPOSITORY, RichLinkPreviewUrls.type(url))
                assertEquals("linux", KtorLinkPreviewRepository(client).getGitHubInfo(url).name)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun wikipediaTitleIsDecodedOnceAndExcludesQueryAndFragment() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Siméon_Denis_Poisson", request.url.parameters["titles"])
            respond("""{"query":{"pages":{"1":{"title":"Siméon Denis Poisson","extract":"<p>Article.</p>"}}}}""")
        })
        try {
            KtorLinkPreviewRepository(client).getWikipediaInfo(
                "https://en.wikipedia.org/wiki/Sim%C3%A9on_Denis_Poisson?oldformat=true#History",
            )
            assertEquals("C++/History", LinkPreviewUrls.wikipediaTitle("https://en.wikipedia.org/wiki/C%2B%2B/History"))
            assertNull(LinkPreviewUrls.wikipediaTitle("https://en.wikipedia.org/wiki/"))
        } finally {
            client.close()
        }
    }

    @Test
    fun npmVersionPathsDoNotBecomePartOfThePackageName() {
        assertEquals("react", PackageLinkPreview.packageTarget("https://www.npmjs.com/package/react/v/19.0.0")?.name)
        assertEquals("@types/react", PackageLinkPreview.packageTarget("https://www.npmjs.com/package/@types/react/v/19.0.0")?.name)
        assertNull(PackageLinkPreview.packageTarget("https://www.npmjs.com/package/@types"))
    }

    @Test
    fun usgsTsunamiFlagNeverClaimsAWarningStatus() {
        for (flag in listOf(0, 1)) {
            val preview = UsgsLinkPreview.parseUsgs(
                """{"properties":{"title":"Earthquake","tsunami":$flag}}""", "event", "https://earthquake.usgs.gov/",
            )
            assertFalse(preview.details.any { it.value.contains("warning", ignoreCase = true) })
            assertEquals(flag == 1, preview.details.any { it.label == "Tsunami information" })
        }
    }
}
