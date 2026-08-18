package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.HuggingFaceModelInfo
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.OpenRouterModelInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.WikipediaInfo

data class LinkPreviewPreferences(
    val enabledTypes: Set<LinkPreviewType>,
) {
    fun isEnabled(type: LinkPreviewType): Boolean = type in enabledTypes
}

sealed interface LinkPreviewData {
    val type: LinkPreviewType

    data class Arxiv(val value: ArxivInfo) : LinkPreviewData {
        override val type = LinkPreviewType.ARXIV
    }

    data class GitHub(val value: RepoInfo) : LinkPreviewData {
        override val type = LinkPreviewType.GITHUB_REPOSITORY
    }

    data class GitLab(val value: GitLabInfo) : LinkPreviewData {
        override val type = LinkPreviewType.GITLAB_PROJECT
    }

    data class HuggingFace(val value: HuggingFaceModelInfo) : LinkPreviewData {
        override val type = LinkPreviewType.HUGGING_FACE_MODEL
    }

    data class OpenRouter(val value: OpenRouterModelInfo) : LinkPreviewData {
        override val type = LinkPreviewType.OPENROUTER_MODEL
    }

    data class StackExchange(val value: StackExchangeInfo) : LinkPreviewData {
        override val type = LinkPreviewType.STACK_EXCHANGE
    }

    data class Wikipedia(val value: WikipediaInfo) : LinkPreviewData {
        override val type = LinkPreviewType.WIKIPEDIA
    }

    data class Rich(val value: LinkPreviewInfo) : LinkPreviewData {
        override val type get() = value.type
    }
}

fun LinkPreviewData.applyTo(story: Story) {
    when (this) {
        is LinkPreviewData.Arxiv -> story.arxivInfo = value
        is LinkPreviewData.GitHub -> story.repoInfo = value
        is LinkPreviewData.GitLab -> story.gitLabInfo = value
        is LinkPreviewData.HuggingFace -> story.huggingFaceInfo = value
        is LinkPreviewData.OpenRouter -> story.openRouterInfo = value
        is LinkPreviewData.StackExchange -> story.stackExchangeInfo = value
        is LinkPreviewData.Wikipedia -> story.wikiInfo = value
        is LinkPreviewData.Rich -> story.linkPreviewInfo = value
    }
}

/** Selects and loads provider-specific previews without platform UI or lifecycle dependencies. */
class LinkPreviewUseCase(private val repository: LinkPreviewRepository) {
    fun selectProvider(url: String?, preferences: LinkPreviewPreferences): LinkPreviewType? =
        RichLinkPreviewUrls.type(url)?.takeIf(preferences::isEnabled)

    suspend fun load(type: LinkPreviewType, url: String): LinkPreviewData = when (type) {
        LinkPreviewType.ARXIV -> LinkPreviewData.Arxiv(repository.getArxivInfo(url))
        LinkPreviewType.GITHUB_REPOSITORY -> LinkPreviewData.GitHub(repository.getGitHubInfo(url))
        LinkPreviewType.GITLAB_PROJECT -> LinkPreviewData.GitLab(repository.getGitLabInfo(url))
        LinkPreviewType.HUGGING_FACE_MODEL ->
            LinkPreviewData.HuggingFace(repository.getHuggingFaceInfo(url))
        LinkPreviewType.OPENROUTER_MODEL ->
            LinkPreviewData.OpenRouter(repository.getOpenRouterInfo(url))
        LinkPreviewType.STACK_EXCHANGE ->
            LinkPreviewData.StackExchange(repository.getStackExchangeInfo(url))
        LinkPreviewType.WIKIPEDIA -> LinkPreviewData.Wikipedia(repository.getWikipediaInfo(url))
        LinkPreviewType.TWITTER_X ->
            throw LinkPreviewException("Twitter / X previews use the Nitter web runtime")
        else -> LinkPreviewData.Rich(repository.getRichInfo(type, url))
    }
}
