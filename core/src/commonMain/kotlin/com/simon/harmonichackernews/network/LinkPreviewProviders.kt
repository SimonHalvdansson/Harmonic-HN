package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient

/** Recognition and loading live together so every network-backed preview has one owner. */
private data class LinkPreviewProvider(
    val types: Set<LinkPreviewType>,
    val classify: (String) -> LinkPreviewType?,
    val load: suspend (HttpClient, LinkPreviewType, String) -> LinkPreviewData,
)

internal object LinkPreviewProviders {
    /** Twitter/X is rendered by [NitterLinkPreviewRuntime], not by the network preview runtime. */
    val externalTypes: Set<LinkPreviewType> = setOf(LinkPreviewType.TWITTER_X)

    /** List order is URL-classification precedence. */
    private val providers = listOf(
        LinkPreviewProvider(
            types = setOf(
                LinkPreviewType.GITHUB_REPOSITORY,
                LinkPreviewType.GITHUB_ISSUE,
                LinkPreviewType.GITHUB_PULL_REQUEST,
                LinkPreviewType.GITHUB_FILE,
                LinkPreviewType.GITHUB_RELEASE,
                LinkPreviewType.GITHUB_DISCUSSION,
            ),
            classify = { RichLinkPreviewUrls.githubTarget(it)?.type },
            load = { client, type, url ->
                if (type == LinkPreviewType.GITHUB_REPOSITORY) {
                    LinkPreviewData.GitHub(client.loadGitHubInfo(url))
                } else {
                    LinkPreviewData.Rich(client.loadGitHubPreview(type, url))
                }
            },
        ),
        LinkPreviewProvider(
            types = setOf(
                LinkPreviewType.HUGGING_FACE_MODEL,
                LinkPreviewType.HUGGING_FACE_DATASET,
                LinkPreviewType.HUGGING_FACE_SPACE,
                LinkPreviewType.HUGGING_FACE_PAPER,
                LinkPreviewType.HUGGING_FACE_COLLECTION,
            ),
            classify = { RichLinkPreviewUrls.huggingFaceTarget(it)?.type },
            load = { client, type, url ->
                if (type == LinkPreviewType.HUGGING_FACE_MODEL) {
                    LinkPreviewData.HuggingFace(client.loadHuggingFaceInfo(url))
                } else {
                    LinkPreviewData.Rich(client.loadHuggingFacePreview(type, url))
                }
            },
        ),
        LinkPreviewProvider(
            types = setOf(
                LinkPreviewType.NPM_PACKAGE,
                LinkPreviewType.PYPI_PACKAGE,
                LinkPreviewType.CRATES_PACKAGE,
                LinkPreviewType.GO_PACKAGE,
                LinkPreviewType.HOMEBREW_PACKAGE,
            ),
            classify = { RichLinkPreviewUrls.packageTarget(it)?.type },
            load = { client, type, url ->
                LinkPreviewData.Rich(client.loadPackagePreview(type, url))
            },
        ),
        singleTypeProvider(
            LinkPreviewType.GITLAB_PROJECT,
            matches = LinkPreviewUrls::isGitLabUrl,
            load = { client, url -> LinkPreviewData.GitLab(client.loadGitLabInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.OPENROUTER_MODEL,
            matches = LinkPreviewUrls::isOpenRouterUrl,
            load = { client, url -> LinkPreviewData.OpenRouter(client.loadOpenRouterInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.STACK_EXCHANGE,
            matches = LinkPreviewUrls::isStackExchangeUrl,
            load = { client, url ->
                LinkPreviewData.StackExchange(client.loadStackExchangeInfo(url))
            },
        ),
        singleTypeProvider(
            LinkPreviewType.ARXIV,
            matches = LinkPreviewUrls::isArxivUrl,
            load = { client, url -> LinkPreviewData.Arxiv(client.loadArxivInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.WIKIPEDIA,
            matches = LinkPreviewUrls::isWikipediaUrl,
            load = { client, url -> LinkPreviewData.Wikipedia(client.loadWikipediaInfo(url)) },
        ),
        richProvider(
            LinkPreviewType.STATUS_PAGE,
            matches = { RichLinkPreviewUrls.statusPageIncident(it) != null },
            load = { client, url -> client.loadStatusPagePreview(url) },
        ),
        richProvider(
            LinkPreviewType.CROSSREF_ARTICLE,
            matches = { RichLinkPreviewUrls.crossrefDoi(it) != null },
            load = { client, url -> client.loadCrossrefPreview(url) },
        ),
        richProvider(
            LinkPreviewType.USGS_EARTHQUAKE,
            matches = { RichLinkPreviewUrls.usgsEventId(it) != null },
            load = { client, url -> client.loadUsgsPreview(url) },
        ),
        richProvider(
            LinkPreviewType.SUBSTACK_ARTICLE,
            matches = RichLinkPreviewUrls::isSubstackArticle,
            load = { client, url -> client.loadSubstackPreview(url) },
        ),
        richProvider(
            LinkPreviewType.MASTODON_POST,
            matches = { RichLinkPreviewUrls.mastodonStatus(it) != null },
            load = { client, url -> client.loadMastodonPreview(url) },
        ),
        richProvider(
            LinkPreviewType.BLUESKY_POST,
            matches = RichLinkPreviewUrls::isBlueskyPost,
            load = { client, url -> client.loadBlueskyPreview(url) },
        ),
        richProvider(
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

    private val providersByType: Map<LinkPreviewType, LinkPreviewProvider> = buildMap {
        providers.forEach { provider ->
            provider.types.forEach { type ->
                check(type !in externalTypes) { "External preview type $type cannot have a loader" }
                check(put(type, provider) == null) { "Preview type $type has more than one loader" }
            }
        }
    }

    val recognizedTypes: Set<LinkPreviewType> = providersByType.keys

    init {
        val expectedNetworkTypes = LinkPreviewType.entries.toSet() - externalTypes
        check(recognizedTypes == expectedNetworkTypes) {
            "Preview catalog does not cover every network-backed type"
        }
    }

    fun type(url: String?): LinkPreviewType? {
        val candidate = url?.takeIf(String::isNotBlank) ?: return null
        providers.forEach { provider ->
            val type = provider.classify(candidate) ?: return@forEach
            check(type in provider.types) { "Preview classifier returned undeclared type $type" }
            return type
        }
        return null
    }

    suspend fun load(
        client: HttpClient,
        type: LinkPreviewType,
        url: String,
    ): LinkPreviewData {
        if (type in externalTypes) {
            throw LinkPreviewException("${type.title} uses the Nitter web runtime")
        }
        val provider = providersByType[type]
            ?: throw LinkPreviewException("${type.title} has no preview loader")
        return provider.load(client, type, url)
    }

    fun loaderCount(type: LinkPreviewType): Int = providers.count { type in it.types }
}

private fun singleTypeProvider(
    type: LinkPreviewType,
    matches: (String) -> Boolean,
    load: suspend (HttpClient, String) -> LinkPreviewData,
) = LinkPreviewProvider(
    types = setOf(type),
    classify = { url -> type.takeIf { matches(url) } },
    load = { client, _, url -> load(client, url) },
)

private fun richProvider(
    type: LinkPreviewType,
    matches: (String) -> Boolean,
    load: suspend (HttpClient, String) -> LinkPreviewInfo,
) = singleTypeProvider(type, matches) { client, url ->
    LinkPreviewData.Rich(load(client, url))
}
