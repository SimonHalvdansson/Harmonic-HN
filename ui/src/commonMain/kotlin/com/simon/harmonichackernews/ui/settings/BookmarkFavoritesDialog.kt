package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

data class BookmarkFavoriteItem(val id: Int, val title: String)

data class BookmarkFavoriteResult(
    val id: Int,
    val title: String,
    val successful: Boolean,
    val message: String,
)

@Composable
fun AddBookmarksToFavoritesDialog(
    items: List<BookmarkFavoriteItem>,
    prerequisiteError: String?,
    addFavorite: suspend (BookmarkFavoriteItem) -> BookmarkFavoriteResult,
    onDismiss: () -> Unit,
) {
    val itemIds = items.map(BookmarkFavoriteItem::id)
    val currentItems by rememberUpdatedState(items.associateBy(BookmarkFavoriteItem::id))
    val currentAddFavorite by rememberUpdatedState(addFavorite)
    var completedResults by remember(itemIds, prerequisiteError) { mutableStateOf<List<BookmarkFavoriteResult>>(emptyList()) }
    var currentIndex by remember(itemIds, prerequisiteError) { mutableIntStateOf(0) }
    var finished by remember(itemIds, prerequisiteError) { mutableStateOf(false) }
    val results = completedResults.map { result ->
        result.copy(title = currentItems[result.id]?.title ?: result.title)
    }

    LaunchedEffect(itemIds, prerequisiteError) {
        if (prerequisiteError != null) {
            completedResults = itemIds.map { id ->
                val item = currentItems.getValue(id)
                BookmarkFavoriteResult(
                    id = item.id,
                    title = item.title,
                    successful = false,
                    message = prerequisiteError,
                )
            }
            currentIndex = itemIds.size
            finished = true
            return@LaunchedEffect
        }

        val completed = mutableListOf<BookmarkFavoriteResult>()
        itemIds.forEachIndexed { index, id ->
            currentIndex = index
            completed += currentAddFavorite(currentItems.getValue(id))
            completedResults = completed.toList()
            currentIndex = index + 1
            if (index < itemIds.lastIndex) delay(1_000)
        }
        finished = true
    }

    val progressTarget = if (items.isEmpty()) 0f else currentIndex.toFloat() / items.size
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "bookmark favorite progress",
    )

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.animateContentSize(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
        ),
        properties = DialogProperties(dismissOnClickOutside = finished),
        title = {
            AnimatedContent(
                targetState = finished,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "bookmark favorite title",
            ) { done ->
                SettingsDialogTitle(
                    if (done) "Finished" else "Adding bookmarks to HN favorites",
                )
            }
        },
        text = {
            AnimatedContent(
                targetState = finished,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(120))).using(
                        SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                tween(280, easing = FastOutSlowInEasing)
                            },
                        ),
                    )
                },
                label = "bookmark favorite content",
            ) { done ->
                if (done) {
                    BookmarkFavoriteResults(results)
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BookmarkTransferAnimation()
                        Text(
                            text = if (items.isEmpty()) {
                                "No bookmarks to add"
                            } else {
                                "Adding bookmark ${currentIndex + 1} of ${items.size}"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (finished) {
                SettingsDialogTextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (!finished) {
                SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun BookmarkTransferAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "bookmark transfer")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, delayMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "story position",
    )
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        val targetSize = 52.dp
        val storySize = 28.dp
        val startX = with(density) { ((targetSize - storySize) / 2).toPx() }
        val endX = with(density) {
            (maxWidth - targetSize + (targetSize - storySize) / 2).toPx()
        }
        val startY = with(density) { ((96.dp - storySize) / 2).toPx() }
        val controlX = (startX + endX) / 2f
        val controlY = startY - with(density) { 34.dp.toPx() }
        fun position(): IntOffset {
            val p = progress
            val remaining = 1f - p
            return IntOffset(
                (remaining * remaining * startX + 2f * remaining * p * controlX + p * p * endX).roundToInt(),
                (remaining * remaining * startY + 2f * remaining * p * controlY + p * p * startY).roundToInt(),
            )
        }

        TransferTarget(
            icon = Res.drawable.ic_bookmark,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        TransferTarget(
            icon = Res.drawable.ic_star_filled,
            modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer {
                val arrival = (1f - abs(progress - 0.88f) / 0.12f).coerceAtLeast(0f)
                scaleX = 1f + 0.08f * arrival
                scaleY = scaleX
            },
        )
        Icon(
            painter = painterResource(Res.drawable.ic_newspaper),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset { position() }
                .size(storySize)
                .graphicsLayer {
                    val p = progress
                    scaleX = 0.86f + 0.14f * sin(PI * p).toFloat()
                    scaleY = scaleX
                    alpha = when {
                        p < 0.16f -> p / 0.16f
                        p > 0.82f -> (1f - p) / 0.18f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                },
        )
    }
}

@Composable
private fun TransferTarget(icon: DrawableResource, modifier: Modifier = Modifier) {
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
private fun BookmarkFavoriteResults(
    results: List<BookmarkFavoriteResult>,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(results, key = { _, result -> result.id }, contentType = { _, _ -> "bookmark-result" }) { index, result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (result.successful) Res.drawable.ic_check else Res.drawable.ic_close,
                            ),
                            contentDescription = null,
                            tint = if (result.successful) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(24.dp).padding(top = 2.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                text = result.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = result.message,
                                modifier = Modifier.padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 38.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
    }
}
