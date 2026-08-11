package com.simon.harmonichackernews.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsActionMessages
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.platform.AndroidCredentialStore
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils

@Composable
fun AddBookmarksToFavoritesDialog(
    bookmarkIds: IntArray,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val items = remember(bookmarkIds.contentHashCode()) {
        bookmarkIds.map { id ->
            val story = Story().apply { this.id = id }
            BookmarkFavoriteItem(
                id = id,
                title = if (
                    Utils.loadCachedStorySummary(context, story) && !story.title.isNullOrBlank()
                ) {
                    story.title.orEmpty()
                } else {
                    "Story #$id"
                },
            )
        }
    }
    val prerequisiteError = when {
        !AccountUtils.hasAccountDetails(context) ->
            "Log in to Hacker News before adding favorites"
        activity == null -> "Couldn't access the current activity"
        else -> null
    }
    SharedAddBookmarksToFavoritesDialog(
        items = items,
        prerequisiteError = prerequisiteError,
        addFavorite = { item -> addBookmarkToFavorites(checkNotNull(activity), item) },
        onDismiss = onDismiss,
    )
}

private suspend fun addBookmarkToFavorites(
    activity: ComponentActivity,
    item: BookmarkFavoriteItem,
): BookmarkFavoriteResult {
    val service = HackerNewsUserService(
        NetworkComponent.hackerNewsSession,
        AndroidCredentialStore(activity),
    )
    return when (val result = service.setFavorite(item.id, true)) {
        is HackerNewsActionResult.Success -> {
            SavedItemsRepository(AndroidKeyValueStore.global(activity)).setMembership(
                SavedItemSource.FAVORITES,
                item.id,
                present = true,
                createdAtMillis = System.currentTimeMillis(),
            )
            BookmarkFavoriteResult(
                id = item.id,
                title = result.itemTitle?.takeIf(String::isNotBlank) ?: item.title,
                successful = true,
                message = "In HN favorites",
            )
        }
        is HackerNewsActionResult.Failure -> BookmarkFavoriteResult(
            id = item.id,
            title = item.title,
            successful = false,
            message = HackerNewsActionMessages.favoriteFailure(result.summary, result.detail),
        )
        is HackerNewsActionResult.Captcha -> BookmarkFavoriteResult(
            id = item.id,
            title = item.title,
            successful = false,
            message = "HN requires a captcha before adding this favorite",
        )
    }
}
