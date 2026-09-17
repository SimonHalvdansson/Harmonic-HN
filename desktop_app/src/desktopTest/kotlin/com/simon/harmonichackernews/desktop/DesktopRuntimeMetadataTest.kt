package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopRuntimeMetadataTest {
    @Test
    fun packagedMetadataHasARealVersionAndNoDebugSettings() {
        val metadata = DesktopRuntimeMetadata.load(debug = false)

        assertTrue(metadata.versionName.isNotBlank())
        assertTrue(metadata.versionCode > 0)
        assertEquals(metadata.versionCode.toString(), metadata.buildNumber)
        assertEquals("release", metadata.buildType)
        assertFalse(metadata.debug)
        assertFalse(metadata.debugSettingsEnabled)
    }

    @Test
    fun localRunMetadataEnablesDebugSettingsExplicitly() {
        val metadata = DesktopRuntimeMetadata.load(debug = true)

        assertEquals("debug", metadata.buildType)
        assertTrue(metadata.debug)
        assertTrue(metadata.debugSettingsEnabled)
    }
}
