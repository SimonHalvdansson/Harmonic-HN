package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HackerNewsWebConcurrencyTest {
    @Test
    fun independentListsOverlapButPaginationAndMergedOrderRemainStable() = runTest {
        var active = 0
        var peak = 0
        val seen = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val comments = request.url.parameters["comments"] == "t"
                    val second = request.url.parameters["p"] == "2"
                    seen += "${if (comments) "comments" else "stories"}:${if (second) 2 else 1}"
                    active++
                    peak = maxOf(peak, active)
                    try { delay(if (comments) 50 else 150) } finally { active-- }
                    val id = if (comments) { if (second) 4 else 3 } else { if (second) 2 else 1 }
                    val link = if (comments) {
                        """<span class="comhead"><span class="age"><a href="item?id=$id">age</a></span></span>"""
                    } else {
                        """<tr class="athing" id="$id"><td>Story</td></tr>"""
                    }
                    val next = if (second) "" else """<a class="morelink" href="favorites?id=test&amp;p=2${if (comments) "&amp;comments=t" else ""}">More</a>"""
                    respond("<table>$link</table>$next")
                }
            }
        }
        try {
            val result = KtorHackerNewsWebRepository(transport, dispatcher).getUserItems("favorites", "test")
            assertEquals(listOf(1, 2, 3, 4), result.itemIds)
            assertEquals(listOf(3, 4), result.commentIds)
            assertEquals(2, peak)
            assertEquals(300, testScheduler.currentTime)
            assertEquals(listOf("stories:1", "comments:1", "comments:2", "stories:2"), seen)
        } finally { transport.close() }
    }
}
