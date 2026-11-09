package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubLinkPreviewTest {
    @Test
    fun mergeTimestampPreservesMergedStateWhenBooleanIsMissing() {
        val url = "https://github.com/example/repo/pull/42"
        for (mergeFields in listOf("\"merged\":true", "\"merged_at\":\"2026-07-31T12:00:00Z\"")) {
            val info = GitHubLinkPreview.parseGitHub(LinkPreviewType.GITHUB_PULL_REQUEST,
                """{"title":"A change","state":"closed",$mergeFields}""",
                GitHubLinkPreview.githubTarget(url)!!, url)
            assertEquals("Merged", info.details.single { it.label == "State" }.value)
        }
        val closed = GitHubLinkPreview.parseGitHub(LinkPreviewType.GITHUB_PULL_REQUEST,
            """{"title":"A change","state":"closed","merged_at":null}""",
            GitHubLinkPreview.githubTarget(url)!!, url)
        assertEquals("Closed", closed.details.single { it.label == "State" }.value)
    }

    @Test
    fun fileDescriptionKeepsOnlyPathInformationThatAddsToItsName() {
        val url = "https://github.com/example/repo/blob/main/README.md"
        for ((path, expected) in listOf("README.md" to null, "docs/README.md" to "docs/README.md")) {
            val info = GitHubLinkPreview.parseGitHub(LinkPreviewType.GITHUB_FILE,
                """{"name":"README.md","path":"$path"}""",
                GitHubLinkPreview.githubTarget(url)!!, url)
            assertEquals(expected, info.description)
        }
    }

    @Test
    fun discImageStudioFallbackUsesExactRepositoryMetadataWithoutInventingAnAboutSection() {
        val target = GitHubLinkPreview.githubTarget(DISC_IMAGE_STUDIO_URL)!!
        val info = GitHubLinkPreview.parseGitHubRepositoryPage(DISC_IMAGE_STUDIO_PAGE, target, DISC_IMAGE_STUDIO_URL)

        assertEquals("JiaFeiMiao-K-Cat", info.owner)
        assertEquals("DiscImageStudio", info.name)
        assertEquals("https://avatars.githubusercontent.com/u/60653106?s=60&v=4", info.avatarUrl)
        assertEquals(3970, info.stars)
        assertEquals(5, info.watching)
        assertEquals(117, info.forks)
        assertEquals("GPL-3.0", info.license)
        assertNull(info.about)
        assertNull(info.pageTitle)
        assertNull(info.language)
    }

    @Test
    fun matchingSidebarMetadataCanUseNestedCodeViewPayload() {
        val page = DISC_IMAGE_STUDIO_PAGE.replace(
            "\"payload\":{\"sidebarAbout\":",
            "\"payload\":{\"codeViewRepoRoute\":{\"overview\":{\"sidebarAboutData\":",
        ).replace("}}</script>", "}}}}</script>")
        val target = GitHubLinkPreview.githubTarget(DISC_IMAGE_STUDIO_URL)!!
        val info = GitHubLinkPreview.parseGitHubRepositoryPage(page, target, DISC_IMAGE_STUDIO_URL)
        assertEquals(3970, info.stars)
        assertEquals("GPL-3.0", info.license)
    }

    @Test
    fun unrelatedOrMalformedEmbeddedMetadataLeavesUnavailableFieldsUnknown() {
        for (page in listOf(
            DISC_IMAGE_STUDIO_PAGE.replace("\"repoName\":\"DiscImageStudio\"", "\"repoName\":\"another-repository\""),
            """<meta property="og:title" content="Custom repository title"><script data-target="react-app.embeddedData">{bad json}</script>""",
        )) {
            val target = GitHubLinkPreview.githubTarget(DISC_IMAGE_STUDIO_URL)!!
            val info = GitHubLinkPreview.parseGitHubRepositoryPage(page, target, DISC_IMAGE_STUDIO_URL)
            assertNull(info.stars)
            assertNull(info.watching)
            assertNull(info.forks)
            assertNull(info.license)
            assertNull(info.avatarUrl)
        }
    }

    @Test
    fun zeroRepositoryCountsArePreservedButInvalidCountsStayUnknown() {
        val page = DISC_IMAGE_STUDIO_PAGE.replace("\"stargazerCount\":3970", "\"stargazerCount\":0")
            .replace("\"watcherCount\":5", "\"watcherCount\":-1")
            .replace("\"forksCount\":117", "\"forksCount\":2147483648")
        val info = GitHubLinkPreview.parseGitHubRepositoryPage(page, GitHubLinkPreview.githubTarget(DISC_IMAGE_STUDIO_URL)!!, DISC_IMAGE_STUDIO_URL)
        assertEquals(0, info.stars)
        assertNull(info.watching)
        assertNull(info.forks)
    }

    @Test
    fun repositoryPageCanUseHtmlTitleAndDescriptionWithoutOpenGraph() {
        val url = "https://github.com/example/repo"
        val preview = GitHubLinkPreview.parseGitHubPage(
            LinkPreviewType.GITHUB_REPOSITORY,
            """<title>Repository title</title><meta name="description" content="Repository summary">""",
            GitHubLinkPreview.githubTarget(url)!!,
            url,
        )
        assertEquals("Repository title", preview.title)
        assertEquals("Repository summary", preview.description)
        assertNull(preview.imageUrl)
    }

    @Test
    fun repositoryDescriptionOmitsGitHubContributionBoilerplate() {
        val url = "https://github.com/alex0ptr/once"
        val boilerplate = "Contribute to alex0ptr/once development by creating an account on GitHub."
        val summary = "Run a command once, reuse its output for a while."
        for ((description, expected) in listOf(
            "$summary $boilerplate" to summary,
            "$summary - $boilerplate" to summary,
            boilerplate to null,
            summary to summary,
            "$summary -" to "$summary -",
            // Only the matching repository's generated suffix is removed.
            "Contribute to other/repo development by creating an account on GitHub." to
                "Contribute to other/repo development by creating an account on GitHub.",
        )) {
            val preview = GitHubLinkPreview.parseGitHubPage(
                LinkPreviewType.GITHUB_REPOSITORY,
                """<meta property="og:title" content="Once"><meta property="og:description" content="$description">""",
                GitHubLinkPreview.githubTarget(url)!!,
                url,
            )
            assertEquals(expected, preview.description)
        }
    }

    @Test
    fun parsesOwnerAvatarFromRepositoryResponse() {
        val info = LinkPreviewParsers.parseGitHub(
            """
            {
              "name":"harmonic",
              "owner":{
                "login":"simon",
                "avatar_url":"https://avatars.githubusercontent.com/u/1?v=4"
              }
            }
            """.trimIndent(),
        )

        assertEquals("simon", info.owner)
        assertEquals("https://avatars.githubusercontent.com/u/1?v=4", info.avatarUrl)
    }

    @Test
    fun toleratesRepositoryResponseWithoutOwnerAvatar() {
        val info = LinkPreviewParsers.parseGitHub(
            """{"name":"harmonic","owner":{"login":"simon"}}""",
        )

        assertNull(info.avatarUrl)
    }
}

// Minimal metadata fixture captured from HN story 50346849's destination on 2026-11-03.
internal const val DISC_IMAGE_STUDIO_URL = "https://github.com/JiaFeiMiao-K-Cat/DiscImageStudio"
internal const val DISC_IMAGE_STUDIO_PAGE = """<html><head>
    <meta property="og:title" content="GitHub - JiaFeiMiao-K-Cat/DiscImageStudio">
    <meta property="og:description" content="Contribute to JiaFeiMiao-K-Cat/DiscImageStudio development by creating an account on GitHub.">
    <meta property="og:image" content="https://opengraph.githubassets.com/example/JiaFeiMiao-K-Cat/DiscImageStudio">
    </head><body><script type="application/json" data-target="react-app.embeddedData">
    {"payload":{"sidebarAbout":{"ownerLogin":"JiaFeiMiao-K-Cat","repoName":"DiscImageStudio","stargazerCount":3970,"watcherCount":5,"forksCount":117,"repo":{"ownerAvatarUrl":"https://avatars.githubusercontent.com/u/60653106?s=60\u0026v=4","license":{"spdxId":"GPL-3.0","name":"GNU General Public License v3.0"}}}}}</script></body></html>"""
