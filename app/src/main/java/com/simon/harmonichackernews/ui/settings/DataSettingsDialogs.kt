package com.simon.harmonichackernews.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class BookmarkFavoriteResult(
    val id: Int,
    val title: String,
    val successful: Boolean,
    val message: String,
)

@Composable
fun AddBookmarksToFavoritesDialog(
    bookmarkIds: IntArray,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val initialTitles = remember(bookmarkIds.contentHashCode()) {
        bookmarkIds.map { id ->
            val story = Story().apply { this.id = id }
            if (Utils.loadCachedStorySummary(context, story) && !story.title.isNullOrBlank()) {
                story.title.orEmpty()
            } else {
                "Story #$id"
            }
        }
    }
    var results by remember { mutableStateOf<List<BookmarkFavoriteResult>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(bookmarkIds.contentHashCode(), activity) {
        if (!AccountUtils.hasAccountDetails(context)) {
            results = bookmarkIds.mapIndexed { index, id ->
                BookmarkFavoriteResult(
                    id = id,
                    title = initialTitles[index],
                    successful = false,
                    message = "Log in to Hacker News before adding favorites",
                )
            }
            currentIndex = bookmarkIds.size
            finished = true
            return@LaunchedEffect
        }

        if (activity == null) {
            results = bookmarkIds.mapIndexed { index, id ->
                BookmarkFavoriteResult(
                    id = id,
                    title = initialTitles[index],
                    successful = false,
                    message = "Couldn't access the current activity",
                )
            }
            currentIndex = bookmarkIds.size
            finished = true
            return@LaunchedEffect
        }

        val completed = mutableListOf<BookmarkFavoriteResult>()
        bookmarkIds.forEachIndexed { index, id ->
            currentIndex = index
            val result = addBookmarkToFavorites(
                activity = activity,
                id = id,
                initialTitle = initialTitles[index],
            )
            completed += result
            results = completed.toList()
            currentIndex = index + 1
            if (index < bookmarkIds.lastIndex) {
                delay(1_000)
            }
        }
        finished = true
    }

    if (finished) {
        BookmarkFavoriteResultsDialog(
            results = results,
            onDismiss = onDismiss,
        )
    } else {
        SettingsAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { SettingsDialogTitle("Adding bookmarks to HN favorites") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BookmarkTransferAnimation()
                    Text(
                        text = if (bookmarkIds.isEmpty()) {
                            "No bookmarks to add"
                        } else {
                            "Adding bookmark ${currentIndex + 1} of ${bookmarkIds.size}"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = HarmonicTheme.colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (bookmarkIds.isEmpty()) {
                                0f
                            } else {
                                currentIndex.toFloat() / bookmarkIds.size
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                SettingsDialogTextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BookmarkTransferAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "bookmark transfer")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_900,
                delayMillis = 180,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "story position",
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val targetSize = 52.dp
        val storySize = 28.dp
        val startX = with(density) { ((targetSize - storySize) / 2).toPx() }
        val endX = with(density) {
            (maxWidth - targetSize + (targetSize - storySize) / 2).toPx()
        }
        val startY = with(density) { ((96.dp - storySize) / 2).toPx() }
        val endY = startY
        val controlX = (startX + endX) / 2f
        val controlY = startY - with(density) { 34.dp.toPx() }
        val remaining = 1f - progress
        val x = remaining * remaining * startX +
            2f * remaining * progress * controlX +
            progress * progress * endX
        val y = remaining * remaining * startY +
            2f * remaining * progress * controlY +
            progress * progress * endY
        val storyAlpha = when {
            progress < 0.16f -> progress / 0.16f
            progress > 0.82f -> (1f - progress) / 0.18f
            else -> 1f
        }.coerceIn(0f, 1f)
        val storyScale = 0.86f + 0.14f * sin(Math.PI * progress).toFloat()
        val arrival = (1f - abs(progress - 0.88f) / 0.12f).coerceAtLeast(0f)

        TransferTarget(
            icon = R.drawable.ic_bookmark,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        TransferTarget(
            icon = R.drawable.ic_star_filled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .scale(1f + 0.08f * arrival),
        )
        Icon(
            painter = painterResource(R.drawable.ic_newspaper),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(storySize)
                .scale(storyScale)
                .alpha(storyAlpha),
        )
    }
}

@Composable
private fun TransferTarget(
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BookmarkFavoriteResultsDialog(
    results: List<BookmarkFavoriteResult>,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Finished") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
            ) {
                itemsIndexed(results, key = { _, result -> result.id }) { index, result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (result.successful) R.drawable.ic_check else R.drawable.ic_close,
                            ),
                            contentDescription = null,
                            tint = if (result.successful) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .padding(top = 2.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                        ) {
                            Text(
                                text = result.title,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = result.message,
                                modifier = Modifier.padding(top = 2.dp),
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 38.dp),
                            color = HarmonicTheme.colors.outlineVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private suspend fun addBookmarkToFavorites(
    activity: ComponentActivity,
    id: Int,
    initialTitle: String,
): BookmarkFavoriteResult = suspendCancellableCoroutine { continuation ->
    var title = initialTitle
    UserActions.setFavorite(
        activity,
        id,
        true,
        object : UserActions.ActionCallback {
            override fun onItemTitleLoaded(itemId: Int, loadedTitle: String?) {
                if (itemId == id && !loadedTitle.isNullOrBlank()) {
                    title = loadedTitle
                }
            }

            override fun onSuccess(response: Response) {
                response.close()
                Utils.setFavorite(activity, id, true)
                if (continuation.isActive) {
                    continuation.resume(
                        BookmarkFavoriteResult(
                            id = id,
                            title = title,
                            successful = true,
                            message = "In HN favorites",
                        ),
                    )
                }
            }

            override fun onFailure(summary: String?, response: String?) {
                if (continuation.isActive) {
                    continuation.resume(
                        BookmarkFavoriteResult(
                            id = id,
                            title = title,
                            successful = false,
                            message = formatFavoriteFailure(summary, response),
                        ),
                    )
                }
            }
        },
    )
}

private fun formatFavoriteFailure(summary: String?, response: String?): String {
    val safeSummary = summary?.trim().takeUnless(String?::isNullOrEmpty)
        ?: "Couldn't add to favorites"
    var safeResponse = response?.trim().orEmpty()
    if (safeResponse.isEmpty() || safeResponse == safeSummary) {
        return safeSummary
    }
    safeResponse = safeResponse.replace('\n', ' ').replace(Regex("\\s+"), " ")
    if (safeResponse.length > 160) {
        safeResponse = safeResponse.take(157) + "…"
    }
    return "$safeSummary: $safeResponse"
}
