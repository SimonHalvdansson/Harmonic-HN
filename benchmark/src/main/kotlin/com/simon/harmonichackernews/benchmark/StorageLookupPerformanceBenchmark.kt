package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.TimestampedItem
import com.simon.harmonichackernews.settings.ContentFilterKeys
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.UserTagKeys
import com.simon.harmonichackernews.settings.UserTagsRepository
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Repeated repository reads; fixtures and first-read validation stay outside measurement. */
@RunWith(AndroidJUnit4::class)
class StorageLookupPerformanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    @Test
    fun userTagAmong200() {
        val repository = UserTagsRepository(InMemoryKeyValueStore().apply {
            putString(UserTagKeys.TAGS, (0 until 200).joinToString(",", "{", "}") {
                "\"User$it\":\"tag$it\""
            })
        })
        check(repository.tagFor(" USER199 ") == "tag199")
        benchmarkRule.measureRepeated { sink = repository.tagFor(" USER199 ") }
    }

    @Test
    fun blockedUserAmong100() {
        val repository = blockedUsers()
        check(repository.containsUser(" USER99 "))
        benchmarkRule.measureRepeated { sink = repository.containsUser(" USER99 ") }
    }

    @Test
    fun unblockedUserAmong100() {
        val repository = blockedUsers()
        check(!repository.containsUser("missing"))
        benchmarkRule.measureRepeated { sink = repository.containsUser("missing") }
    }

    @Test fun savedSnapshot30() = savedSnapshot(30)
    @Test fun savedSnapshot2000() = savedSnapshot(2_000)

    private fun savedSnapshot(count: Int) {
        val repository = SavedItemsRepository(InMemoryKeyValueStore())
        repository.saveItems(
            SavedItemSource.FAVORITES,
            (1..count).shuffled(Random(42)).map { TimestampedItem(it, it.toLong()) },
        )
        check(repository.loadSnapshot(SavedItemSource.FAVORITES).itemIds == (count downTo 1).toList())
        benchmarkRule.measureRepeated { sink = repository.loadSnapshot(SavedItemSource.FAVORITES) }
    }

    private fun blockedUsers() = ContentFilterRepository(InMemoryKeyValueStore().apply {
        putString(ContentFilterKeys.USERS, (0 until 100).joinToString(", ") { "User$it" })
    })
}
