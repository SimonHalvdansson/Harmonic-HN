@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

// Dispatch from 1bfa9b10 retained exactly, including classification order and checks.
package com.simon.harmonichackernews.benchmark

import com.simon.harmonichackernews.network.*

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient

/** Recognition and loading live together so every network-backed preview has one owner. */
private data class LinkPreviewProvider(
    val types: Set<LinkPreviewType>,
    val classify: (NetworkUrl) -> LinkPreviewType?,
    val load: suspend (HttpClient, LinkPreviewType, String, GitHubPreviewLoader) -> LinkPreviewData,
)

internal object LegacyLinkPreviewProviders {
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
            classify = { GitHubLinkPreview.githubTarget(it)?.type },
            load = { client, type, url, github -> github.load(client, type, url) },
        ),
        LinkPreviewProvider(
            types = setOf(
                LinkPreviewType.HUGGING_FACE_MODEL,
                LinkPreviewType.HUGGING_FACE_DATASET,
                LinkPreviewType.HUGGING_FACE_SPACE,
                LinkPreviewType.HUGGING_FACE_PAPER,
                LinkPreviewType.HUGGING_FACE_COLLECTION,
            ),
            classify = { HuggingFaceLinkPreview.huggingFaceTarget(it)?.type },
            load = { client, type, url, _ ->
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
            classify = { legacyPackageTarget(it)?.type },
            load = { client, type, url, _ ->
                LinkPreviewData.Rich(client.loadPackagePreview(type, url))
            },
        ),
        singleTypeProvider(
            LinkPreviewType.GITLAB_PROJECT,
            matches = { GitLabLinkPreview.gitLabProjectPath(it) != null },
            load = { client, url -> LinkPreviewData.GitLab(client.loadGitLabInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.OPENROUTER_MODEL,
            matches = { OpenRouterLinkPreview.openRouterModel(it) != null },
            load = { client, url -> LinkPreviewData.OpenRouter(client.loadOpenRouterInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.STACK_EXCHANGE,
            matches = { StackExchangeLinkPreview.stackExchangeRequest(it) != null },
            load = { client, url ->
                LinkPreviewData.StackExchange(client.loadStackExchangeInfo(url))
            },
        ),
        singleTypeProvider(
            LinkPreviewType.ARXIV,
            matches = { ArxivLinkPreview.arxivId(it) != null },
            load = { client, url -> LinkPreviewData.Arxiv(client.loadArxivInfo(url)) },
        ),
        singleTypeProvider(
            LinkPreviewType.WIKIPEDIA,
            matches = { WikipediaLinkPreview.wikipediaTitle(it) != null },
            load = { client, url -> LinkPreviewData.Wikipedia(client.loadWikipediaInfo(url)) },
        ),
        richProvider(
            LinkPreviewType.STATUS_PAGE,
            matches = { legacyStatusPageIncident(it) != null },
            load = { client, url -> client.loadStatusPagePreview(url) },
        ),
        richProvider(
            LinkPreviewType.CROSSREF_ARTICLE,
            matches = { legacyCrossrefDoi(it) != null },
            load = { client, url -> client.loadCrossrefPreview(url) },
        ),
        richProvider(
            LinkPreviewType.USGS_EARTHQUAKE,
            matches = { UsgsLinkPreview.usgsEventId(it) != null },
            load = { client, url -> client.loadUsgsPreview(url) },
        ),
        richProvider(
            LinkPreviewType.SUBSTACK_ARTICLE,
            matches = ::legacySubstackArticle,
            load = { client, url -> client.loadSubstackPreview(url) },
        ),
        richProvider(
            LinkPreviewType.MASTODON_POST,
            matches = { MastodonLinkPreview.mastodonStatus(it) != null },
            load = { client, url -> client.loadMastodonPreview(url) },
        ),
        richProvider(
            LinkPreviewType.BLUESKY_POST,
            matches = ::legacyBlueskyPost,
            load = { client, url -> client.loadBlueskyPreview(url) },
        ),
        richProvider(
            LinkPreviewType.REDDIT_POST,
            matches = ::legacyRedditPost,
            load = { client, url -> client.loadRedditPreview(url) },
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
        val parsed = candidate.toNetworkUrlOrNull() ?: return null
        providers.forEach { provider ->
            val type = provider.classify(parsed) ?: return@forEach
            check(type in provider.types) { "Preview classifier returned undeclared type $type" }
            return type
        }
        return null
    }

    suspend fun load(
        client: HttpClient,
        type: LinkPreviewType,
        url: String,
        github: GitHubPreviewLoader,
    ): LinkPreviewData {
        if (type in externalTypes) {
            throw LinkPreviewException("${type.title} uses the Nitter web runtime")
        }
        val provider = providersByType[type]
            ?: throw LinkPreviewException("${type.title} has no preview loader")
        return provider.load(client, type, url, github)
    }

    fun loaderCount(type: LinkPreviewType): Int = providers.count { type in it.types }
}

private fun singleTypeProvider(
    type: LinkPreviewType,
    matches: (NetworkUrl) -> Boolean,
    load: suspend (HttpClient, String) -> LinkPreviewData,
) = LinkPreviewProvider(
    types = setOf(type),
    classify = { url -> type.takeIf { matches(url) } },
    load = { client, _, url, _ -> load(client, url) },
)

private fun richProvider(
    type: LinkPreviewType,
    matches: (NetworkUrl) -> Boolean,
    load: suspend (HttpClient, String) -> LinkPreviewInfo,
) = singleTypeProvider(type, matches) { client, url ->
    LinkPreviewData.Rich(load(client, url))
}

private data class LegacyPackagePreviewTarget(
    val type: LinkPreviewType,
    val name: String,
    val variant: String? = null,
)

private fun legacyPackageTarget(parsed: NetworkUrl): LegacyPackagePreviewTarget? {
    val host = parsed.host.lowercase().removePrefix("www.")
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    return when {
        host == "npmjs.com" && segments.firstOrNull() == "package" && segments.size >= 2 ->
            LegacyPackagePreviewTarget(
                LinkPreviewType.NPM_PACKAGE,
                if (segments[1].startsWith('@')) {
                    if (segments.size < 3) return null
                    segments.subList(1, 3).joinToString("/")
                } else segments[1],
            )
        host == "pypi.org" && segments.firstOrNull() == "project" && segments.size >= 2 ->
            LegacyPackagePreviewTarget(LinkPreviewType.PYPI_PACKAGE, segments[1])
        host == "crates.io" && segments.firstOrNull() == "crates" && segments.size >= 2 ->
            LegacyPackagePreviewTarget(LinkPreviewType.CRATES_PACKAGE, segments[1])
        host == "pkg.go.dev" && segments.isNotEmpty() && segments.first() != "vuln" ->
            LegacyPackagePreviewTarget(LinkPreviewType.GO_PACKAGE, segments.joinToString("/"))
        host == "formulae.brew.sh" && segments.size >= 2 &&
            segments[0] in setOf("formula", "cask") -> LegacyPackagePreviewTarget(
                LinkPreviewType.HOMEBREW_PACKAGE, segments[1], variant = segments[0],
            )
        else -> null
    }
}

private fun legacyStatusPageIncident(parsed: NetworkUrl): Pair<String, String>? {
    val host = parsed.host.lowercase()
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    if (!host.endsWith(".statuspage.io") || segments.size < 2 || segments[0] != "incidents") return null
    return host to segments[1]
}

private fun legacyCrossrefDoi(parsed: NetworkUrl): String? {
    val host = parsed.host.lowercase().removePrefix("www.")
    if (host !in setOf("doi.org", "dx.doi.org")) return null
    return parsed.pathSegments.filter(String::isNotEmpty).joinToString("/").takeIf(String::isNotEmpty)
}

private fun legacySubstackArticle(parsed: NetworkUrl): Boolean {
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    return parsed.host.lowercase().endsWith(".substack.com") && segments.size >= 2 && segments[0] == "p"
}

private fun legacyBlueskyPost(parsed: NetworkUrl): Boolean {
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    return parsed.host.lowercase() == "bsky.app" && segments.size >= 4 &&
        segments[0] == "profile" && segments[2] == "post"
}

private fun legacyRedditPost(parsed: NetworkUrl): Boolean {
    val host = parsed.host.lowercase().removePrefix("www.").removePrefix("old.")
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    return host == "reddit.com" && "comments" in segments
}
