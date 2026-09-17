package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionMessages
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsFavoriteService

data class AddBookmarkToFavoritesResult(
    val id: Int,
    val title: String,
    val successful: Boolean,
    val message: String,
)

/** Owns the remote favorite action, local membership update, timestamp and result mapping. */
class AddBookmarksToFavoritesUseCase(
    private val favorites: HackerNewsFavoriteService,
    private val savedItems: SavedItemsRepository,
    private val nowMillis: () -> Long = {
        kotlin.time.Clock.System.now().toEpochMilliseconds()
    },
) {
    suspend fun add(id: Int, fallbackTitle: String): AddBookmarkToFavoritesResult = when (
        val result = favorites.setFavorite(id, favorite = true)
    ) {
        is HackerNewsActionResult.Success -> {
            savedItems.setMembershipAtomic(
                source = SavedItemSource.FAVORITES,
                id = id,
                present = true,
                createdAtMillis = nowMillis(),
            )
            AddBookmarkToFavoritesResult(
                id = id,
                title = result.itemTitle?.takeIf(String::isNotBlank) ?: fallbackTitle,
                successful = true,
                message = "In HN favorites",
            )
        }
        is HackerNewsActionResult.Failure -> AddBookmarkToFavoritesResult(
            id = id,
            title = fallbackTitle,
            successful = false,
            message = HackerNewsActionMessages.favoriteFailure(result.summary, result.detail),
        )
        is HackerNewsActionResult.Captcha -> AddBookmarkToFavoritesResult(
            id = id,
            title = fallbackTitle,
            successful = false,
            message = "HN requires a captcha before adding this favorite",
        )
    }
}
