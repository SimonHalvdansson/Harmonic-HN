package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTagsRepositoryTest {
    @Test
    fun exactIdentitiesKeepIndependentTagsAndColors() {
        val store = TestKeyValueStore()
        val repository = UserTagsRepository(store)
        assertTrue(repository.setTag(" Alice ", " maintainer ", UserTagColor.Purple))
        assertTrue(repository.setTag("alice", "designer", UserTagColor.Green))
        assertTrue(repository.setTag("ALICE", "other", UserTagColor.Blue))
        assertEquals(mapOf("Alice" to "maintainer", "alice" to "designer", "ALICE" to "other"), repository.tags())
        assertEquals("maintainer", repository.tagFor(" Alice "))
        assertEquals("designer", repository.tagFor("alice"))
        assertEquals("", repository.tagFor("AlIce"))
        assertEquals(UserTagColor.Purple, repository.colorFor("Alice"))
        assertEquals(UserTagColor.Green, repository.colorFor("alice"))
        assertEquals(UserTagColor.Theme, repository.colorFor("AlIce"))
        repository.setTag("Alice", "updated")
        assertEquals(UserTagColor.Purple, repository.colorFor("Alice"))
        repository.setTag("alice", " ")
        assertEquals(mapOf("Alice" to "updated", "ALICE" to "other"), repository.tags())
        assertEquals(UserTagColor.Blue, repository.colorFor("ALICE"))
        assertEquals(UserTagColor.Theme, repository.colorFor("alice"))
    }

    @Test
    fun everyArchivedSpellingIncludingLowercaseIsActiveAsStored() {
        val legacy = """{"Alice":"capitalized","alice":"lowercase","bob":"another"}"""
        val legacyColors = """{"Alice":"purple","alice":"red","bob":"green"}"""
        val store = TestKeyValueStore(mapOf(UserTagKeys.TAGS to legacy, UserTagKeys.COLORS to legacyColors))
        val repository = UserTagsRepository(store)
        assertEquals(mapOf("Alice" to "capitalized", "alice" to "lowercase", "bob" to "another"), repository.tags())
        assertEquals("capitalized", repository.tagFor("Alice"))
        assertEquals("lowercase", repository.tagFor("alice"))
        assertEquals("", repository.tagFor("ALICE"))
        assertEquals(mapOf("Alice" to UserTagColor.Purple, "alice" to UserTagColor.Red, "bob" to UserTagColor.Green), repository.colors())
        assertEquals(emptyList(), repository.legacyTags())
        assertEquals(legacy, store.getString(UserTagKeys.TAGS))
        assertEquals(legacyColors, store.getString(UserTagKeys.COLORS))
        assertFalse(store.contains(UserTagKeys.EXACT_TAGS))
    }

    @Test
    fun exactOverridesWinWithoutHidingUnrelatedArchivesOrInheritingLegacyColors() {
        val legacy = """{"Alice":"capitalized","alice":"archived","bob":"untouched"}"""
        val legacyColors = """{"Alice":"purple","alice":"red","bob":"green"}"""
        val store = TestKeyValueStore(mapOf(
            UserTagKeys.TAGS to legacy, UserTagKeys.COLORS to legacyColors,
            UserTagKeys.EXACT_TAGS to """{"alice":"new exact"}""",
            UserTagKeys.EXACT_COLORS to "{}",
        ))
        val repository = UserTagsRepository(store)
        assertEquals(mapOf("Alice" to "capitalized", "alice" to "new exact", "bob" to "untouched"), repository.tags())
        assertEquals(UserTagColor.Theme, repository.colorFor("alice"))
        assertEquals(UserTagColor.Purple, repository.colorFor("Alice"))
        assertEquals(UserTagColor.Green, repository.colorFor("bob"))
        repository.setTag("alice", "newer exact", UserTagColor.Blue)
        assertEquals(UserTagColor.Blue, repository.colorFor("alice"))
        repository.setTag("alice", "newest exact", UserTagColor.Theme)
        assertEquals(UserTagColor.Theme, repository.colorFor("alice"))
        assertEquals("untouched", repository.tagFor("bob"))
        assertEquals(legacy, store.getString(UserTagKeys.TAGS))
        assertEquals(legacyColors, store.getString(UserTagKeys.COLORS))
    }

    @Test
    fun deletingArchivedTagsTombstonesOnlyTheExactSpellingAndNeverRevivesThem() {
        val legacy = """{"Alice":"capitalized","alice":"lowercase"}"""
        val store = TestKeyValueStore(mapOf(UserTagKeys.TAGS to legacy))
        val repository = UserTagsRepository(store)
        repository.setTag("alice", "override", UserTagColor.Blue)
        repository.setTag("alice", "")
        assertEquals(mapOf("Alice" to "capitalized"), UserTagsRepository(store).tags())
        assertEquals(setOf("alice"), store.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS))
        repository.setTag("Alice", "")
        assertEquals(emptyMap(), UserTagsRepository(store).tags())
        assertEquals(setOf("Alice", "alice"), store.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS))
        repository.setTag("alice", "readded")
        assertEquals("readded", repository.tagFor("alice"))
        assertEquals(UserTagColor.Theme, repository.colorFor("alice"))
        repository.setTag("alice", null)
        assertEquals("", UserTagsRepository(store).tagFor("alice"))
        assertEquals(legacy, store.getString(UserTagKeys.TAGS))
    }

    @Test
    fun existingReviewedMarkersSuppressArchivesButKeepExplicitReplacements() {
        val store = TestKeyValueStore(mapOf(
            UserTagKeys.TAGS to """{"alice":"archived","bob":"discarded","OtherUser":"retained"}""",
            UserTagKeys.REVIEWED_LEGACY_TAGS to setOf("alice", "bob"),
            UserTagKeys.EXACT_TAGS to """{"ALIce":"resolved earlier","bob":"readded explicitly"}""",
            UserTagKeys.EXACT_COLORS to """{"ALIce":"purple","bob":"blue"}""",
        ))
        val repository = UserTagsRepository(store)
        assertEquals(mapOf("OtherUser" to "retained", "ALIce" to "resolved earlier", "bob" to "readded explicitly"), repository.tags())
        assertEquals("", repository.tagFor("alice"))
        assertEquals(UserTagColor.Purple, repository.colorFor("ALIce"))
        assertEquals(UserTagColor.Blue, repository.colorFor("bob"))
        repository.setTag("bob", "")
        assertEquals("", repository.tagFor("bob"))
        assertEquals(setOf("alice", "bob"), store.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS))
    }

    @Test
    fun archivedDeletionCommitsItsOverlayAndTombstoneTogether() {
        val store = RecordingStore()
        val legacy = """{"alice":"archived","OtherUser":"retained"}"""
        store.putString(UserTagKeys.TAGS, legacy)
        store.putString(UserTagKeys.COLORS, """{"alice":"red"}""")
        val repository = UserTagsRepository(store)
        assertTrue(repository.setTag("alice", ""))
        assertEquals(1, store.commits.size)
        assertEquals(emptyMap(), store.commits.single().tags)
        assertEquals(emptyMap(), store.commits.single().colors)
        assertEquals(setOf("alice"), store.commits.single().removed)
        assertEquals(mapOf("OtherUser" to "retained"), repository.tags())
        assertEquals(legacy, store.getString(UserTagKeys.TAGS))
        assertEquals("""{"alice":"red"}""", store.getString(UserTagKeys.COLORS))
    }

    @Test
    fun textOnlyEditsRetainTheirExistingColorAndThemeRemovalStaysExact() {
        val store = TestKeyValueStore(mapOf(
            UserTagKeys.TAGS to """{"alice":"archived"}""",
            UserTagKeys.COLORS to """{"alice":"red"}""",
        ))
        val repository = UserTagsRepository(store)
        repository.setTag("alice", "edited")
        assertEquals(UserTagColor.Red, repository.colorFor("alice"))
        store.putString(UserTagKeys.COLORS, """{"alice":"blue"}""")
        assertEquals(UserTagColor.Red, repository.colorFor("alice"))
        repository.setTag("Alice", "other", UserTagColor.Green)
        repository.setTag("alice", "edited", UserTagColor.Theme)
        assertEquals(UserTagColor.Theme, repository.colorFor("alice"))
        assertEquals(UserTagColor.Green, repository.colorFor("Alice"))
        repository.setTag("Alice", "")
        repository.setTag("Alice", "new")
        assertEquals(UserTagColor.Theme, repository.colorFor("Alice"))
        assertEquals(emptyMap(), UserTagCodec.decode(store.getString(UserTagKeys.EXACT_COLORS)))
    }

    @Test
    fun malformedLegacySourcesRemainArchivedWhenAnExactTagIsAdded() {
        val store = TestKeyValueStore(mapOf(UserTagKeys.TAGS to "bad legacy tags", UserTagKeys.COLORS to "[1,2]"))
        val repository = UserTagsRepository(store)
        assertEquals(emptyMap(), repository.tags())
        assertTrue(repository.setTag("alice", "new exact", UserTagColor.Red))
        assertEquals("new exact", repository.tagFor("alice"))
        assertEquals("bad legacy tags", store.getString(UserTagKeys.TAGS))
        assertEquals("[1,2]", store.getString(UserTagKeys.COLORS))
    }

    @Test
    fun malformedExactDataRefusesMutationsWithoutErasingAnySource() {
        for (key in listOf(UserTagKeys.EXACT_TAGS, UserTagKeys.EXACT_COLORS)) {
            val store = TestKeyValueStore(mapOf(UserTagKeys.TAGS to """{"alice":"archived"}""", key to "not json"))
            val repository = UserTagsRepository(store)
            assertFalse(repository.setTag("Alice", "new"))
            assertFalse(repository.setTag("alice", ""))
            assertEquals("not json", store.getString(key))
            assertEquals("""{"alice":"archived"}""", store.getString(UserTagKeys.TAGS))
            assertEquals(emptySet(), store.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS))
        }
    }

    @Test
    fun repositoryCachesObserveOtherRepositoriesArchivesOverlaysAndTombstones() {
        val store = TestKeyValueStore()
        val repository = UserTagsRepository(store)
        val other = UserTagsRepository(store)
        store.putString(UserTagKeys.TAGS, """{"Alice":"archived","alice":"lowercase"}""")
        repeat(2) { assertEquals("archived", repository.tagFor("Alice")) }
        other.setTag("Alice", "exact", UserTagColor.Red)
        assertEquals("exact", repository.tagFor("Alice"))
        assertEquals("lowercase", repository.tagFor("alice"))
        store.putString(UserTagKeys.TAGS, """{"Alice":"archive updated","alice":"lowercase updated","bob":"restored"}""")
        assertEquals("exact", repository.tagFor("Alice"))
        assertEquals("lowercase updated", repository.tagFor("alice"))
        assertEquals("restored", repository.tagFor("bob"))
        store.putString(UserTagKeys.EXACT_COLORS, """{"Alice":"blue"}""")
        assertEquals(UserTagColor.Blue, repository.colorFor("Alice"))
        other.setTag("alice", "")
        assertEquals("", repository.tagFor("alice"))
        store.putStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS, setOf("alice", "bob"))
        assertEquals("", repository.tagFor("bob"))
        store.clear()
        assertEquals("", repository.tagFor("Alice"))
        assertEquals(UserTagColor.Theme, repository.colorFor("Alice"))
        assertEquals(emptyMap(), repository.colors())
    }

    @Test
    fun orphanedColorsDoNotAttachToNewTags() {
        val store = TestKeyValueStore(mapOf(
            UserTagKeys.COLORS to """{"Alice":"red"}""",
            UserTagKeys.EXACT_COLORS to """{"OtherUser":"blue"}""",
        ))
        val repository = UserTagsRepository(store)
        repository.setTag("Alice", "new")
        repository.setTag("OtherUser", "another new")
        assertEquals(UserTagColor.Theme, repository.colorFor("Alice"))
        assertEquals(UserTagColor.Theme, repository.colorFor("OtherUser"))
        assertEquals(emptyMap(), UserTagCodec.decode(store.getString(UserTagKeys.EXACT_COLORS)))
    }

    @Test
    fun blankUsernamesDoNotCreateStorageOrModifyArchives() {
        val store = TestKeyValueStore(mapOf(UserTagKeys.TAGS to """{"alice":"archived"}"""))
        val repository = UserTagsRepository(store)
        for (username in listOf(null, "", " \t\n")) {
            assertFalse(repository.setTag(username, "tag", UserTagColor.Red))
            assertEquals("", repository.tagFor(username))
            assertEquals(UserTagColor.Theme, repository.colorFor(username))
        }
        assertFalse(store.contains(UserTagKeys.EXACT_TAGS))
        assertEquals("""{"alice":"archived"}""", store.getString(UserTagKeys.TAGS))
    }

    @Test
    fun codecOnlyMatchesTrimmedExactKeysAndPreservesDistinctUnicodeNames() {
        val serialized = """{" Alice ":"first","Alice":"last exact","ALICE":"upper","alice":"lower","Bob":42,"Σ":"sigma","ς":"final","İ":"dotted","i̇":"lower dotted"}"""
        val decoded = UserTagCodec.decode(serialized)
        for (username in listOf(" Alice ", "ALICE", "alice", "Bob", "Σ", "ς", "İ", "i̇", "missing")) {
            assertEquals(decoded[username.trim()].orEmpty(), UserTagCodec.tagFor(serialized, username))
        }
        assertEquals("last exact", UserTagCodec.tagFor(serialized, "Alice"))
        assertEquals("", UserTagCodec.tagFor(serialized, "AlIce"))
        assertEquals("42", UserTagCodec.tagFor(serialized, "Bob"))
        val edited = UserTagCodec.update(serialized, "Alice", "edited")
        assertEquals("edited", UserTagCodec.tagFor(edited, "Alice"))
        assertEquals("upper", UserTagCodec.tagFor(edited, "ALICE"))
        assertEquals("lower", UserTagCodec.tagFor(edited, "alice"))
        assertEquals("", UserTagCodec.tagFor(UserTagCodec.update(edited, "Alice", ""), "Alice"))
        assertEquals("lower", UserTagCodec.tagFor(UserTagCodec.update(edited, "Alice", ""), "alice"))
    }

    @Test
    fun malformedCodecInputIsReadableAsEmptyButCannotBeSilentlyReplaced() {
        for (serialized in listOf("not json", "[]", """{"alice":"tag",""")) {
            assertEquals("", UserTagCodec.tagFor(serialized, "alice"))
            assertEquals(emptyMap(), UserTagCodec.decode(serialized))
            assertNull(UserTagCodec.update(serialized, "alice", "new"))
        }
        for (username in listOf(null, "", " \t\n")) {
            assertEquals("", UserTagCodec.tagFor("""{"":"empty","alice":"tag"}""", username))
            assertNull(UserTagCodec.update("{}", username, "new"))
        }
    }

    private data class Commit(val tags: Map<String, String>, val colors: Map<String, String>, val removed: Set<String>)

    private class RecordingStore(private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore()) : KeyValueStore by delegate {
        val commits = mutableListOf<Commit>()
        override fun update(block: KeyValueStore.Editor.() -> Unit) {
            delegate.update(block)
            commits += Commit(
                UserTagCodec.decode(getString(UserTagKeys.EXACT_TAGS)),
                UserTagCodec.decode(getString(UserTagKeys.EXACT_COLORS)),
                getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS),
            )
        }
    }
}
