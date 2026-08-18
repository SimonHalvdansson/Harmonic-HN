package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RichLinkPreviewUrlsTest {
    @Test
    fun classifiesEveryNetworkBackedPreviewType() {
        val examples = mapOf(
            LinkPreviewType.GITHUB_REPOSITORY to "https://github.com/salmanzafar949/ctxdiff",
            LinkPreviewType.GITHUB_ISSUE to "https://github.com/BurntSushi/ripgrep/issues/3494",
            LinkPreviewType.GITHUB_PULL_REQUEST to "https://github.com/openjdk/jdk/pull/31120",
            LinkPreviewType.GITHUB_FILE to "https://github.com/FFmpeg/FFmpeg/blob/n9.0/RELEASE_NOTES",
            LinkPreviewType.GITHUB_RELEASE to "https://github.com/astral-sh/uv/releases/tag/0.12.0",
            LinkPreviewType.GITHUB_DISCUSSION to "https://github.com/transmission/transmission/discussions/9031",
            LinkPreviewType.GITLAB_PROJECT to "https://gitlab.com/sifoo/snigl",
            LinkPreviewType.HUGGING_FACE_MODEL to "https://huggingface.co/moonshotai/Kimi-K3",
            LinkPreviewType.HUGGING_FACE_DATASET to "https://huggingface.co/datasets/huggingface/forensic-refusal/blob/main/glm5.2.jsonl",
            LinkPreviewType.HUGGING_FACE_SPACE to "https://huggingface.co/spaces/HuggingFaceCode/in-the-stack",
            LinkPreviewType.HUGGING_FACE_PAPER to "https://huggingface.co/papers/2608.09888",
            LinkPreviewType.HUGGING_FACE_COLLECTION to "https://huggingface.co/collections/Qwen/qwen38",
            LinkPreviewType.OPENROUTER_MODEL to "https://openrouter.ai/openai/gpt-5.6-sol",
            LinkPreviewType.STACK_EXCHANGE to "https://meta.stackexchange.com/questions/333965/example",
            LinkPreviewType.ARXIV to "https://arxiv.org/abs/2501.06425",
            LinkPreviewType.CROSSREF_ARTICLE to "https://doi.org/10.1016/j.watres.2026.125866",
            LinkPreviewType.WIKIPEDIA to "https://en.wikipedia.org/wiki/Hacker_News",
            LinkPreviewType.USGS_EARTHQUAKE to "https://earthquake.usgs.gov/earthquakes/eventpage/us6000tkt2/executive",
            LinkPreviewType.MASTODON_POST to "https://mastodon.gamedev.place/@rygorous/117047697255584965",
            LinkPreviewType.BLUESKY_POST to "https://bsky.app/profile/techprodbangers.bsky.social/post/3mr4askb6tk2i",
            LinkPreviewType.REDDIT_POST to "https://old.reddit.com/r/linux/comments/1vcpk8i/example/",
            LinkPreviewType.NPM_PACKAGE to "https://www.npmjs.com/package/serpapi-byline",
            LinkPreviewType.PYPI_PACKAGE to "https://pypi.org/project/ttsproof/",
            LinkPreviewType.CRATES_PACKAGE to "https://crates.io/crates/sqawk",
            LinkPreviewType.GO_PACKAGE to "https://pkg.go.dev/golang.org/x/tools/go/analysis",
            LinkPreviewType.HOMEBREW_PACKAGE to "https://formulae.brew.sh/formula/fastrace",
            LinkPreviewType.STATUS_PAGE to "https://anthropic.statuspage.io/incidents/kmbpgrsszf72",
            LinkPreviewType.SUBSTACK_ARTICLE to "https://nealstephenson.substack.com/p/writing-by-hand-is-good-for-your",
        )

        examples.forEach { (type, url) ->
            assertEquals(type, RichLinkPreviewUrls.type(url), url)
        }
        assertEquals(
            LinkPreviewType.entries.filterNot { it == LinkPreviewType.TWITTER_X }.toSet(),
            examples.keys,
        )
    }

    @Test
    fun rejectsProviderLandingPagesAndUnrelatedLookalikes() {
        listOf(
            "https://github.com/",
            "https://huggingface.co/datasets",
            "https://pkg.go.dev/",
            "https://substack.com/home",
            "https://reddit.com/r/linux/",
            "not a URL",
        ).forEach { assertNull(RichLinkPreviewUrls.type(it), it) }
    }

    @Test
    fun individualSwitchDisablesOnlyItsType() {
        val enabled = LinkPreviewType.entries.toMutableSet().apply {
            remove(LinkPreviewType.GITHUB_ISSUE)
        }
        val useCase = LinkPreviewUseCase(object : LinkPreviewRepository {
            override suspend fun getArxivInfo(url: String) = error("unused")
            override suspend fun getGitHubInfo(url: String) = error("unused")
            override suspend fun getGitLabInfo(url: String) = error("unused")
            override suspend fun getHuggingFaceInfo(url: String) = error("unused")
            override suspend fun getOpenRouterInfo(url: String) = error("unused")
            override suspend fun getStackExchangeInfo(url: String) = error("unused")
            override suspend fun getWikipediaInfo(url: String) = error("unused")
            override suspend fun getArchiveUrl(url: String) = error("unused")
        })
        val preferences = LinkPreviewPreferences(enabled)

        assertNull(
            useCase.selectProvider(
                "https://github.com/BurntSushi/ripgrep/issues/3494",
                preferences,
            ),
        )
        assertEquals(
            LinkPreviewType.GITHUB_RELEASE,
            useCase.selectProvider(
                "https://github.com/astral-sh/uv/releases/tag/0.12.0",
                preferences,
            ),
        )
        assertFalse(LinkPreviewType.GITHUB_ISSUE in preferences.enabledTypes)
    }
}
