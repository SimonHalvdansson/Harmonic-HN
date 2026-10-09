package com.simon.harmonichackernews.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class DomainSnapshotsTest {
    @Test
    fun idListsAreImmutableCopiesWithNormalListAndSerializationSemantics() {
        for (ids in listOf(intArrayOf(), intArrayOf(500), intArrayOf(500, 501, 500),
            IntArray(64) { Int.MAX_VALUE - it })) {
            val expected = ids.toList()
            val story = Story().apply { id = 1; kids = ids; pollOptionIds = ids }
            val comment = Comment().apply { id = 2; kidsIds = ids }
            val snapshot = story.toSnapshot()
            val commentSnapshot = comment.toSnapshot()
            ids.fill(999)
            assertEquals(expected, snapshot.childIds)
            assertEquals(snapshot.childIds, expected)
            assertEquals(expected, snapshot.pollOptionIds)
            assertEquals(expected, commentSnapshot.childIds)
            assertEquals(expected.hashCode(), snapshot.childIds.hashCode())
            assertEquals(expected.contains(500), snapshot.childIds.contains(500))
            assertEquals(expected.indexOf(500), snapshot.childIds.indexOf(500))
            assertEquals(expected.lastIndexOf(500), snapshot.childIds.lastIndexOf(500))
            val untyped: List<Any?> = snapshot.childIds
            assertFalse(untyped.contains(null))
            assertFalse(untyped.contains(500L))
            val second = Story().apply { kids = expected.toIntArray() }.toSnapshot()
            assertEquals(snapshot.childIds, second.childIds)
            assertFalse(snapshot.childIds == expected + 123)
            assertEquals(snapshot, Json.decodeFromString<StorySnapshot>(Json.encodeToString(snapshot)))
            assertEquals(commentSnapshot,
                Json.decodeFromString<CommentSnapshot>(Json.encodeToString(commentSnapshot)))
            assertEquals(expected, Story().applySnapshot(snapshot).kids?.toList().orEmpty())
        }
    }

    @Test
    fun emptyEnrichmentKeepsTheExactLegacyValuesAndEncoding() {
        val expected = StoryPresentationSnapshot(
            previewTint = ResourceTintSnapshot(0, false),
            faviconTint = ResourceTintSnapshot(0, false),
            rootStory = CommentMasterSnapshot(),
        )
        val actual = Story().presentationSnapshot()
        assertEquals(expected, actual)
        assertEquals(Json.encodeToString(expected), Json.encodeToString(actual))
    }

    @Test
    fun partiallyLoadedResourcesArePreservedAndEarlierSnapshotsRemainUnchanged() {
        val story = Story()
        val empty = story.presentationSnapshot()
        story.previewImageUrl = ""
        story.faviconTintSourceUrl = "favicon"
        story.previewImageTintBaseColor = 123
        story.faviconTintMode = "mode"
        story.rootStoryTitle = "Known title before its root ID"
        val partial = story.presentationSnapshot()
        assertEquals(ResourceLoadSnapshot(url = ""), partial.previewImage)
        assertEquals(ResourceLoadSnapshot(url = "favicon"), partial.favicon)
        assertEquals(ResourceTintSnapshot(0, false, baseColorArgb = 123), partial.previewTint)
        assertEquals(ResourceTintSnapshot(0, false, "favicon", mode = "mode"), partial.faviconTint)
        assertEquals(CommentMasterSnapshot(title = story.rootStoryTitle), partial.rootStory)
        assertEquals(ResourceLoadSnapshot(), empty.previewImage)
        assertEquals(ResourceLoadSnapshot(), empty.favicon)
        assertEquals(ResourceTintSnapshot(0, false), empty.previewTint)
        assertEquals(CommentMasterSnapshot(), empty.rootStory)

        story.previewImageUrlResolved = true
        story.previewImageTintColorLoaded = true
        story.faviconTintColorLoaded = true
        story.rootStoryLoaded = true
        val loaded = story.presentationSnapshot()
        assertEquals(partial.previewImage.copy(loaded = true), loaded.previewImage)
        assertEquals(partial.favicon.copy(loaded = true), loaded.favicon)
        assertEquals(partial.previewTint?.copy(loaded = true), loaded.previewTint)
        assertEquals(partial.faviconTint?.copy(loaded = true), loaded.faviconTint)
        assertEquals(partial.rootStory?.copy(loaded = true), loaded.rootStory)
        assertEquals(loaded, Json.decodeFromString(Json.encodeToString(loaded)))
    }

    @Test
    fun storyDomainAndPresentationStateAreSeparated() {
        val story = Story().apply {
            id = 42
            by = "alice"
            title = "KMP"
            createdAtEpochSeconds = 1_700_000_000
            kids = intArrayOf(1, 2)
            loaded = true
            isRead = true
            previewImageUrl = "https://example.com/image.png"
            previewImageUrlResolved = true
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
        val story = Story().apply { createdAtEpochSeconds = 100 }

        assertEquals("1m", story.formatTime(nowMillis = 160_000))
    }

    @Test
    fun snapshotsRetainImmutablePreviewsWhenStoryEnrichmentIsReplaced() {
        val option = PollOption().apply {
            id = 5
            text = "Kotlin"
            points = 12
            loaded = true
        }
        val repo = RepoInfo(
            name = "harmonic",
            owner = "simon",
            avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
            stars = 99,
        )
        val huggingFace = HuggingFaceModelInfo(
            name = "Kimi-K3",
            logoUrl = "https://huggingface.co/example/logo.png",
            likes = 10_810,
        )
        val openRouter = OpenRouterModelInfo(
            provider = "OpenAI",
            name = "GPT-5.6 Sol",
            contextLength = 1_050_000,
        )
        val story = Story().apply {
            id = 42
            pollOptions = arrayListOf(option)
            gitHubRepoInfo = repo
            huggingFaceInfo = huggingFace
            openRouterInfo = openRouter
        }

        val snapshot = story.presentationSnapshot()
        option.text = "Changed"
        assertSame(repo, snapshot.gitHubRepoInfo)
        assertSame(huggingFace, snapshot.huggingFaceInfo)
        assertSame(openRouter, snapshot.openRouterInfo)
        story.gitHubRepoInfo = repo.copy(name = "changed", avatarUrl = "https://example.com/changed.png")
        story.huggingFaceInfo = huggingFace.copy(name = "changed")
        story.openRouterInfo = openRouter.copy(name = "changed")
        story.pollOptions = null
        story.gitHubRepoInfo = null
        story.huggingFaceInfo = null
        story.openRouterInfo = null

        assertEquals("Kotlin", snapshot.pollOptions.single().text)
        assertEquals("harmonic", snapshot.gitHubRepoInfo?.name)
        assertEquals(
            "https://avatars.githubusercontent.com/u/1?v=4",
            snapshot.gitHubRepoInfo?.avatarUrl,
        )
        assertEquals("Kimi-K3", snapshot.huggingFaceInfo?.name)
        assertEquals(
            "https://huggingface.co/example/logo.png",
            snapshot.huggingFaceInfo?.logoUrl,
        )
        assertEquals("10.8K likes", snapshot.huggingFaceInfo?.formatLikes())
        assertEquals("GPT-5.6 Sol", snapshot.openRouterInfo?.name)
        assertEquals("1.05M context", snapshot.openRouterInfo?.formatContext())
        assertNull(story.gitHubRepoInfo)
    }

    @Test
    fun canonicalPreviewValuesSurvivePresentationSerialization() {
        val story = Story().apply {
            gitHubRepoInfo = RepoInfo(
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
            )
            gitLabInfo = GitLabInfo(
                name = "project",
                namespace = "group/project",
                description = "description",
                website = "https://example.com/project",
                language = "Swift",
                visibility = "public",
                stars = 4,
                forks = 5,
            )
            huggingFaceInfo = HuggingFaceModelInfo(
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
            )
            openRouterInfo = OpenRouterModelInfo(
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
            )
            stackExchangeInfo = StackExchangeInfo(
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
            )
            arxivInfo = ArxivInfo(
                arxivAbstract = "abstract",
                authors = listOf("First Author", null),
                primaryCategory = "cs.SE",
                arxivID = "2608.12345",
                secondaryCategories = listOf("cs.AI", null),
                publishedDate = "2026-08-30",
                htmlUrl = "https://arxiv.org/html/2608.12345",
            )
            wikiInfo = WikipediaInfo(
                title = "Article title",
                summary = "Article summary",
            )
            nitterInfo = NitterInfo(
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
            )
        }

        val snapshot = Json.decodeFromString<StoryPresentationSnapshot>(
            Json.encodeToString(story.presentationSnapshot()),
        )

        assertEquals(
            RepoInfo(
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
            snapshot.gitHubRepoInfo,
        )
        assertEquals(
            GitLabInfo(
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
            HuggingFaceModelInfo(
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
            OpenRouterModelInfo(
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
            StackExchangeInfo(
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
            ArxivInfo(
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
            WikipediaInfo(
                summary = "Article summary",
                title = "Article title",
            ),
            snapshot.wikiInfo,
        )
        assertEquals(
            NitterInfo(
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
    fun presentationDecodesPreviouslySerializedProviderSnapshots() {
        // The old snapshot classes wrote these same field names and array values.
        val json = """
            {
              "clicked": true,
              "summary": "Saved AI summary",
              "commentMaster": {"id": 42, "title": "Root story"},
              "repoInfo": {
                "name": "repo", "owner": "owner", "about": null,
                "website": null, "license": null, "language": "Kotlin",
                "stars": 12, "watching": 0, "forks": 2
              },
              "arxivInfo": {
                "arxivAbstract": "abstract", "authors": ["Author", null],
                "primaryCategory": "cs.SE", "arxivID": "2608.12345",
                "secondaryCategories": [], "publishedDate": "2026-08-30"
              },
              "stackExchangeInfo": {
                "title": "Question", "author": null, "questionText": null,
                "tags": ["kotlin", null], "site": "Stack Overflow",
                "score": 0, "answerCount": 0, "viewCount": 0,
                "isAnswered": false, "hasAcceptedAnswer": false
              }
            }
        """.trimIndent()

        val presentation = Json.decodeFromString<StoryPresentationSnapshot>(json)

        assertEquals(true, presentation.isRead)
        assertEquals("Saved AI summary", presentation.aiSummaryText)
        assertEquals(42, presentation.rootStory?.id)
        assertEquals("Root story", presentation.rootStory?.title)
        val encoded = Json.parseToJsonElement(Json.encodeToString(presentation)).jsonObject
        val legacy = Json.parseToJsonElement(json).jsonObject
        for (key in listOf("clicked", "summary", "commentMaster")) {
            assertEquals(legacy[key], encoded[key])
        }
        assertEquals(setOf("repoInfo"), encoded.keys.intersect(setOf("repoInfo", "gitHubRepoInfo")))
        assertEquals(emptySet(), encoded.keys.intersect(setOf("isRead", "aiSummaryText", "rootStory")))
        assertEquals(
            RepoInfo(name = "repo", owner = "owner", language = "Kotlin", stars = 12, watching = 0, forks = 2),
            presentation.gitHubRepoInfo,
        )
        assertEquals(listOf("Author", null), presentation.arxivInfo?.authors)
        assertEquals(listOf("kotlin", null), presentation.stackExchangeInfo?.tags)
        assertEquals("12 stars", presentation.gitHubRepoInfo?.formatStars())
        assertNull(presentation.arxivInfo?.htmlUrl)
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
