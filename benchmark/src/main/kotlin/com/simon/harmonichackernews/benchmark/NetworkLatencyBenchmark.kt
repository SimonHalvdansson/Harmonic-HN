package com.simon.harmonichackernews.benchmark

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.DefaultReplyScanner
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.PollOptionsRepository
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/** Measures request scheduling with repeatable latency; never accesses a live account or server. */
@RunWith(AndroidJUnit4::class)
class NetworkLatencyBenchmark {
    @Test
    fun independentItemRequests() = runBlocking {
        repeat(5) { iteration ->
            val api = DelayedApi()
            var start = SystemClock.elapsedRealtime()
            val options = PollOptionsRepository(api).loadOptions(IntArray(8) { 10 + it }).toList()
            assertEquals(8, options.size)
            Log.i("HnNetworkBenchmark", "poll iteration=$iteration durationMs=${SystemClock.elapsedRealtime() - start}")

            start = SystemClock.elapsedRealtime()
            val replies = DefaultReplyScanner(api).scan("test", previousLastSeenItemId = 50)
            assertEquals(16, replies.replies.size)
            Log.i("HnNetworkBenchmark", "replies iteration=$iteration durationMs=${SystemClock.elapsedRealtime() - start}")
        }
    }

    private class DelayedApi : HackerNewsApi {
        override suspend fun getItem(id: Int): HackerNewsItemDto {
            delay(150)
            return HackerNewsItemDto(
                id = id,
                type = if (id in 1..4) "story" else "comment",
                by = "other",
                time = Clock.System.now().epochSeconds.toInt(),
                text = "Option or reply $id",
                kids = if (id in 1..4) List(4) { id * 100 + it } else emptyList(),
            )
        }
        override suspend fun getUser(username: String): HackerNewsUserDto {
            delay(150)
            return HackerNewsUserDto(id = username, submitted = listOf(4, 3, 2, 1))
        }
        override suspend fun getMaxItemId(): Int { delay(150); return 1_000 }
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected feed request")
    }
}
