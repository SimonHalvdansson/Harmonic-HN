package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyScannerDispatchTest {
    @Test
    fun parentAndReplyProcessingRunOffTheCallingThread() = runBlocking {
        val owner = Thread.currentThread()
        val threads = CopyOnWriteArrayList<Thread>()
        val api = object : HackerNewsApi {
            override suspend fun getUser(username: String) = HackerNewsUserDto(id = username, submitted = listOf(1))
            override suspend fun getMaxItemId() = 10
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected feed")
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                threads += Thread.currentThread()
                return if (id == 1) HackerNewsItemDto(id = 1, kids = listOf(2)) else
                    HackerNewsItemDto(id = 2, type = "comment", by = "alice", text = "<p>Hello <i>there</i></p>")
            }
        }

        val scanner = DefaultReplyScanner(api)
        assertEquals("Hello there", scanner.scan("me", 1).replies.single().text)
        assertEquals(2, scanner.findLatestReply("me").reply?.id)
        assertEquals(4, threads.size)
        assertTrue(threads.all { it !== owner })
    }
}
