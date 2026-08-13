package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.AddBookmarksToFavoritesUseCase
import com.simon.harmonichackernews.utils.AndroidStoryCache

@Composable
fun AddBookmarksToFavoritesDialog(
    bookmarkIds: IntArray,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    val accounts = appComposition.platform.accounts
    val addFavorites = remember(appComposition) {
        AddBookmarksToFavoritesUseCase(
            favorites = appComposition.hackerNewsUser,
            savedItems = appComposition.savedItems,
        )
    }
    val items = remember(bookmarkIds.contentHashCode()) {
        bookmarkIds.map { id ->
            val story = Story().apply { this.id = id }
            BookmarkFavoriteItem(
                id = id,
                title = if (
                    AndroidStoryCache.hydrate(context, story) && !story.title.isNullOrBlank()
                ) {
                    story.title.orEmpty()
                } else {
                    "Story #$id"
                },
            )
        }
    }
    val prerequisiteError = when {
        accounts.load() == null -> "Log in to Hacker News before adding favorites"
        else -> null
    }
    SharedAddBookmarksToFavoritesDialog(
        items = items,
        prerequisiteError = prerequisiteError,
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
