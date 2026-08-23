package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.TestKeyValueStore
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLocalModelStorageLocationTest {
    @Test
    fun selectedDirectoryIsCreatedAndRestored() {
        val temporaryRoot = createTempDirectory("harmonic-model-location-test")
        try {
            val preferences = TestKeyValueStore()
            val defaultDirectory = temporaryRoot.resolve("default")
            val selectedDirectory = temporaryRoot.resolve("selected")
            val location = DesktopLocalModelStorageLocation(preferences, defaultDirectory)

            assertEquals(defaultDirectory.toAbsolutePath().normalize().toString(), location.directoryPath)
            assertNull(location.changeDirectory(selectedDirectory.toString()))
            assertEquals(selectedDirectory.toAbsolutePath().normalize().toString(), location.directoryPath)
            assertEquals(true, Files.isDirectory(selectedDirectory))

            val restored = DesktopLocalModelStorageLocation(preferences, defaultDirectory)
            assertEquals(location.directoryPath, restored.directoryPath)
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }
}
