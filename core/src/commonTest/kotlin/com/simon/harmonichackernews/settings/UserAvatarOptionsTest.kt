package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow

class UserAvatarOptionsTest {
    @Test
    fun everyStyleSubsetPreservesExistingUsernameAssignments() {
        val authors = listOf("", "simon", "Simon", "_reader", "7bit", "café", "中文", "😀") +
            List(100) { "reader_$it" }
        for (mask in 0 until (1 shl UserAvatarStyle.entries.size)) {
            val styles = UserAvatarStyle.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val options = UserAvatarOptions(styles.reversed().toSet())
            val choices = styles.ifEmpty { listOf(UserAvatarStyle.MOSAIC) }
            for (author in authors) {
                val expectedIndex = (userAvatarSeed("$author:style").toUInt() % choices.size.toUInt()).toInt()
                assertEquals(choices[expectedIndex], options.styleFor(author), "mask=$mask author=$author")
            }
        }
    }

    @Test
    fun letterStylePersistsAndUsesTheUsernameInitial() {
        val options = UserAvatarOptions(setOf(UserAvatarStyle.LETTERS), UserAvatarShape.ROUNDED, UserAvatarColors.MUTED)
        assertEquals(options, UserAvatarOptions.decode(options.encode()))
        assertEquals(UserAvatarStyle.LETTERS, options.styleFor("simon"))
        assertEquals("Letters", options.summary)
        assertEquals("S", userAvatarInitial("simon"))
        assertEquals("S", userAvatarInitial("Simon"))
        assertEquals("7", userAvatarInitial("7bit"))
        assertEquals("_", userAvatarInitial("_reader"))
        assertEquals("?", userAvatarInitial(""))
    }

    @Test
    fun mixedStylesAndCustomizationPersistWhileDisabled() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        val options = UserAvatarOptions(
            setOf(UserAvatarStyle.ROBOT, UserAvatarStyle.LANDSCAPE),
            UserAvatarShape.ROUNDED, UserAvatarColors.MONOCHROME,
        )
        repository.setUserAvatarsEnabled(false)
        repository.setUserAvatarOptions(options)
        val restored = AppSettingsRepository(store, emptyFlow()).snapshot().comments
        assertEquals(false, restored.userAvatarsEnabled)
        assertEquals(options, restored.userAvatarOptions)
        repository.setUserAvatarsEnabled(true)
        assertEquals(options, repository.snapshot().comments.userAvatarOptions)
        repository.setUserAvatarsEnabled(false)
        repository.setUserAvatarsEnabled(true)
        assertEquals(options, repository.snapshot().comments.userAvatarOptions)
    }

    @Test
    fun selectionIsStableRegardlessOfSetOrderAndOnlyUsesChosenStyles() {
        val options = UserAvatarOptions(setOf(UserAvatarStyle.ROBOT, UserAvatarStyle.ORBITAL, UserAvatarStyle.LANDSCAPE))
        val reversed = options.copy(styles = options.styles.reversed().toSet())
        val results = (0..100).map { index ->
            val author = "user$index"
            val style = options.styleFor(author)
            assertEquals(style, reversed.styleFor(author))
            assertTrue(style in options.styles)
            style
        }.toSet()
        assertEquals(options.styles, results)
        assertEquals(UserAvatarStyle.ROBOT, UserAvatarOptions(setOf(UserAvatarStyle.ROBOT)).styleFor("anyone"))
    }

    @Test
    fun genericIsExclusiveAndPreservesExpressiveChoices() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        val expressive = UserAvatarOptions(setOf(UserAvatarStyle.ROBOT, UserAvatarStyle.ORBITAL))
        repository.setUserAvatarOptions(expressive.copy(generic = true))
        val generic = AppSettingsRepository(store, emptyFlow()).snapshot().comments.userAvatarOptions
        assertTrue(generic.generic)
        assertEquals("Generic", generic.summary)
        assertEquals(expressive.styles, generic.styles)
        repository.setUserAvatarOptions(generic.copy(generic = false))
        assertEquals(expressive, repository.snapshot().comments.userAvatarOptions)
    }

    @Test
    fun malformedStoredOptionsHaveSafeDefaults() {
        assertEquals(UserAvatarOptions(), UserAvatarOptions.decode("unknown;bad;bad;nan"))
        val options = UserAvatarOptions.decode("robot,future;ROUNDED;MUTED;99")
        assertEquals(setOf(UserAvatarStyle.ROBOT), options.styles)
        assertEquals(UserAvatarStyle.MOSAIC, UserAvatarOptions(emptySet()).styleFor("someone"))
    }
}
