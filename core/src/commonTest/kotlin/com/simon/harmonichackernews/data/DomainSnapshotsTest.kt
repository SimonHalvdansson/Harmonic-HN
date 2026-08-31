package com.simon.harmonichackernews.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DomainSnapshotsTest {
    @Test
    fun storyDomainAndPresentationStateAreSeparated() {
        val story = Story().apply {
            id = 42
            by = "alice"
            title = "KMP"
            time = 1_700_000_000
            kids = intArrayOf(1, 2)
            loaded = true
            clicked = true
            previewImageUrl = "https://example.com/image.png"
            previewImageUrlLoaded = true
        }

        val domain = story.toSnapshot()
        val presentation = story.presentationSnapshot()

        assertEquals(listOf(1, 2), domain.childIds)
        assertEquals("alice", domain.author)
        assertFalse(Json.encodeToString(domain).contains("previewImage"))
        assertEquals(true, presentation.loaded)
        assertEquals("https://example.com/image.png", presentation.previewImage.url)
    }

    @Test
    fun commentSnapshotRoundTripsWithoutExpansionState() {
        val source = Comment().apply {
            id = 7
            by = "bob"
            text = "Hello"
            expanded = true
            depth = 4
        }

        val restored = Comment().applySnapshot(source.toSnapshot())

        assertEquals(7, restored.id)
        assertEquals("bob", restored.by)
        assertFalse(restored.expanded)
        assertEquals(4, source.presentationSnapshot().depth)
    }

    @Test
    fun relativeTimeFormattingAcceptsAnExplicitClockValue() {
        val story = Story().apply { time = 100 }

        assertEquals("1m", story.formatTime(nowMillis = 160_000))
    }

    @Test
    fun commentsHeaderEnrichmentIsDeepCopiedIntoImmutablePresentation() {
        val option = PollOption().apply {
            id = 5
            text = "Kotlin"
            points = 12
            loaded = true
        }
        val repo = RepoInfo().apply {
            name = "harmonic"
            owner = "simon"
            avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4"
            stars = 99
        }
        val huggingFace = HuggingFaceModelInfo().apply {
            name = "Kimi-K3"
            logoUrl = "https://huggingface.co/example/logo.png"
            likes = 10_810
        }
        val openRouter = OpenRouterModelInfo().apply {
            provider = "OpenAI"
            name = "GPT-5.6 Sol"
            contextLength = 1_050_000
        }
        val story = Story().apply {
            id = 42
            pollOptionArrayList = arrayListOf(option)
            repoInfo = repo
            huggingFaceInfo = huggingFace
            openRouterInfo = openRouter
        }

        val snapshot = story.presentationSnapshot()
        option.text = "Changed"
        repo.name = "changed"
        repo.avatarUrl = "https://example.com/changed.png"
        huggingFace.name = "changed"
        huggingFace.logoUrl = "https://example.com/changed.png"
        openRouter.name = "changed"
        story.pollOptionArrayList = null
        story.repoInfo = null
        story.huggingFaceInfo = null
        story.openRouterInfo = null

        assertEquals("Kotlin", snapshot.pollOptions.single().text)
        assertEquals("harmonic", snapshot.repoInfo?.name)
        assertEquals(
            "https://avatars.githubusercontent.com/u/1?v=4",
            snapshot.repoInfo?.avatarUrl,
        )
        assertEquals("Kimi-K3", snapshot.huggingFaceInfo?.name)
        assertEquals(
            "https://huggingface.co/example/logo.png",
            snapshot.huggingFaceInfo?.logoUrl,
        )
        assertEquals("10.8K likes", snapshot.huggingFaceInfo?.formatLikes())
        assertEquals("GPT-5.6 Sol", snapshot.openRouterInfo?.name)
        assertEquals("1.05M context", snapshot.openRouterInfo?.formatContext())
        assertNull(story.repoInfo)
    }

    @Test
    fun linkPreviewModelsMapEveryFieldIntoNamedSnapshots() {
        val story = Story().apply {
            repoInfo = RepoInfo().apply {
                name = "repo"
                owner = "owner"
                avatarUrl = "https://example.com/avatar.png"
                about = "about"
                website = "https://example.com/repo"
                license = "Apache-2.0"
                language = "Kotlin"
                stars = 1
                watching = 2
                forks = 3
            }
            gitLabInfo = GitLabInfo().apply {
                name = "project"
                namespace = "group/project"
                description = "description"
                website = "https://example.com/project"
                language = "Swift"
                visibility = "public"
                stars = 4
                forks = 5
            }
            huggingFaceInfo = HuggingFaceModelInfo().apply {
                author = "model-author"
                name = "model"
                website = "https://example.com/model"
                logoUrl = "https://example.com/model.png"
                pipelineTag = "text-generation"
                libraryName = "transformers"
                quantization = "Q4"
                licenseName = "mit"
                lastModified = "2026-08-30"
                likes = 6
                downloads = 7
                parameterCount = 8
            }
            openRouterInfo = OpenRouterModelInfo().apply {
                provider = "provider"
                name = "router-model"
                website = "https://example.com/router-model"
                providerIconUrl = "https://example.com/provider.png"
                description = "router description"
                promptPricePerToken = "0.000001"
                completionPricePerToken = "0.000002"
                contextLength = 9
                maxCompletionTokens = 10
                inputModalities = listOf("text", "image")
                outputModalities = listOf("text")
                knowledgeCutoff = "2025-01"
            }
            stackExchangeInfo = StackExchangeInfo().apply {
                title = "Question"
                author = "question-author"
                questionText = "question text"
                tags = arrayOf("kotlin", null)
                site = "Stack Overflow"
                score = 11
                answerCount = 12
                viewCount = 13
                isAnswered = true
                hasAcceptedAnswer = false
            }
            arxivInfo = ArxivInfo().apply {
                arxivAbstract = "abstract"
                authors = arrayOf("First Author", null)
                primaryCategory = "cs.SE"
                arxivID = "2608.12345"
                secondaryCategories = arrayOf("cs.AI", null)
                publishedDate = "2026-08-30"
                htmlUrl = "https://arxiv.org/html/2608.12345"
            }
            wikiInfo = WikipediaInfo().apply {
                title = "Article title"
                summary = "Article summary"
            }
            nitterInfo = NitterInfo().apply {
                text = "post"
                userName = "User"
                userTag = "@user"
                date = "today"
                replyCount = "14"
                reposts = "15"
                likes = "16"
                imgSrc = "https://example.com/post.png"
                hasVideo = true
                beforeUserName = "Quoted User"
                beforeUserTag = "@quoted"
                beforeText = "quoted post"
                beforeDate = "yesterday"
                beforeImgSrc = "https://example.com/quoted.png"
            }
        }

        val snapshot = story.presentationSnapshot()

        assertEquals(
            RepoInfoSnapshot(
                name = "repo",
                owner = "owner",
                avatarUrl = "https://example.com/avatar.png",
                about = "about",
                website = "https://example.com/repo",
                license = "Apache-2.0",
                language = "Kotlin",
                stars = 1,
                watching = 2,
                forks = 3,
            ),
            snapshot.repoInfo,
        )
        assertEquals(
            GitLabInfoSnapshot(
                name = "project",
                namespace = "group/project",
                description = "description",
                website = "https://example.com/project",
                language = "Swift",
                visibility = "public",
                stars = 4,
                forks = 5,
            ),
            snapshot.gitLabInfo,
        )
        assertEquals(
            HuggingFaceModelInfoSnapshot(
                author = "model-author",
                name = "model",
                website = "https://example.com/model",
                logoUrl = "https://example.com/model.png",
                pipelineTag = "text-generation",
                libraryName = "transformers",
                quantization = "Q4",
                licenseName = "mit",
                lastModified = "2026-08-30",
                likes = 6,
                downloads = 7,
                parameterCount = 8,
            ),
            snapshot.huggingFaceInfo,
        )
        assertEquals(
            OpenRouterModelInfoSnapshot(
                provider = "provider",
                name = "router-model",
                website = "https://example.com/router-model",
                providerIconUrl = "https://example.com/provider.png",
                description = "router description",
                promptPricePerToken = "0.000001",
                completionPricePerToken = "0.000002",
                contextLength = 9,
                maxCompletionTokens = 10,
                inputModalities = listOf("text", "image"),
                outputModalities = listOf("text"),
                knowledgeCutoff = "2025-01",
            ),
            snapshot.openRouterInfo,
        )
        assertEquals(
            StackExchangeInfoSnapshot(
                title = "Question",
                author = "question-author",
                questionText = "question text",
                tags = listOf("kotlin", null),
                site = "Stack Overflow",
                score = 11,
                answerCount = 12,
                viewCount = 13,
                isAnswered = true,
                hasAcceptedAnswer = false,
            ),
            snapshot.stackExchangeInfo,
        )
        assertEquals(
            ArxivInfoSnapshot(
                arxivAbstract = "abstract",
                authors = listOf("First Author", null),
                primaryCategory = "cs.SE",
                arxivID = "2608.12345",
                secondaryCategories = listOf("cs.AI", null),
                publishedDate = "2026-08-30",
                htmlUrl = "https://arxiv.org/html/2608.12345",
            ),
            snapshot.arxivInfo,
        )
        assertEquals(
            WikipediaInfoSnapshot(
                summary = "Article summary",
                title = "Article title",
            ),
            snapshot.wikiInfo,
        )
        assertEquals(
            NitterInfoSnapshot(
                text = "post",
                userName = "User",
                userTag = "@user",
                date = "today",
                replyCount = "14",
                reposts = "15",
                likes = "16",
                imgSrc = "https://example.com/post.png",
                hasVideo = true,
                beforeUserName = "Quoted User",
                beforeUserTag = "@quoted",
                beforeText = "quoted post",
                beforeDate = "yesterday",
                beforeImgSrc = "https://example.com/quoted.png",
            ),
            snapshot.nitterInfo,
        )
    }

    @Test
    fun loadedPreviewTypeIsSharedByMutableStoriesAndSnapshots() {
        val story = Story().apply {
            linkPreviewInfo = LinkPreviewInfo(
                type = LinkPreviewType.GITHUB_RELEASE,
                title = "octo / project",
                url = "https://github.com/octo/project/releases/tag/v1",
            )
        }

        assertEquals(LinkPreviewType.GITHUB_RELEASE, story.loadedLinkPreviewType())
        assertEquals(
            LinkPreviewType.GITHUB_RELEASE,
            story.presentationSnapshot().loadedLinkPreviewType(),
        )
    }
}
