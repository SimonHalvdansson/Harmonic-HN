package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Public display data only. The potentially huge submission-ID array is not retained. */
data class UserProfileData(
    val id: String,
    val created: Long,
    val karma: Int,
    val about: String,
    val hasSubmissions: Boolean,
) {
    companion object {
        fun from(user: HackerNewsUserDto) = UserProfileData(
            user.id, user.created, user.karma, user.about.orEmpty().trim(), user.submitted.isNotEmpty(),
        )
    }
}

/**
 * 32 most recently used public profiles, fresh for five minutes, with a 1 MiB text budget.
 * Stale entries remain available to render during refresh/failure. Oversized entries are returned
 * without retention. Username keys are trimmed but case-sensitive, as required by the HN API.
 * Each caller owns one request reference; only the last departing caller cancels the download.
 */
class UserProfileRepository(
    private val api: HackerNewsApi,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxEntries: Int = 32,
    private val maxTextBytes: Int = 1024 * 1024,
    private val freshMillis: Long = 5 * 60 * 1000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UserProfileLoader {
    private data class Entry(val data: UserProfileData, val fetchedAt: Long) {
        val bytes: Long get() = 64L + 2L * (data.id.length + data.about.length)
    }
    private class Request {
        lateinit var result: Deferred<Result<UserProfileData>>
        var references = 0
    }
    private val mutex = Mutex()
    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val pending = mutableMapOf<String, Request>()

    init {
        require(maxEntries > 0 && maxTextBytes > 0 && freshMillis >= 0)
    }

    override fun cached(username: String): UserProfileData? = entries.value[username.trim()]?.data

    override suspend fun load(username: String): UserProfileData = load(username, force = false)

    override suspend fun refresh(username: String): UserProfileData = load(username, force = true)

    private suspend fun load(username: String, force: Boolean): UserProfileData = withContext(dispatcher) {
        val key = username.trim()
        require(key.isNotEmpty())
        val request = mutex.withLock {
            val cached = entries.value[key]
            val age = cached?.let { nowMillis() - it.fetchedAt }
            if (!force && cached != null && age != null && age in 0..freshMillis) {
                entries.value = (entries.value - key) + (key to cached)
                return@withContext cached.data
            }
            val request = pending[key]?.takeUnless { it.result.isCompleted } ?: Request().also { created ->
                created.result = scope.async(dispatcher, start = CoroutineStart.LAZY) {
                    try {
                        val user = api.getUser(key) ?: error("Hacker News user not found")
                        val data = UserProfileData.from(user)
                        currentCoroutineContext().ensureActive()
                        mutex.withLock {
                            if (pending[key] === created) {
                                val entry = Entry(data, nowMillis())
                                val updated = (entries.value - key).toMutableMap()
                                if (entry.bytes <= maxTextBytes) updated[key] = entry
                                while (updated.size > maxEntries || updated.values.sumOf { it.bytes } > maxTextBytes) {
                                    updated.remove(updated.keys.first())
                                }
                                entries.value = updated
                            }
                        }
                        Result.success(data)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }
                pending[key] = created
            }
            request.references++
            request
        }
        request.result.start()
        try {
            request.result.await().getOrThrow()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (--request.references == 0) {
                        if (pending[key] === request) pending.remove(key)
                        request.result.cancel()
                    }
                }
            }
        }
    }
}
