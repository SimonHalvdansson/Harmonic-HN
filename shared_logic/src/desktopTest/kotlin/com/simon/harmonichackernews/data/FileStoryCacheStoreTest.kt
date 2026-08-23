package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileStoryCacheStoreTest {
    @Test
    fun replacingPayloadPromotesACompleteTemporaryFileAndCleansItUp() {
        val directory = createTempDirectory("harmonic-story-cache-")
        try {
            val store = FileStoryCacheStore(
                root = Path(directory.toString()),
                accessTimes = FileAccessTimeStore(InMemoryKeyValueStore()),
            )

            assertTrue(store.write(StoryCacheKeys.FULL_NAMESPACE, "42.json", "old".encodeToByteArray()))
            assertTrue(store.write(StoryCacheKeys.FULL_NAMESPACE, "42.json", "replacement".encodeToByteArray()))

            assertContentEquals(
                "replacement".encodeToByteArray(),
                requireNotNull(store.read(StoryCacheKeys.FULL_NAMESPACE, "42.json")),
            )
            assertFalse(
                Files.exists(directory.resolve(StoryCacheKeys.FULL_NAMESPACE).resolve(".42.json.tmp")),
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
