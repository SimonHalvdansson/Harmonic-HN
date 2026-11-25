package com.simon.harmonichackernews.summary

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class DesktopExternalModelAccessTest {
    @Test
    fun validatesTheContainerAndOpensTheOriginalFile() = runTest {
        val path = Files.createTempFile("linked-model-", ".litertlm")
        try {
            Files.writeString(path, "other container")
            assertFailsWith<IllegalArgumentException> { DesktopExternalModelAccess.inspect(path.toString()) }
            Files.writeString(path, "LITERTLM-test-header")
            val file = DesktopExternalModelAccess.inspect(path.toString())
            assertEquals(path.fileName.toString(), file.name)
            assertEquals(Files.size(path), file.sizeBytes)
            assertEquals(path.toString(), DesktopExternalModelAccess.withPath(file) { it })
        } finally {
            Files.deleteIfExists(path)
        }
        assertFailsWith<IllegalArgumentException> { DesktopExternalModelAccess.inspect(path.toString()) }
    }
}
