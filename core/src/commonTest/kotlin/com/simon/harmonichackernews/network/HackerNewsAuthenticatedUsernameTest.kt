package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HackerNewsAuthenticatedUsernameTest {
    @Test
    fun authenticatedHeaderPreservesTheAuthoritativeExactSpelling() {
        assertEquals("MiXeD", HackerNewsActionParser.authenticatedUsername(
            "<span class='pagetop'><a id='me' href='user?id=MiXeD'>MiXeD</a></span>"))
        assertEquals("MiXeD", HackerNewsActionParser.authenticatedUsername(
            "<span class='pagetop'><a id='me' href='https://news.ycombinator.com/user?id=MiXeD'>MiXeD</a></span>"))
    }

    @Test
    fun malformedOrUnrelatedIdentityMarkersCannotConfirmALogin() {
        for (body in listOf(
            "<input name='fnid' value='token'>",
            "<div class='commtext'><a id='me' href='user?id=MiXeD'>MiXeD</a></div>",
            header("https://example.com/user?id=MiXeD", "MiXeD"),
            header("user?id=MiXeD", "mixed"),
            header("user?id=MiXeD&id=Other", "MiXeD"),
            header("user?id=", ""),
            header("item?id=MiXeD", "MiXeD"),
            header("user?id=MiXeD#fragment", "MiXeD"),
            header("user?id=MiXeD", "MiXeD") + header("user?id=Other", "Other"),
        )) assertNull(HackerNewsActionParser.authenticatedUsername(body), body)
    }

    private fun header(href: String, username: String) =
        "<span class='pagetop'><a id='me' href='$href'>$username</a></span>"
}
