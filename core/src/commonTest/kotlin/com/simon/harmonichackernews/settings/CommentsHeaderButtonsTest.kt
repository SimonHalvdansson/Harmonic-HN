package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.*

class CommentsHeaderButtonsTest {
    @Test fun parentNavigationAlwaysStaysInOverflow() {
        val buttons = CommentsHeaderButtons(CommentsHeaderButton.entries.associateWith { true })
        val inline = buttons.inlineButtons(CommentsHeaderButton.entries, 30, true, false)
        for (button in listOf(CommentsHeaderButton.OPEN_PARENT, CommentsHeaderButton.OPEN_TOP_LEVEL)) {
            assertFalse(button.configurable)
            assertFalse(button in inline)
        }
    }

    @Test fun resetRemovesOverridesAndRestoresContextualDefaultsWithoutChangingOtherSettings() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setCommentsHeaderMetaInfo(CommentsHeaderMetaInfo.BUTTONS)
        repository.setCommentBoolean(CommentBooleanPreference.HEADER_DOMAIN, false)
        val original = repository.snapshot()
        CommentsHeaderButton.entries.filter { it.configurable }.forEach {
            repository.setCommentsHeaderButton(it, !original.comments.headerButtons.isShown(it, true, true))
        }
        // Also clean up a choice saved before parent navigation became fixed.
        store.putBoolean(CommentsHeaderButton.OPEN_PARENT.storageKey, true)
        repository.resetCommentsHeaderButtons()
        assertEquals(original, repository.snapshot())
        assertTrue(CommentsHeaderButton.entries.none { store.contains(it.storageKey) })
        val restored = AppSettingsRepository(store, emptyFlow()).snapshot().comments.headerButtons
        assertFalse(restored.isShown(CommentsHeaderButton.BOOKMARK, true, false))
        assertTrue(restored.isShown(CommentsHeaderButton.BOOKMARK, true, true))
        assertTrue(restored.isShown(CommentsHeaderButton.BOOKMARK, false, false))
    }

    @Test fun domainAndExplicitButtonChoicesSurviveBackupWithoutChangingUnchosenDefaults() {
        val source = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(source, emptyFlow())
        assertTrue(repository.snapshot().comments.showHeaderDomain)
        repository.setCommentBoolean(CommentBooleanPreference.HEADER_DOMAIN, false)
        repository.setCommentsHeaderButton(CommentsHeaderButton.USER, false)
        repository.setCommentsHeaderButton(CommentsHeaderButton.ARCHIVE, true)
        val destination = InMemoryKeyValueStore()
        SettingsTransfer(destination, InMemoryKeyValueStore()).import(SettingsTransfer(source, InMemoryKeyValueStore()).export())
        val restored = AppSettingsRepository(destination, emptyFlow()).snapshot().comments
        val display = CommentDisplaySettings.from(restored, false, false, true, false)
        assertFalse(display.showHeaderDomain)
        assertTrue(display.showFavicons, "Hiding the domain must retain the thumbnail preference")
        assertEquals(mapOf(CommentsHeaderButton.USER to false, CommentsHeaderButton.ARCHIVE to true), display.headerButtons.overrides)
        for (signedIn in listOf(false, true)) for (large in listOf(false, true)) {
            assertFalse(display.headerButtons.isShown(CommentsHeaderButton.USER, signedIn, large))
            assertTrue(display.headerButtons.isShown(CommentsHeaderButton.ARCHIVE, signedIn, large))
            assertEquals(!signedIn || large, display.headerButtons.isShown(CommentsHeaderButton.BOOKMARK, signedIn, large))
        }
    }

    @Test fun capacityPreservesOrderAndShareAndOverflowStaysReachable() {
        val all = CommentsHeaderButton.entries
        val preferences = CommentsHeaderButtons(all.associateWith { true })
        for (capacity in 1..20) {
            val inline = preferences.inlineButtons(all, capacity, true, false)
            assertTrue(inline.size < capacity)
            assertEquals(all.filter { it in inline }, inline)
            if (capacity > 1) assertTrue(CommentsHeaderButton.SHARE in inline)
            assertEquals(all.size, inline.size + all.count { it !in inline })
        }
        assertEquals(emptyList(), preferences.inlineButtons(all, 0, false, false))
        val withoutShare = CommentsHeaderButtons(all.associateWith { it != CommentsHeaderButton.SHARE })
        assertEquals(all.take(3), withoutShare.inlineButtons(all, 4, true, false))
    }

    @Test fun defaultToolbarRemainsContextDependentAndExplicitOverridesWin() {
        val preferences = CommentsHeaderButtons()
        for (signedIn in listOf(false, true)) for (large in listOf(false, true)) {
            assertEquals(!signedIn || large, preferences.isShown(CommentsHeaderButton.REFRESH, signedIn, large))
            assertEquals(!signedIn, preferences.isShown(CommentsHeaderButton.SIDE_BY_SIDE, signedIn, large))
            assertFalse(preferences.isShown(CommentsHeaderButton.SEARCH, signedIn, large))
        }
        val explicit = CommentsHeaderButtons(mapOf(CommentsHeaderButton.REFRESH to false))
        assertFalse(explicit.isShown(CommentsHeaderButton.REFRESH, false, true))
    }
}
