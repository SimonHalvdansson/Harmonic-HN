package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.platform.TranslationLanguages
import com.simon.harmonichackernews.platform.CommentTranslationService
import com.simon.harmonichackernews.platform.CommentTranslationResult
import com.simon.harmonichackernews.settings.CommentTranslationMode
import com.simon.harmonichackernews.settings.CommentTranslationPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentTranslationsTest {
    @Test fun hiddenOriginalOnlyAppliesToPendingAutomaticTranslations() {
        val automatic = CommentTranslationPreferences(enabled = true, mode = CommentTranslationMode.Automatic)
        assertEquals(false, automatic.waitingToShowTranslation(null))
        val hidden = automatic.copy(showOriginalWhileTranslating = false)
        assertEquals(true, hidden.waitingToShowTranslation(null))
        assertEquals(true, hidden.waitingToShowTranslation(TranslationDisplay(loading = true)))
        assertEquals(false, hidden.waitingToShowTranslation(TranslationDisplay(notice = "Language unavailable")))
        assertEquals(false, hidden.waitingToShowTranslation(TranslationDisplay(translated = "Bonjour", showOriginal = true)))
        for (mode in listOf(CommentTranslationMode.Button, CommentTranslationMode.Details)) {
            assertEquals(false, hidden.copy(mode = mode).waitingToShowTranslation(TranslationDisplay(loading = true)))
        }
        assertEquals(false, hidden.copy(enabled = false).waitingToShowTranslation(null))
    }

    @Test fun rowAndDialogShareOneRequestAndKeepExplicitOriginal() = runTest {
        val translations = CommentTranslations()
        val key = TranslationKey(1, "Hello", "fr")
        val response = CompletableDeferred<Unit>()
        var calls = 0
        val service = object : CommentTranslationService {
            override suspend fun languageOptions() = TranslationLanguages("fr", mapOf("fr" to "French"))
            override suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult {
                calls++
                response.await()
                return CommentTranslationResult.Translated("Bonjour")
            }
        }
        val interrupted = launch(start = CoroutineStart.UNDISPATCHED) { translations.toggle(key, service) }
        translations.toggle(key, service)
        assertEquals(1, calls)
        interrupted.cancelAndJoin()
        assertNull(translations[key])

        response.complete(Unit)
        translations.toggle(key, service)
        assertEquals("Bonjour", translations[key]?.visibleHtml)
        translations.toggle(key, service)
        assertEquals(true, translations[key]?.showOriginal)
        assertNull(translations[key]?.visibleHtml)
        assertNull(translations[key.copy(html = "Edited comment")])
        assertNull(translations[key.copy(language = "sv")])
        translations.toggle(key, service)
        assertEquals("Bonjour", translations[key]?.visibleHtml)
        assertEquals(2, calls)
    }
}
