package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredSettingsMutatorTest {
    @Test
    fun commentDepthModeKeepsLegacyBooleanInSync() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        mutator.setCommentDepthIndicatorMode(CommentDepthPreferences.MONOCHROME)
        assertEquals(
            CommentDepthPreferences.MONOCHROME,
            store.getString(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS),
        )
        assertTrue(store.getBoolean(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH, false))

        mutator.setCommentDepthIndicatorMode("unsupported")
        assertEquals(
            CommentDepthPreferences.THEME_DEFAULT,
            store.getString(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS),
        )
        assertFalse(store.getBoolean(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH, true))
    }

    @Test
    fun paletteWritesAreSanitizedAndReportCacheInvalidation() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        assertTrue(mutator.setPaletteTint("unknown", 400, -1, 80))
        assertEquals(PaletteTintPreferences.DEFAULT, store.getString(UserPreferenceKeys.PALETTE_TINT_MODE))
        assertEquals(200, store.getInt(UserPreferenceKeys.PALETTE_TINT_STRENGTH, -1))
        assertEquals(0, store.getInt(UserPreferenceKeys.PALETTE_TINT_COLORFULNESS, -1))
        assertEquals(20, store.getInt(UserPreferenceKeys.PALETTE_TINT_TONE, -1))
        assertFalse(mutator.setPaletteTint("unknown", 400, -1, 80))
        assertTrue(mutator.clearPaletteTint())
        assertFalse(mutator.clearPaletteTint())
    }
}
