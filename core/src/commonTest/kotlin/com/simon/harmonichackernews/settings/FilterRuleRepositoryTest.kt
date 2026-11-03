package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.StoryVisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterRuleRepositoryTest {
    @Test
    fun unreadableStructuredRulesNeverReviveOrOverwriteOldRules() {
        for (serialized in listOf("broken", "{\"version\":2,\"rules\":[]}")) {
            val store = TestKeyValueStore(mapOf(
                ContentFilterKeys.WORDS to "AI",
                ContentFilterKeys.DOMAINS to "example.com",
                ContentFilterKeys.TITLE_RULES to serialized,
                ContentFilterKeys.DOMAIN_RULES to serialized,
            ))
            val repository = ContentFilterRepository(store)
            assertEquals(emptyList(), repository.titleRules())
            assertEquals(emptyList(), repository.domainRules())
            assertFalse(repository.canEditTitleRules())
            assertFalse(repository.canEditDomainRules())
            assertFalse(repository.setTitleRules(listOf(TitleFilterRule("new"))))
            assertFalse(repository.setDomainRules(listOf(DomainFilterRule("new.test"))))
            assertEquals(serialized, store.getString(ContentFilterKeys.TITLE_RULES))
            assertEquals(serialized, store.getString(ContentFilterKeys.DOMAIN_RULES))
        }
    }

    @Test
    fun pastedBareUrlCanContainAnotherUrlInItsPathOrQuery() {
        assertEquals("example.com", DomainFilterHostPolicy.normalize("example.com/redirect?next=https://other.test"))
        assertEquals("example.com", DomainFilterHostPolicy.normalize("example.com?next=https://other.test"))
        assertEquals("example.com", DomainFilterHostPolicy.normalize("https://example.com/redirect?next=https://other.test"))
    }

    @Test
    fun releasedSubstringRulesAreReadWithoutWritingOrChangingTheirBehavior() {
        val store = TestKeyValueStore(mapOf(
            ContentFilterKeys.WORDS to " AI, machine learning ",
            ContentFilterKeys.DOMAINS to " example.com, www ",
        ))
        val repository = ContentFilterRepository(store)

        assertEquals(listOf("AI", "machine learning"), repository.titleRules().map { it.text })
        assertTrue(repository.titleRules().all { it.mode == TitleFilterMatchMode.CONTAINS_TEXT })
        assertTrue(repository.domainRules().all { it.legacyContainsText })
        assertNull(store.getString(ContentFilterKeys.TITLE_RULES))
        assertNull(store.getString(ContentFilterKeys.DOMAIN_RULES))
        val policy = StoryVisibilityPolicy()
        policy.update(repository.load(), false)
        assertTrue(policy.shouldHide(Story().apply { title = "A better office chair" }, StoryType.TOP_STORIES))
        assertTrue(policy.shouldHide(Story().apply { url = "https://notexample.com/post" }, StoryType.TOP_STORIES))
        // The released display-domain matcher stripped www.; keep that behavior for legacy rules.
        repository.setDomainRules(listOf(DomainFilterRule("www", legacyContainsText = true)))
        policy.update(repository.load(), false)
        assertFalse(policy.shouldHide(Story().apply { url = "https://www.other.test/post" }, StoryType.TOP_STORIES))
    }

    @Test
    fun addingTypedRulesPreservesUntouchedLegacyModesAndClearingDoesNotReviveOldPreferences() {
        val store = TestKeyValueStore(mapOf(
            ContentFilterKeys.WORDS to "AI",
            ContentFilterKeys.DOMAINS to "example.com",
        ))
        val repository = ContentFilterRepository(store)
        repository.setTitleRules(repository.titleRules() + TitleFilterRule("Kotlin"))
        repository.setDomainRules(repository.domainRules() + DomainFilterRule("news.test", includeSubdomains = false))

        val reopened = ContentFilterRepository(store)
        assertEquals(listOf(TitleFilterMatchMode.CONTAINS_TEXT, TitleFilterMatchMode.WORDS_PHRASE), reopened.titleRules().map { it.mode })
        assertEquals(listOf(true, false), reopened.domainRules().map { it.legacyContainsText })
        assertEquals("AI", store.getString(ContentFilterKeys.WORDS))
        assertEquals("example.com", store.getString(ContentFilterKeys.DOMAINS))

        reopened.setTitleRules(emptyList())
        reopened.setDomainRules(emptyList())
        assertEquals(emptyList(), ContentFilterRepository(store).titleRules())
        assertEquals(emptyList(), ContentFilterRepository(store).domainRules())
    }

    @Test
    fun storyVisibilityUsesTitleModeAndTheActualCanonicalHostname() {
        val repository = ContentFilterRepository(TestKeyValueStore())
        repository.setTitleRules(listOf(TitleFilterRule("AI")))
        val policy = StoryVisibilityPolicy()
        policy.update(repository.load(), false)
        assertTrue(policy.shouldHide(Story().apply { title = "AI tools" }, StoryType.TOP_STORIES))
        assertFalse(policy.shouldHide(Story().apply { title = "A better office chair" }, StoryType.TOP_STORIES))

        repository.setTitleRules(emptyList())
        repository.setDomainRules(listOf(DomainFilterRule("www.example.com", includeSubdomains = false)))
        policy.update(repository.load(), false)
        assertTrue(policy.shouldHide(Story().apply { url = "https://www.example.com:8080/path" }, StoryType.TOP_STORIES))
        assertFalse(policy.shouldHide(Story().apply { url = "https://example.com/path" }, StoryType.TOP_STORIES))
        assertFalse(policy.shouldHide(Story().apply { url = "https://notwww.example.com/path" }, StoryType.TOP_STORIES))

        repository.setDomainRules(listOf(DomainFilterRule("https://BÜCHER.de/read")))
        policy.update(repository.load(), false)
        assertTrue(policy.shouldHide(Story().apply { url = "https://xn--bcher-kva.de/post" }, StoryType.TOP_STORIES))
        assertTrue(policy.shouldHide(Story().apply { url = "https://bücher.de./post" }, StoryType.TOP_STORIES))
        assertFalse(policy.shouldHide(Story().apply { url = "https://notxn--bcher-kva.de/post" }, StoryType.TOP_STORIES))
    }
}
