package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.WikipediaInfo

data class LinkPreviewPreferences(
    val arxiv: Boolean,
    val github: Boolean,
    val gitLab: Boolean,
    val stackExchange: Boolean,
    val wikipedia: Boolean,
)

enum class LinkPreviewProvider {
    ARXIV,
    GITHUB,
    GITLAB,
    STACK_EXCHANGE,
    WIKIPEDIA,
}

sealed interface LinkPreviewData {
    data class Arxiv(val value: ArxivInfo) : LinkPreviewData
    data class GitHub(val value: RepoInfo) : LinkPreviewData
    data class GitLab(val value: GitLabInfo) : LinkPreviewData
    data class StackExchange(val value: StackExchangeInfo) : LinkPreviewData
    data class Wikipedia(val value: WikipediaInfo) : LinkPreviewData
}

/** Selects and loads provider-specific previews without platform UI or lifecycle dependencies. */
class LinkPreviewUseCase(private val repository: LinkPreviewRepository) {
    fun selectProvider(url: String?, preferences: LinkPreviewPreferences): LinkPreviewProvider? =
        when {
            preferences.arxiv && LinkPreviewUrls.isArxivUrl(url) -> LinkPreviewProvider.ARXIV
            preferences.github && LinkPreviewUrls.isGitHubUrl(url) -> LinkPreviewProvider.GITHUB
            preferences.gitLab && LinkPreviewUrls.isGitLabUrl(url) -> LinkPreviewProvider.GITLAB
            preferences.stackExchange && LinkPreviewUrls.isStackExchangeUrl(url) ->
                LinkPreviewProvider.STACK_EXCHANGE
            preferences.wikipedia && LinkPreviewUrls.isWikipediaUrl(url) ->
                LinkPreviewProvider.WIKIPEDIA
            else -> null
        }

    suspend fun load(provider: LinkPreviewProvider, url: String): LinkPreviewData = when (provider) {
        LinkPreviewProvider.ARXIV -> LinkPreviewData.Arxiv(repository.getArxivInfo(url))
        LinkPreviewProvider.GITHUB -> LinkPreviewData.GitHub(repository.getGitHubInfo(url))
        LinkPreviewProvider.GITLAB -> LinkPreviewData.GitLab(repository.getGitLabInfo(url))
        LinkPreviewProvider.STACK_EXCHANGE ->
            LinkPreviewData.StackExchange(repository.getStackExchangeInfo(url))
        LinkPreviewProvider.WIKIPEDIA -> LinkPreviewData.Wikipedia(repository.getWikipediaInfo(url))
    }
}
