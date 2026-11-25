package com.simon.harmonichackernews.platform

interface CommentTranslationService {
    val requiresGoogleAttribution: Boolean get() = false
    suspend fun languageOptions(): TranslationLanguages
    suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult
}

data class TranslationLanguages(val defaultLanguage: String, val names: Map<String, String>)

sealed interface CommentTranslationResult {
    data class Translated(val html: String) : CommentTranslationResult
    data object SameLanguage : CommentTranslationResult
    data object Unsupported : CommentTranslationResult
}
