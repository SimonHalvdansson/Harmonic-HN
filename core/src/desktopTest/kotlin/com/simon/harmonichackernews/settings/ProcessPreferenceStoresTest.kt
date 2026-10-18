package com.simon.harmonichackernews.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertSame

class ProcessPreferenceStoresTest {
    @Test
    fun slowMigrationDoesNotBlockOtherFilesAndSameFileSharesOneWriter() = runBlocking {
        val directory = createTempDirectory("harmonic-preference-open-")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val path = directory.resolve("slow.preferences_pb").toString()
        val migration = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true
            override suspend fun migrate(currentData: Preferences): Preferences {
                entered.complete(Unit)
                release.await()
                return currentData
            }
            override suspend fun cleanUp() = Unit
        }
        try {
            val first = async { ProcessPreferenceStores.open(path, listOf(migration)) }
            withTimeout(5_000) { entered.await() }
            val same = async { ProcessPreferenceStores.open(path) }
            try {
                withTimeout(5_000) {
                    ProcessPreferenceStores.open(directory.resolve("independent.preferences_pb").toString())
                }
            } finally { release.complete(Unit) }
            assertSame(first.await(), same.await())
        } finally { release.complete(Unit); directory.toFile().deleteRecursively() }
    }
}
