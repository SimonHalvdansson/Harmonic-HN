package com.simon.harmonichackernews.platform

interface CommentTranslationService {
    val defaultLanguage: String
    val languages: Map<String, String>
    suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult
}

sealed interface CommentTranslationResult {
    data class Translated(val html: String) : CommentTranslationResult
    data object SameLanguage : CommentTranslationResult
    data object Unsupported : CommentTranslationResult
}
