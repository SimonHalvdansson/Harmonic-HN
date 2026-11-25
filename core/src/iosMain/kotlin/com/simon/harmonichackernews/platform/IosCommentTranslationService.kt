package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.utils.TranslatableComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosCommentTranslationRequest {
    fun cancel()
}

interface IosCommentTranslationCallback {
    fun translated(segments: List<String>)
    fun sameLanguage()
    fun unsupported()
    fun failed(message: String)
}

interface IosCommentTranslationBridge {
    suspend fun languageOptions(): TranslationLanguages
    fun start(sample: String, segments: List<String>, target: String, callback: IosCommentTranslationCallback): IosCommentTranslationRequest
}

internal class IosCommentTranslationService(private val bridge: IosCommentTranslationBridge) : CommentTranslationService {
    override suspend fun languageOptions() = bridge.languageOptions()

    override suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult = withContext(Dispatchers.Default) {
        val document = TranslatableComment(html)
        val segments = document.segments
        if (segments.isEmpty()) return@withContext CommentTranslationResult.Unsupported

        var translated: List<String>? = null
        val notice = suspendCancellableCoroutine<CommentTranslationResult?> { continuation ->
            val request = bridge.start(document.languageSample, segments, targetLanguage, object : IosCommentTranslationCallback {
                override fun translated(segments: List<String>) {
                    translated = segments
                    continuation.resume(null)
                }
                override fun sameLanguage() = continuation.resume(CommentTranslationResult.SameLanguage)
                override fun unsupported() = continuation.resume(CommentTranslationResult.Unsupported)
                override fun failed(message: String) = continuation.resumeWithException(IllegalStateException(message))
            })
            continuation.invokeOnCancellation { request.cancel() }
        }
        notice ?: run {
            val result = checkNotNull(translated)
            check(result.size == segments.size) { "Incomplete comment translation" }
            val replacements = result.iterator()
            CommentTranslationResult.Translated(document.translate { replacements.next() })
        }
    }
}
