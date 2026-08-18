package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubLinkPreviewTest {
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
