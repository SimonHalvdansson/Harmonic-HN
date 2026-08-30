package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient

/**
 * Keeps URL recognition and network dispatch together for each rich-preview provider family.
 * Some recognized types retain their older dedicated repository loader; those providers advertise
 * the type here but deliberately omit it from [loadableTypes].
 */
private data class RichLinkPreviewProvider(
    val recognizedTypes: Set<LinkPreviewType>,
    val classify: (String) -> LinkPreviewType?,
    val loadableTypes: Set<LinkPreviewType> = recognizedTypes,
    val load: (suspend (HttpClient, LinkPreviewType, String) -> LinkPreviewInfo)? = null,
)

internal object RichLinkPreviewProviders {
    private val providers = listOf(
        RichLinkPreviewProvider(
            recognizedTypes = setOf(
                LinkPreviewType.GITHUB_REPOSITORY,
                LinkPreviewType.GITHUB_ISSUE,
                LinkPreviewType.GITHUB_PULL_REQUEST,
                LinkPreviewType.GITHUB_FILE,
                LinkPreviewType.GITHUB_RELEASE,
                LinkPreviewType.GITHUB_DISCUSSION,
            ),
            classify = { RichLinkPreviewUrls.githubTarget(it)?.type },
            loadableTypes = setOf(
                LinkPreviewType.GITHUB_ISSUE,
                LinkPreviewType.GITHUB_PULL_REQUEST,
                LinkPreviewType.GITHUB_FILE,
                LinkPreviewType.GITHUB_RELEASE,
                LinkPreviewType.GITHUB_DISCUSSION,
            ),
            load = { client, type, url -> client.loadGitHubPreview(type, url) },
        ),
        RichLinkPreviewProvider(
            recognizedTypes = setOf(
                LinkPreviewType.HUGGING_FACE_MODEL,
                LinkPreviewType.HUGGING_FACE_DATASET,
                LinkPreviewType.HUGGING_FACE_SPACE,
                LinkPreviewType.HUGGING_FACE_PAPER,
                LinkPreviewType.HUGGING_FACE_COLLECTION,
            ),
            classify = { RichLinkPreviewUrls.huggingFaceTarget(it)?.type },
            loadableTypes = setOf(
                LinkPreviewType.HUGGING_FACE_DATASET,
                LinkPreviewType.HUGGING_FACE_SPACE,
                LinkPreviewType.HUGGING_FACE_PAPER,
                LinkPreviewType.HUGGING_FACE_COLLECTION,
            ),
            load = { client, type, url -> client.loadHuggingFacePreview(type, url) },
        ),
        RichLinkPreviewProvider(
            recognizedTypes = setOf(
                LinkPreviewType.NPM_PACKAGE,
                LinkPreviewType.PYPI_PACKAGE,
                LinkPreviewType.CRATES_PACKAGE,
                LinkPreviewType.GO_PACKAGE,
                LinkPreviewType.HOMEBREW_PACKAGE,
            ),
            classify = { RichLinkPreviewUrls.packageTarget(it)?.type },
            load = { client, type, url -> client.loadPackagePreview(type, url) },
        ),
        RichLinkPreviewProvider(
            recognizedTypes = setOf(
                LinkPreviewType.GITLAB_PROJECT,
                LinkPreviewType.OPENROUTER_MODEL,
                LinkPreviewType.STACK_EXCHANGE,
                LinkPreviewType.ARXIV,
                LinkPreviewType.WIKIPEDIA,
            ),
            classify = { url ->
                when {
                    LinkPreviewUrls.isGitLabUrl(url) -> LinkPreviewType.GITLAB_PROJECT
                    LinkPreviewUrls.isOpenRouterUrl(url) -> LinkPreviewType.OPENROUTER_MODEL
                    LinkPreviewUrls.isStackExchangeUrl(url) -> LinkPreviewType.STACK_EXCHANGE
                    LinkPreviewUrls.isArxivUrl(url) -> LinkPreviewType.ARXIV
                    LinkPreviewUrls.isWikipediaUrl(url) -> LinkPreviewType.WIKIPEDIA
                    else -> null
                }
            },
        ),
        singleTypeProvider(
            LinkPreviewType.STATUS_PAGE,
            matches = { RichLinkPreviewUrls.statusPageIncident(it) != null },
            load = { client, url -> client.loadStatusPagePreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.CROSSREF_ARTICLE,
            matches = { RichLinkPreviewUrls.crossrefDoi(it) != null },
            load = { client, url -> client.loadCrossrefPreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.USGS_EARTHQUAKE,
            matches = { RichLinkPreviewUrls.usgsEventId(it) != null },
            load = { client, url -> client.loadUsgsPreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.SUBSTACK_ARTICLE,
            matches = RichLinkPreviewUrls::isSubstackArticle,
            load = { client, url -> client.loadSubstackPreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.MASTODON_POST,
            matches = { RichLinkPreviewUrls.mastodonStatus(it) != null },
            load = { client, url -> client.loadMastodonPreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.BLUESKY_POST,
            matches = RichLinkPreviewUrls::isBlueskyPost,
            load = { client, url -> client.loadBlueskyPreview(url) },
        ),
        singleTypeProvider(
            LinkPreviewType.REDDIT_POST,
            matches = RichLinkPreviewUrls::isRedditPost,
            load = { client, url ->
                client.loadOEmbedPreview(
                    LinkPreviewType.REDDIT_POST,
                    "https://www.reddit.com/oembed",
                    url,
                )
            },
        ),
    )

    fun type(url: String?): LinkPreviewType? {
        val candidate = url?.takeIf(String::isNotBlank) ?: return null
        providers.forEach { provider ->
            provider.classify(candidate)?.let { return it }
        }
        return null
    }

    suspend fun load(
        client: HttpClient,
        type: LinkPreviewType,
        url: String,
    ): LinkPreviewInfo {
        val provider = providers.firstOrNull { type in it.recognizedTypes }
            ?: throw LinkPreviewException("${type.title} uses a dedicated preview loader")
        if (type !in provider.loadableTypes) {
            throw LinkPreviewException("${type.title} uses a dedicated preview loader")
        }
        val loader = provider.load
            ?: throw LinkPreviewException("${type.title} uses a dedicated preview loader")
        return loader(client, type, url)
    }
}

private fun singleTypeProvider(
    type: LinkPreviewType,
    matches: (String) -> Boolean,
    load: suspend (HttpClient, String) -> LinkPreviewInfo,
) = RichLinkPreviewProvider(
    recognizedTypes = setOf(type),
    classify = { url -> type.takeIf { matches(url) } },
    load = { client, _, url -> load(client, url) },
)
