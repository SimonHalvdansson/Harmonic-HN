package com.simon.harmonichackernews.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

/** Shared orchestration for summaries regardless of which platform presents the result. */
class SummaryUseCase(
    private val repository: CloudSummaryRepository,
) {
    suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> =
        repository.fetchModelIds(baseUrl, apiKey)

    suspend fun extractArticleText(url: String): String = repository.extractMainContent(url)

    fun summarizeText(config: CloudSummaryConfig, text: String?): Flow<CloudSummaryEvent> =
        repository.summarize(config, text)

    fun summarizeArticle(
        config: CloudSummaryConfig,
        articleUrl: String,
    ): Flow<CloudSummaryEvent> = flow {
        val articleText = try {
            repository.extractMainContent(articleUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw SummaryExtractionException(
                "Extraction failed: ${error.message?.takeIf(String::isNotEmpty) ?: "Unknown error"}",
                error,
            )
        }
        emitAll(repository.summarize(config, articleText))
    }
}

class SummaryExtractionException(message: String, cause: Throwable) :
    Exception(message, cause)
