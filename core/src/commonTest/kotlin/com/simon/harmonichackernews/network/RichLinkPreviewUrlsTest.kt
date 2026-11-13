package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RichLinkPreviewUrlsTest {
    @Test
    fun classifiesEveryNetworkBackedPreviewType() {
        val examples = mapOf(
            LinkPreviewType.GITHUB_REPOSITORY to "https://github.com/salmanzafar949/ctxdiff",
            LinkPreviewType.GITHUB_ISSUE to "https://github.com/BurntSushi/ripgrep/issues/3494",
            LinkPreviewType.GITHUB_PULL_REQUEST to "https://github.com/openjdk/jdk/pull/31120",
            LinkPreviewType.GITHUB_FILE to "https://github.com/FFmpeg/FFmpeg/blob/n9.0/RELEASE_NOTES",
            LinkPreviewType.GITHUB_RELEASE to "https://github.com/astral-sh/uv/releases/tag/0.12.0",
            LinkPreviewType.GITHUB_DISCUSSION to "https://github.com/transmission/transmission/discussions/9031",
            LinkPreviewType.GITLAB_PROJECT to "https://gitlab.com/sifoo/snigl",
            LinkPreviewType.HUGGING_FACE_MODEL to "https://huggingface.co/moonshotai/Kimi-K3",
            LinkPreviewType.HUGGING_FACE_DATASET to "https://huggingface.co/datasets/huggingface/forensic-refusal/blob/main/glm5.2.jsonl",
            LinkPreviewType.HUGGING_FACE_SPACE to "https://huggingface.co/spaces/HuggingFaceCode/in-the-stack",
            LinkPreviewType.HUGGING_FACE_PAPER to "https://huggingface.co/papers/2608.09888",
            LinkPreviewType.HUGGING_FACE_COLLECTION to "https://huggingface.co/collections/Qwen/qwen38",
            LinkPreviewType.OPENROUTER_MODEL to "https://openrouter.ai/openai/gpt-5.6-sol",
            LinkPreviewType.STACK_EXCHANGE to "https://meta.stackexchange.com/questions/333965/example",
            LinkPreviewType.ARXIV to "https://arxiv.org/abs/2501.06425",
            LinkPreviewType.CROSSREF_ARTICLE to "https://doi.org/10.1016/j.watres.2026.125866",
            LinkPreviewType.WIKIPEDIA to "https://en.wikipedia.org/wiki/Hacker_News",
            LinkPreviewType.USGS_EARTHQUAKE to "https://earthquake.usgs.gov/earthquakes/eventpage/us6000tkt2/executive",
            LinkPreviewType.MASTODON_POST to "https://mastodon.gamedev.place/@rygorous/117047697255584965",
            LinkPreviewType.BLUESKY_POST to "https://bsky.app/profile/techprodbangers.bsky.social/post/3mr4askb6tk2i",
            LinkPreviewType.REDDIT_POST to "https://old.reddit.com/r/linux/comments/1vcpk8i/example/",
            LinkPreviewType.NPM_PACKAGE to "https://www.npmjs.com/package/serpapi-byline",
            LinkPreviewType.PYPI_PACKAGE to "https://pypi.org/project/ttsproof/",
            LinkPreviewType.CRATES_PACKAGE to "https://crates.io/crates/sqawk",
            LinkPreviewType.GO_PACKAGE to "https://pkg.go.dev/golang.org/x/tools/go/analysis",
            LinkPreviewType.HOMEBREW_PACKAGE to "https://formulae.brew.sh/formula/fastrace",
            LinkPreviewType.STATUS_PAGE to "https://anthropic.statuspage.io/incidents/kmbpgrsszf72",
            LinkPreviewType.SUBSTACK_ARTICLE to "https://nealstephenson.substack.com/p/writing-by-hand-is-good-for-your",
        )

        examples.forEach { (type, url) ->
            assertEquals(type, RichLinkPreviewUrls.type(url), url)
        }
        assertEquals(
            LinkPreviewType.entries.filterNot { it == LinkPreviewType.TWITTER_X }.toSet(),
            examples.keys,
        )
    }

    @Test
    fun knownHostsTakePrecedenceOverFederatedSocialPaths() {
        // Mastodon accepts any host with an /@user/numeric-id path. Known providers
        // must keep ownership when their repository/model URL also has that shape.
        assertEquals(
            LinkPreviewType.GITHUB_REPOSITORY,
            RichLinkPreviewUrls.type("https://github.com/@example/123"),
        )
        assertEquals(
            LinkPreviewType.HUGGING_FACE_MODEL,
            RichLinkPreviewUrls.type("https://huggingface.co/@example/123"),
        )
        assertEquals(
            LinkPreviewType.MASTODON_POST,
            RichLinkPreviewUrls.type("https://social.example/@example/123"),
        )
    }

    @Test
    fun everyEnabledNetworkTypeHasExactlyOneLoader() {
        val externalTypes = LinkPreviewProviders.externalTypes
        val enabledNetworkTypes = LinkPreviewType.entries
            .filter { it.defaultEnabled && it !in externalTypes }
            .toSet()

        assertEquals(
            enabledNetworkTypes,
            LinkPreviewProviders.recognizedTypes.filter(LinkPreviewType::defaultEnabled).toSet(),
        )
        enabledNetworkTypes.forEach { type ->
            assertEquals(1, LinkPreviewProviders.loaderCount(type), type.title)
        }
        externalTypes.forEach { type ->
            assertEquals(0, LinkPreviewProviders.loaderCount(type), type.title)
        }
    }

    @Test
    fun twitterRemainsOnTheExternalNitterPath() = runTest {
        var networkLoadCalls = 0
        val useCase = LinkPreviewUseCase(object : LinkPreviewRepository {
            override suspend fun load(type: LinkPreviewType, url: String): LinkPreviewData {
                networkLoadCalls++
                error("Network loader should not handle Twitter")
            }

            override suspend fun getArchiveUrl(url: String) = error("unused")
        })

        assertNull(RichLinkPreviewUrls.type("https://x.com/example/status/123"))
        assertFailsWith<LinkPreviewException> {
            useCase.load(LinkPreviewType.TWITTER_X, "https://x.com/example/status/123")
        }
        assertEquals(0, networkLoadCalls)
    }

    @Test
    fun rejectsProviderLandingPagesAndUnrelatedLookalikes() {
        listOf(
            "https://github.com/",
            "https://huggingface.co/datasets",
            "https://pkg.go.dev/",
            "https://substack.com/home",
            "https://reddit.com/r/linux/",
            "not a URL",
        ).forEach { assertNull(RichLinkPreviewUrls.type(it), it) }
    }

    @Test
    fun hostGuardedClassifiersPreservePathAndHostMatching() {
        val hosts = listOf(
            "example.com", "npmjs.com", "WWW.NPMJS.COM", "pypi.org", "crates.io", "pkg.go.dev",
            "formulae.brew.sh", "service.statuspage.io", "statuspage.io", "writer.substack.com",
            "substack.com", "bsky.app", "www.bsky.app", "reddit.com", "OLD.REDDIT.COM",
            "www.old.reddit.com", "old.www.reddit.com", "doi.org", "www.dx.doi.org",
        )
        val paths = listOf(
            "/", "/package/name", "//package//%40scope//name/", "/project/name", "/crates/name",
            "/golang.org/x/tools", "/vuln/GO-123", "/formula/name", "/cask/name", "/other/name",
            "/incidents/123", "//incidents//123/", "/p/article", "/p/", "/profile/a/post/b",
            "//profile//a//post//b/", "/r/kotlin/comments/id/title", "//comments//id/", "/Comments/id",
            "/%63omments/id", "/@user/123", "/10.1234/example",
        )
        for (host in hosts) for (path in paths) {
            val url = "https://$host$path"
            val parsed = NetworkUrl.parse(url)
            val normalizedHost = parsed.host.lowercase()
            val segments = parsed.pathSegments.filter(String::isNotEmpty)
            assertEquals(legacyPackageTarget(parsed), PackageLinkPreview.packageTarget(parsed), url)
            assertEquals(
                if (normalizedHost.endsWith(".statuspage.io") && segments.size >= 2 && segments[0] == "incidents") {
                    normalizedHost to segments[1]
                } else null,
                StatusPageLinkPreview.statusPageIncident(parsed), url,
            )
            assertEquals(
                normalizedHost.endsWith(".substack.com") && segments.size >= 2 && segments[0] == "p",
                SubstackLinkPreview.isSubstackArticle(parsed), url,
            )
            assertEquals(
                normalizedHost == "bsky.app" && segments.size >= 4 && segments[0] == "profile" && segments[2] == "post",
                BlueskyLinkPreview.isBlueskyPost(parsed), url,
            )
            assertEquals(
                normalizedHost.removePrefix("www.").removePrefix("old.") == "reddit.com" && "comments" in segments,
                RedditLinkPreview.isRedditPost(parsed), url,
            )
            assertEquals(
                if (normalizedHost.removePrefix("www.") in setOf("doi.org", "dx.doi.org")) {
                    segments.joinToString("/").takeIf(String::isNotEmpty)
                } else null,
                CrossrefLinkPreview.crossrefDoi(parsed), url,
            )
        }
    }

    private fun legacyPackageTarget(parsed: NetworkUrl): PackagePreviewTarget? {
        val host = parsed.host.lowercase().removePrefix("www.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            host == "npmjs.com" && segments.firstOrNull() == "package" && segments.size >= 2 ->
                PackagePreviewTarget(
                    LinkPreviewType.NPM_PACKAGE,
                    if (segments[1].startsWith('@')) {
                        if (segments.size < 3) return null
                        segments.subList(1, 3).joinToString("/")
                    } else segments[1],
                )
            host == "pypi.org" && segments.firstOrNull() == "project" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.PYPI_PACKAGE, segments[1])
            host == "crates.io" && segments.firstOrNull() == "crates" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.CRATES_PACKAGE, segments[1])
            host == "pkg.go.dev" && segments.isNotEmpty() && segments.first() != "vuln" ->
                PackagePreviewTarget(LinkPreviewType.GO_PACKAGE, segments.joinToString("/"))
            host == "formulae.brew.sh" && segments.size >= 2 && segments[0] in setOf("formula", "cask") ->
                PackagePreviewTarget(LinkPreviewType.HOMEBREW_PACKAGE, segments[1], segments[0])
            else -> null
        }
    }

    @Test
    fun individualSwitchDisablesOnlyItsType() {
        val enabled = LinkPreviewType.entries.toMutableSet().apply {
            remove(LinkPreviewType.GITHUB_ISSUE)
        }
        val useCase = LinkPreviewUseCase(object : LinkPreviewRepository {
            override suspend fun load(type: LinkPreviewType, url: String) = error("unused")
            override suspend fun getArchiveUrl(url: String) = error("unused")
        })
        val preferences = LinkPreviewPreferences(enabled)

        assertNull(
            useCase.selectProvider(
                "https://github.com/BurntSushi/ripgrep/issues/3494",
                preferences,
            ),
        )
        assertEquals(
            LinkPreviewType.GITHUB_RELEASE,
            useCase.selectProvider(
                "https://github.com/astral-sh/uv/releases/tag/0.12.0",
                preferences,
            ),
        )
        assertFalse(LinkPreviewType.GITHUB_ISSUE in preferences.enabledTypes)
    }
}
