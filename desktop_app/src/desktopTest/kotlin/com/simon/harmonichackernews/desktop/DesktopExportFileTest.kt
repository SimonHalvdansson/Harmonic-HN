package com.simon.harmonichackernews.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopExportFileTest {
    @Test
    fun cancellingReplacementDoesNotSelectOrChangeTheExistingFile() {
        val existing = Files.createTempFile("harmonic-export", ".txt").toFile()
        try {
            existing.writeText("Keep this document")
            val selected = chooseExportFile({ existing }) { ExportReplacement.CANCEL }
            assertNull(selected)
            assertEquals("Keep this document", existing.readText())
        } finally { existing.delete() }
    }

    @Test
    fun replacingRequiresExplicitApprovalForTheChosenExistingFile() {
        val existing = Files.createTempFile("harmonic-export", ".txt").toFile()
        try {
            var confirmed = 0
            val selected = chooseExportFile({ existing }) {
                assertEquals(existing, it)
                confirmed++
                ExportReplacement.REPLACE
            }
            assertEquals(existing, selected)
            assertEquals(1, confirmed)
        } finally { existing.delete() }
    }

    @Test
    fun decliningReplacementAllowsChoosingANewFileWithoutAnotherPrompt() {
        val directory = Files.createTempDirectory("harmonic-export").toFile()
        val existing = directory.resolve("existing.txt").apply { writeText("Keep this document") }
        val newFile = directory.resolve("new.txt")
        try {
            val choices = ArrayDeque(listOf(existing, newFile))
            var confirmations = 0
            val selected = chooseExportFile({ choices.removeFirst() }) {
                confirmations++
                ExportReplacement.CHOOSE_ANOTHER
            }
            assertEquals(newFile, selected)
            assertEquals(1, confirmations)
            assertEquals("Keep this document", existing.readText())
        } finally {
            existing.delete()
            directory.delete()
        }
    }
}
