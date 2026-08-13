package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.AddBookmarksToFavoritesUseCase
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AddBookmarksToFavoritesDialog(
    bookmarkIds: IntArray,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val accounts = app.platform.accounts
    val addFavorites = remember(app) {
        AddBookmarksToFavoritesUseCase(
            favorites = app.hackerNewsUser,
            savedItems = app.savedItems,
        )
    }
    val items = remember(bookmarkIds.contentHashCode()) {
        bookmarkIds.map { id ->
            val story = Story().apply { this.id = id }
            BookmarkFavoriteItem(
                id = id,
                title = if (app.storyCache.hydrateStory(story) && !story.title.isNullOrBlank()) {
                    story.title.orEmpty()
                } else {
                    "Story #$id"
                },
            )
        }
    }
    SharedAddBookmarksToFavoritesDialog(
        items = items,
        prerequisiteError = if (accounts.load() == null) {
            "Log in to Hacker News before adding favorites"
        } else {
            null
        },
        addFavorite = { item ->
            addFavorites.add(item.id, item.title).let { result ->
                BookmarkFavoriteResult(
                    id = result.id,
                    title = result.title,
                    successful = result.successful,
                    message = result.message,
                )
            }
        },
        onDismiss = onDismiss,
    )
}
