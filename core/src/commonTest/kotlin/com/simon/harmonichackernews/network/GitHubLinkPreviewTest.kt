package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubLinkPreviewTest {
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
