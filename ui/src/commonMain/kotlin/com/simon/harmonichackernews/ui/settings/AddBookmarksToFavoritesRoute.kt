package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.AddBookmarksToFavoritesUseCase
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AddBookmarksToFavoritesDialog(
    bookmarkIds: IntArray,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val accounts = app.platform.accounts
    val accountState by accounts.accountState.collectAsState()
    val addFavorites = remember(app) {
        AddBookmarksToFavoritesUseCase(
            favorites = app.hackerNewsUser,
            savedItems = app.savedItems,
        )
    }
    val ids = remember(bookmarkIds.contentHashCode()) { bookmarkIds.copyOf() }
    val items by produceState(
        initialValue = ids.map { id -> BookmarkFavoriteItem(id, "Story #$id") },
        key1 = ids,
        key2 = app.storyCache,
    ) {
        value = withContext(Dispatchers.Default) {
            ids.map { id ->
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
    }
    AddBookmarksToFavoritesDialog(
        items = items,
        prerequisiteError = when (accountState) {
            HackerNewsAccountState.Loading -> "Checking Hacker News login…"
            HackerNewsAccountState.LoggedOut -> "Log in to Hacker News before adding favorites"
            is HackerNewsAccountState.LoggedIn -> null
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
