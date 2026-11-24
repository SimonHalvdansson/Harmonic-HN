package com.simon.harmonichackernews.platform

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.simon.harmonichackernews.utils.TranslatableComment
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

internal val androidCommentTranslation: CommentTranslationService = object : CommentTranslationService {
    override val defaultLanguage: String
        get() = TranslateLanguage.fromLanguageTag(Locale.getDefault().toLanguageTag()) ?: TranslateLanguage.ENGLISH

    override val languages: Map<String, String>
        get() = TranslateLanguage.getAllLanguages().associateWith { Locale.forLanguageTag(it).displayLanguage }

    override suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult = withContext(Dispatchers.IO) {
        val document = TranslatableComment(html)
        val sample = document.languageSample
        if (sample.isBlank()) return@withContext CommentTranslationResult.Unsupported
        val detected = LanguageIdentification.getClient().use { identifier ->
            runInterruptible { Tasks.await(identifier.identifyLanguage(sample)) }
        }
        val source = TranslateLanguage.fromLanguageTag(detected) ?: return@withContext CommentTranslationResult.Unsupported
        if (source == targetLanguage) return@withContext CommentTranslationResult.SameLanguage
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(targetLanguage).build()
        Translation.getClient(options).use { translator ->
            runInterruptible { Tasks.await(translator.downloadModelIfNeeded()) }
            CommentTranslationResult.Translated(document.translate { text ->
                runInterruptible { Tasks.await(translator.translate(text)) }
            })
        }
    }
}
