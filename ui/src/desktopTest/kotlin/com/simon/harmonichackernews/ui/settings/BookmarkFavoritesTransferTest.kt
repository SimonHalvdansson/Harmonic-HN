package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CompletableDeferred
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkFavoritesTransferTest {
    @Test fun titleHydrationDoesNotCancelAnInFlightFavoriteRequest() = SwingUtilities.invokeAndWait {
        val items = mutableStateOf(listOf(BookmarkFavoriteItem(100, "Story #100")))
        val response = CompletableDeferred<Unit>()
        var requests = 0
        var completions = 0
        var cancellations = 0
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val scene = ImageComposeScene(700, 800, Density(1f)) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AddBookmarksToFavoritesDialog(items.value, null, { item ->
                    requests++
                    try {
                        response.await()
                        completions++
                        BookmarkFavoriteResult(item.id, item.title, true, "In HN favorites")
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        cancellations++
                        throw error
                    }
                }, {})
            }
        }
        try {
            repeat(10) { scene.render(it * 16_000_000L).close() }
            assertEquals(1, requests)
            items.value = listOf(BookmarkFavoriteItem(100, "The hydrated story title"))
            repeat(10) { scene.render((it + 10) * 16_000_000L).close() }
            assertEquals(1, requests)
            assertEquals(0, cancellations)
            response.complete(Unit)
            repeat(10) { scene.render((it + 20) * 16_000_000L).close() }
            assertEquals(1, completions)
        } finally { scene.close() }
    }

    @Test fun titleHydrationDoesNotRepeatCompletedFavoriteRequests() = SwingUtilities.invokeAndWait {
        val items = mutableStateOf(listOf(BookmarkFavoriteItem(100, "Story #100")))
        val requests = mutableListOf<Int>()
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val scene = ImageComposeScene(700, 800, Density(1f)) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                AddBookmarksToFavoritesDialog(items.value, null, { item ->
                    requests += item.id
                    BookmarkFavoriteResult(item.id, item.title, true, "In HN favorites")
                }, {})
            }
        }
        try {
            repeat(10) { scene.render(it * 16_000_000L).close() }
            assertEquals(listOf(100), requests, "Initial request must run once")
            items.value = listOf(BookmarkFavoriteItem(100, "The hydrated story title"))
            repeat(10) { scene.render((it + 10) * 16_000_000L).close() }
            assertEquals(listOf(100), requests, "Loading cached titles must not restart a transfer")
        } finally { scene.close() }
    }
}
