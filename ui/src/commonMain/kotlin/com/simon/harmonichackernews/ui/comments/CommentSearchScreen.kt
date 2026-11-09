package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.content.PrefetchCommentContent
import com.simon.harmonichackernews.ui.settings.RequestSettingsDialogFocus

/** Stateless comment-search surface backed by the shared comment-thread store. */
@Composable
fun CommentSearchScreen(
    fieldValue: TextFieldValue,
    onFieldValueChanged: (TextFieldValue) -> Unit,
    listState: LazyListState,
    scrollState: ScrollState,
    visibleComments: List<PortableCommentItem>,
    mutedColor: Color,
    fontFamily: FontFamily,
    requestFocus: Boolean,
    preparing: Boolean = false,
    collectLinks: Boolean = false,
    commentContent: @Composable (PortableCommentItem) -> Unit,
) {
    val listMaxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.62f }
    PrefetchCommentContent(listState, visibleComments, collectLinks)
    val focusRequester = remember { FocusRequester() }
    RequestSettingsDialogFocus(focusRequester, enabled = requestFocus)
    val matchLabel = if (visibleComments.size == 1) "MATCH" else "MATCHES"

    // Give results the remaining *visible* height, rather than nesting a window-sized list
    // inside another scrolling column that focus can pan above the keyboard.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxHeight < 200.dp * LocalDensity.current.fontScale
        Column(Modifier.fillMaxWidth()) {
            if (!compact) {
                Text(
                    text = if (preparing) "Searching…" else "(${visibleComments.size} $matchLabel)",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 2.dp),
                    color = mutedColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.simon.harmonichackernews.ui.common.DialogSearchField(
                    value = fieldValue,
                    onValueChange = onFieldValueChanged,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusRequester(focusRequester),
                    placeholder = "Search comments",
                )
                if (compact) {
                    Text(
                        text = if (preparing) "Searching…" else "${visibleComments.size} $matchLabel",
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        color = mutedColor,
                        fontFamily = fontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(
                    top = if (compact) 0.dp else 8.dp,
                    bottom = if (compact) 8.dp else 24.dp,
                ),
            ) {
                items(visibleComments, key = PortableCommentItem::id, contentType = { "comment" }) { comment ->
                    Box(
                        Modifier.fillMaxWidth().animateItem(
                            fadeInSpec = tween(
                                durationMillis = SearchResultFadeInDurationMillis,
                                delayMillis = SearchResultPlacementDelayMillis,
                            ),
                            placementSpec = tween(
                                durationMillis = SearchResultPlacementDurationMillis,
                                delayMillis = SearchResultPlacementDelayMillis,
                                easing = FastOutSlowInEasing,
                            ),
                            fadeOutSpec = tween(durationMillis = SearchResultFadeOutDurationMillis),
                        ),
                    ) {
                        commentContent(comment)
                    }
                }
            }
        }
    }
}

private const val SearchResultFadeOutDurationMillis = 110
private const val SearchResultFadeInDurationMillis = 180
private const val SearchResultPlacementDelayMillis = 70
private const val SearchResultPlacementDurationMillis = 230
