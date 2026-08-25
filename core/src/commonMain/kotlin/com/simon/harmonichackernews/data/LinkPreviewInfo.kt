package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable
enum class LinkPreviewGroup(val label: String) {
    CODE("Code hosting"),
    RESEARCH("Research & knowledge"),
    SOCIAL("Social posts"),
    PACKAGES("Package registries"),
    SERVICES("Services & publishing"),
}

/** Every independently configurable link-preview shape supported by Harmonic. */
@Serializable
enum class LinkPreviewType(
    val title: String,
    val group: LinkPreviewGroup,
    val preferenceKey: String,
    val defaultEnabled: Boolean = true,
) {
    GITHUB_REPOSITORY("GitHub repositories", LinkPreviewGroup.CODE, "pref_link_preview_github"),
    GITHUB_ISSUE("GitHub issues", LinkPreviewGroup.CODE, "pref_link_preview_github_issues"),
    GITHUB_PULL_REQUEST("GitHub pull requests", LinkPreviewGroup.CODE, "pref_link_preview_github_pull_requests"),
    GITHUB_FILE("GitHub files", LinkPreviewGroup.CODE, "pref_link_preview_github_files"),
    GITHUB_RELEASE("GitHub releases", LinkPreviewGroup.CODE, "pref_link_preview_github_releases"),
    GITHUB_DISCUSSION("GitHub discussions", LinkPreviewGroup.CODE, "pref_link_preview_github_discussions"),
    GITLAB_PROJECT("GitLab projects", LinkPreviewGroup.CODE, "pref_link_preview_gitlab"),
    HUGGING_FACE_MODEL("Hugging Face models", LinkPreviewGroup.CODE, "pref_link_preview_hugging_face"),
    HUGGING_FACE_DATASET("Hugging Face datasets", LinkPreviewGroup.CODE, "pref_link_preview_hugging_face_datasets"),
    HUGGING_FACE_SPACE("Hugging Face Spaces", LinkPreviewGroup.CODE, "pref_link_preview_hugging_face_spaces"),
    HUGGING_FACE_PAPER("Hugging Face papers", LinkPreviewGroup.RESEARCH, "pref_link_preview_hugging_face_papers"),
    HUGGING_FACE_COLLECTION("Hugging Face collections", LinkPreviewGroup.CODE, "pref_link_preview_hugging_face_collections"),
    OPENROUTER_MODEL("OpenRouter models", LinkPreviewGroup.CODE, "pref_link_preview_open_router"),
    STACK_EXCHANGE("Stack Exchange questions", LinkPreviewGroup.RESEARCH, "pref_link_preview_stack_exchange"),
    ARXIV("arXiv papers", LinkPreviewGroup.RESEARCH, "pref_link_preview_arxiv"),
    CROSSREF_ARTICLE("Crossref articles", LinkPreviewGroup.RESEARCH, "pref_link_preview_crossref"),
    WIKIPEDIA("Wikipedia articles", LinkPreviewGroup.RESEARCH, "pref_link_preview_wikipedia"),
    USGS_EARTHQUAKE("USGS earthquakes", LinkPreviewGroup.RESEARCH, "pref_link_preview_usgs_earthquakes"),
    MASTODON_POST("Mastodon posts", LinkPreviewGroup.SOCIAL, "pref_link_preview_mastodon"),
    BLUESKY_POST("Bluesky posts", LinkPreviewGroup.SOCIAL, "pref_link_preview_bluesky"),
    REDDIT_POST("Reddit posts", LinkPreviewGroup.SOCIAL, "pref_link_preview_reddit"),
    TWITTER_X("Twitter / X posts", LinkPreviewGroup.SOCIAL, "pref_link_preview_x", defaultEnabled = false),
    NPM_PACKAGE("npm packages", LinkPreviewGroup.PACKAGES, "pref_link_preview_npm"),
    PYPI_PACKAGE("PyPI packages", LinkPreviewGroup.PACKAGES, "pref_link_preview_pypi"),
    CRATES_PACKAGE("crates.io packages", LinkPreviewGroup.PACKAGES, "pref_link_preview_crates"),
    GO_PACKAGE("Go packages", LinkPreviewGroup.PACKAGES, "pref_link_preview_go_packages"),
    HOMEBREW_PACKAGE("Homebrew packages", LinkPreviewGroup.PACKAGES, "pref_link_preview_homebrew"),
    STATUS_PAGE("Status pages", LinkPreviewGroup.SERVICES, "pref_link_preview_status_pages"),
    SUBSTACK_ARTICLE("Substack articles", LinkPreviewGroup.SERVICES, "pref_link_preview_substack"),
}

@Serializable
data class LinkPreviewDetail(
    val label: String,
    val value: String,
    val displayText: String? = null,
)

/** Portable presentation data for providers that do not need a bespoke legacy card. */
@Serializable
data class LinkPreviewInfo(
    val type: LinkPreviewType,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val url: String,
    val details: List<LinkPreviewDetail> = emptyList(),
)

/** Common preview slots shared by mutable stories and immutable presentation snapshots. */
interface LinkPreviewState {
    val repoInfo: Any?
    val gitLabInfo: Any?
    val huggingFaceInfo: Any?
    val openRouterInfo: Any?
    val stackExchangeInfo: Any?
    val arxivInfo: Any?
    val wikiInfo: Any?
    val nitterInfo: Any?
    val linkPreviewInfo: LinkPreviewInfo?
}

fun LinkPreviewState.loadedLinkPreviewType(): LinkPreviewType? = when {
    repoInfo != null -> LinkPreviewType.GITHUB_REPOSITORY
    gitLabInfo != null -> LinkPreviewType.GITLAB_PROJECT
    huggingFaceInfo != null -> LinkPreviewType.HUGGING_FACE_MODEL
    openRouterInfo != null -> LinkPreviewType.OPENROUTER_MODEL
    stackExchangeInfo != null -> LinkPreviewType.STACK_EXCHANGE
    arxivInfo != null -> LinkPreviewType.ARXIV
    wikiInfo != null -> LinkPreviewType.WIKIPEDIA
    nitterInfo != null -> LinkPreviewType.TWITTER_X
    else -> linkPreviewInfo?.type
}
