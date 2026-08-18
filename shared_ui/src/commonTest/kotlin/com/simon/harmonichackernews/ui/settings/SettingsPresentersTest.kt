package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.ContentFilterType
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.UserTagsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsPresentersTest {
    @Test
    fun storiesMapsActionsToTypedSettingsAndPlatformEffects() {
        val presenter = fixture().stories

        assertEquals(
            setOf(SettingsPlatformEffect.RefreshStoryWidgets),
            presenter.setBoolean(StoriesBooleanSetting.ShowIndex, true),
        )
        assertTrue(presenter.snapshot.story.showIndex)
        assertEquals(
            setOf(SettingsPlatformEffect.RequestRestart),
            presenter.setBoolean(StoriesBooleanSetting.HideClicked, true),
        )
        assertTrue(presenter.snapshot.story.hideClicked)
        assertEquals(
            setOf(SettingsPlatformEffect.RequestRestart),
            presenter.setStartingPage("best"),
        )
        assertEquals("best", presenter.snapshot.story.preferredStoryType)
    }

    @Test
    fun commentsAndWebLinksBuildStateFromSharedSettings() {
        val fixture = fixture()
        fixture.comments.setBoolean(CommentsBooleanSetting.Scrollbar, false)
        fixture.comments.setBoolean(CommentsBooleanSetting.ShowUpButton, true)
        fixture.comments.setSorting(CommentSortingPreference.NEWEST_FIRST.storedValue)
        fixture.comments.setProvider(CommentsProvider.OFFICIAL.storedValue)
        fixture.webLinks.setLinkPreview(LinkPreviewType.GITHUB_REPOSITORY, false)
        fixture.webLinks.setLinkPreview(LinkPreviewType.HUGGING_FACE_MODEL, false)
        fixture.webLinks.setLinkPreview(LinkPreviewType.OPENROUTER_MODEL, false)
        fixture.webLinks.setLinkPreview(LinkPreviewType.USGS_EARTHQUAKE, false)

        val comments = fixture.comments.state()
        val webLinks = fixture.webLinks.state(fontLabel = "System")

        assertFalse(comments.showScrollbar)
        assertTrue(comments.showUpButton)
        assertEquals(CommentSortingPreference.NEWEST_FIRST, comments.sorting)
        assertEquals(CommentsProvider.OFFICIAL, comments.provider)
        assertFalse(LinkPreviewType.GITHUB_REPOSITORY in webLinks.enabledLinkPreviews)
        assertFalse(LinkPreviewType.HUGGING_FACE_MODEL in webLinks.enabledLinkPreviews)
        assertFalse(LinkPreviewType.OPENROUTER_MODEL in webLinks.enabledLinkPreviews)
        assertFalse(LinkPreviewType.USGS_EARTHQUAKE in webLinks.enabledLinkPreviews)
        assertEquals("System", webLinks.readerModeFontLabel)
    }

    @Test
    fun appearanceOnlyRequestsPlatformThemeWorkWhenRequired() {
        val presenter = fixture().appearance

        assertEquals(
            emptySet(),
            presenter.setBoolean(AppearanceBooleanSetting.CompactHeader, true),
        )
        assertEquals(
            setOf(SettingsPlatformEffect.ThemeChanged),
            presenter.setBoolean(AppearanceBooleanSetting.SpecialNighttime, true),
        )
        assertEquals(
            setOf(SettingsPlatformEffect.ThemeChanged),
            presenter.setTheme("amoled", nighttime = false),
        )
        assertEquals("amoled", presenter.snapshot.appearance.theme)
    }

    @Test
    fun filtersAndTagsAreNormalizedAndPresentedInPortableState() {
        val fixture = fixture()
        fixture.filtersTags.setFilterItems(
            ContentFilterType.DOMAIN,
            listOf(" example.com ", "example.com", "news.example"),
        )
        fixture.filtersTags.setTag("Zed", "friend")
        fixture.filtersTags.setTag("alice", "author")
        fixture.filtersTags.setHideJobs(true)

        assertEquals(
            listOf("example.com", "news.example"),
            fixture.filtersTags.filterItems(ContentFilterType.DOMAIN),
        )
        assertEquals(listOf("alice", "Zed"), fixture.filtersTags.state().tags.map { it.username })
        assertTrue(fixture.filtersTags.state().hideJobs)
        assertEquals(ContentFilterType.USER, ContentFilterDialog.User.content.type)
    }

    private fun fixture(): PresenterFixture {
        val store = InMemoryKeyValueStore()
        val settings = AppSettingsRepository(store, store.changes)
        return PresenterFixture(
            stories = StoriesSettingsPresenter(settings),
            comments = CommentsSettingsPresenter(settings),
            webLinks = WebLinksSettingsPresenter(settings),
            appearance = AppearanceSettingsPresenter(settings),
            filtersTags = FiltersTagsSettingsPresenter(
                settings,
                ContentFilterRepository(store),
                UserTagsRepository(store),
            ),
        )
    }

    private data class PresenterFixture(
        val stories: StoriesSettingsPresenter,
        val comments: CommentsSettingsPresenter,
        val webLinks: WebLinksSettingsPresenter,
        val appearance: AppearanceSettingsPresenter,
        val filtersTags: FiltersTagsSettingsPresenter,
    )
}
