package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichLinkPreviewParsersTest {
    @Test
    fun parsesEveryGitHubShape() {
        val cases = listOf(
            LinkPreviewType.GITHUB_ISSUE to GitHubPreviewTarget(
                LinkPreviewType.GITHUB_ISSUE,
                "octo",
                "project",
                identifier = "12",
            ),
            LinkPreviewType.GITHUB_PULL_REQUEST to GitHubPreviewTarget(
                LinkPreviewType.GITHUB_PULL_REQUEST,
                "octo",
                "project",
                identifier = "13",
            ),
            LinkPreviewType.GITHUB_FILE to GitHubPreviewTarget(
                LinkPreviewType.GITHUB_FILE,
                "octo",
                "project",
                ref = "main",
                filePath = "README.md",
            ),
            LinkPreviewType.GITHUB_RELEASE to GitHubPreviewTarget(
                LinkPreviewType.GITHUB_RELEASE,
                "octo",
                "project",
                identifier = "v1.0",
            ),
            LinkPreviewType.GITHUB_DISCUSSION to GitHubPreviewTarget(
                LinkPreviewType.GITHUB_DISCUSSION,
                "octo",
                "project",
                identifier = "14",
            ),
        )
        val responses = mapOf(
            LinkPreviewType.GITHUB_ISSUE to
                """{"title":"Issue title","body":"Markdown body","state":"open","comments":2,"user":{"login":"octocat"}}""",
            LinkPreviewType.GITHUB_PULL_REQUEST to
                """{"title":"Pull title","body":"Markdown body","state":"closed","merged":true,"additions":10,"deletions":2,"changed_files":3,"commits":4,"user":{"login":"octocat"}}""",
            LinkPreviewType.GITHUB_FILE to
                """{"name":"README.md","path":"README.md","type":"file","size":2048,"sha":"1234567890abcdef","download_url":"https://example.com/file"}""",
            LinkPreviewType.GITHUB_RELEASE to
                """{"name":"Version 1","tag_name":"v1.0","body":"Release notes","prerelease":false,"draft":false,"assets":[{}],"author":{"login":"octocat"}}""",
            LinkPreviewType.GITHUB_DISCUSSION to
                """{"title":"Discussion title","body":"Discussion body","comments":5,"category":{"name":"Ideas"},"user":{"login":"octocat"}}""",
        )

        cases.forEach { (type, target) ->
            val result = RichLinkPreviewParsers.parseGitHub(
                type,
                responses.getValue(type),
                target,
                "https://github.com/octo/project",
            )
            assertEquals(type, result.type)
            assertTrue(result.title.isNotBlank())
            assertTrue(result.details.isNotEmpty())
            when (type) {
                LinkPreviewType.GITHUB_ISSUE -> assertEquals(
                    "2 comments",
                    result.details.single { it.label == "Comments" }.displayText,
                )
                LinkPreviewType.GITHUB_RELEASE -> {
                    assertEquals("octo / project", result.title)
                    assertEquals("Version 1 · v1.0", result.subtitle)
                }
                LinkPreviewType.GITHUB_DISCUSSION -> assertEquals(
                    "5 comments",
                    result.details.single { it.label == "Comments" }.displayText,
                )
                else -> Unit
            }
        }
    }

    @Test
    fun parsesEveryHuggingFaceShape() {
        val cases = listOf(
            Triple(
                LinkPreviewType.HUGGING_FACE_DATASET,
                HuggingFacePreviewTarget(LinkPreviewType.HUGGING_FACE_DATASET, "owner", "data"),
                """{"id":"owner/data","description":"Dataset","downloads":123,"likes":4,"tags":["format:json","size_categories:1K<n<10K"]}""",
            ),
            Triple(
                LinkPreviewType.HUGGING_FACE_SPACE,
                HuggingFacePreviewTarget(LinkPreviewType.HUGGING_FACE_SPACE, "owner", "space"),
                """{"id":"owner/space","sdk":"gradio","likes":8,"cardData":{"title":"Space title","short_description":"A demo"},"runtime":{"stage":"RUNNING","hardware":{"current":"cpu-basic"}}}""",
            ),
            Triple(
                LinkPreviewType.HUGGING_FACE_PAPER,
                HuggingFacePreviewTarget(LinkPreviewType.HUGGING_FACE_PAPER, name = "2608.00001"),
                """{"id":"2608.00001","title":"Paper title","summary":"Abstract","upvotes":9,"authors":[{"name":"Ada"}]}""",
            ),
            Triple(
                LinkPreviewType.HUGGING_FACE_COLLECTION,
                HuggingFacePreviewTarget(LinkPreviewType.HUGGING_FACE_COLLECTION, "owner", "collection"),
                """{"title":"Collection title","description":"Models","items":[{}],"owner":{"fullname":"Owner","followerCount":7}}""",
            ),
        )

        cases.forEach { (type, target, response) ->
            val result = RichLinkPreviewParsers.parseHuggingFace(
                type,
                response,
                target,
                "https://huggingface.co/example",
            )
            assertEquals(type, result.type)
            assertTrue(result.title.isNotBlank())
            assertTrue(result.details.isNotEmpty())
        }
    }

    @Test
    fun parsesStructuredServiceAndSocialResponsesWithoutHtmlBodies() {
        val results = listOf(
            RichLinkPreviewParsers.parseStatusPage(
                """{"page":{"name":"Example Status"},"incident":{"name":"API unavailable","status":"resolved","impact":"major","incident_updates":[{"body":"Recovered"}]}}""",
                "https://example.statuspage.io/incidents/abc",
            ),
            RichLinkPreviewParsers.parseCrossref(
                """{"message":{"title":["Article title"],"container-title":["Journal"],"author":[{"given":"Ada","family":"Lovelace"}],"published":{"date-parts":[[2026,8,18]]},"type":"journal-article","publisher":"Publisher","is-referenced-by-count":12}}""",
                "10.1000/example",
                "https://doi.org/10.1000/example",
            ),
            RichLinkPreviewParsers.parseUsgs(
                """{"properties":{"title":"M 7.0 - Example","place":"Example","mag":7.0,"sig":700,"tsunami":1,"status":"reviewed","type":"earthquake"},"geometry":{"coordinates":[1.0,2.0,12.5]}}""",
                "us123",
                "https://earthquake.usgs.gov/earthquakes/eventpage/us123",
            ),
            RichLinkPreviewParsers.parseSubstackFeed(
                """<?xml version="1.0"?><rss><channel><title>Example Publication</title><item><title>RSS article</title><link>https://writer.substack.com/p/rss-article</link><description><![CDATA[Writer&#8217;s feed summary]]></description><pubDate>Tue, 18 Aug 2026 12:00:00 GMT</pubDate></item></channel></rss>""",
                "https://writer.substack.com/p/rss-article",
            ),
            RichLinkPreviewParsers.parseMastodon(
                """{"content":"<p>This must not be parsed</p>","account":{"display_name":"Ada","username":"ada","acct":"ada@example.social","avatar":"https://example.social/avatar.png"},"replies_count":1,"reblogs_count":2,"favourites_count":3}""",
                "https://example.social/@ada/1",
            ),
            RichLinkPreviewParsers.parseBluesky(
                """{"thread":{"post":{"author":{"displayName":"Ada","handle":"ada.bsky.social"},"record":{"text":"Structured post text","createdAt":"2026-08-18T12:00:00Z"},"replyCount":1,"repostCount":2,"likeCount":3,"quoteCount":4}}}""",
                "https://bsky.app/profile/ada.bsky.social/post/abc",
            ),
            RichLinkPreviewParsers.parseOEmbed(
                LinkPreviewType.REDDIT_POST,
                """{"title":"Reddit title","author_name":"u/ada","provider_name":"Reddit","html":"<blockquote>This must not be parsed</blockquote>"}""",
                "https://reddit.com/r/example/comments/abc/title",
            ),
        )

        assertEquals(
            setOf(
                LinkPreviewType.STATUS_PAGE,
                LinkPreviewType.CROSSREF_ARTICLE,
                LinkPreviewType.USGS_EARTHQUAKE,
                LinkPreviewType.SUBSTACK_ARTICLE,
                LinkPreviewType.MASTODON_POST,
                LinkPreviewType.BLUESKY_POST,
                LinkPreviewType.REDDIT_POST,
            ),
            results.map { it.type }.toSet(),
        )
        assertEquals(
            "Writer’s feed summary",
            results.singleType(LinkPreviewType.SUBSTACK_ARTICLE).description,
        )
        assertNull(results.singleType(LinkPreviewType.MASTODON_POST).description)
        assertTrue(
            results.singleType(LinkPreviewType.MASTODON_POST).details.none { it.label == "Language" },
        )
        assertNull(results.singleType(LinkPreviewType.REDDIT_POST).description)
    }

    @Test
    fun parsesEveryPackageRegistryShape() {
        val cases = listOf(
            Triple(
                LinkPreviewType.NPM_PACKAGE,
                PackagePreviewTarget(LinkPreviewType.NPM_PACKAGE, "example"),
                """{"name":"example","version":"1.2.3","description":"npm package","license":"MIT","dependencies":{"one":"1"},"author":{"name":"Ada"}}""",
            ),
            Triple(
                LinkPreviewType.PYPI_PACKAGE,
                PackagePreviewTarget(LinkPreviewType.PYPI_PACKAGE, "example"),
                """{"info":{"name":"example","version":"1.2.3","summary":"PyPI package","license_expression":"MIT","author":"Ada","requires_python":">=3.11"}}""",
            ),
            Triple(
                LinkPreviewType.CRATES_PACKAGE,
                PackagePreviewTarget(LinkPreviewType.CRATES_PACKAGE, "example"),
                """{"crate":{"name":"example","newest_version":"1.2.3","description":"Rust crate","downloads":100,"recent_downloads":20,"repository":"https://github.com/example/project"}}""",
            ),
            Triple(
                LinkPreviewType.GO_PACKAGE,
                PackagePreviewTarget(LinkPreviewType.GO_PACKAGE, "example.org/mod/pkg"),
                """{"modulePath":"example.org/mod","version":"v1.2.3","isLatest":true,"isStandardLibrary":false,"path":"example.org/mod/pkg","name":"pkg","synopsis":"Go package","isRedistributable":true}""",
            ),
            Triple(
                LinkPreviewType.HOMEBREW_PACKAGE,
                PackagePreviewTarget(LinkPreviewType.HOMEBREW_PACKAGE, "example", "formula"),
                """{"name":"example","desc":"Homebrew formula","homepage":"https://example.com","license":"MIT","versions":{"stable":"1.2.3"},"dependencies":["one"],"analytics":{"install":{"30d":{"example":42}}}}""",
            ),
        )

        val results = cases.map { (type, target, response) ->
            RichLinkPreviewParsers.parsePackage(
                type,
                response,
                target,
                "https://example.com/package",
            )
        }

        assertEquals(cases.map { it.first }, results.map { it.type })
        assertTrue(results.all { it.title.isNotBlank() && it.details.isNotEmpty() })
    }

    private fun List<LinkPreviewInfo>.singleType(type: LinkPreviewType): LinkPreviewInfo =
        single { it.type == type }
}
