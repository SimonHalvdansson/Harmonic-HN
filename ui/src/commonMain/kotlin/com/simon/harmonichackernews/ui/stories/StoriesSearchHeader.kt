@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.resources.ic_history
import com.simon.harmonichackernews.presentation.StorySearchOption
import org.jetbrains.compose.resources.painterResource

data class StorySearchPresentationState(
    val active: Boolean,
    val draft: String,
    val suppressAutoFocus: Boolean,
    val sortLabel: String,
    val dateLabel: String,
    val pointsLabel: String,
    val commentsLabel: String,
    val sortLabels: List<String>,
    val dateLabels: List<String>,
    val pointsLabels: List<String>,
    val commentsLabels: List<String>,
    val onlyRead: Boolean,
)

@Composable
fun StorySearchHeader(
    state: StorySearchPresentationState,
    sideStart: Dp,
    sideEnd: Dp,
    iconColor: Color,
    menuColor: Color,
    menuTextColor: Color,
    fontFamily: FontFamily,
    onDraftChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    onOptionSelected: (kind: StorySearchOption, index: Int) -> Unit,
    onToggleOnlyRead: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme
    val searchContainerColor = if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.onSurface.copy(alpha = 0.04f).compositeOver(colorScheme.surfaceContainerHighest)
    } else {
        colorScheme.surfaceContainerHighest
    }
    LaunchedEffect(state.active, state.suppressAutoFocus) {
        if (state.active && !state.suppressAutoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Column {
        TextField(
            value = state.draft,
            onValueChange = onDraftChanged,
            placeholder = { Text("Search posts") },
            leadingIcon = {
                IconButton(
                    onClick = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onClose()
                    },
                ) {
                    Icon(painterResource(Res.drawable.ic_arrow_back), "Back", tint = iconColor)
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        onDraftChanged("")
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                ) {
                    Icon(painterResource(Res.drawable.ic_close), "Clear search", tint = iconColor)
                }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                onSearch(state.draft)
                keyboard?.hide()
                focusManager.clearFocus()
            }),
            shape = RoundedCornerShape(32.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = searchContainerColor,
                unfocusedContainerColor = searchContainerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = sideStart, end = sideEnd)
                .focusRequester(focusRequester),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = PaddingValues(start = sideStart + 4.dp, end = sideEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchOptionChip(state.sortLabel, state.sortLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.SORT, it) } }
            item { SearchOptionChip(state.dateLabel, state.dateLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.DATE, it) } }
            item { SearchOptionChip(state.pointsLabel, state.pointsLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.POINTS, it) } }
            item { SearchOptionChip(state.commentsLabel, state.commentsLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.COMMENTS, it) } }
            item {
                FilterChip(
                    selected = state.onlyRead,
                    onClick = onToggleOnlyRead,
                    label = { Text("From history") },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_history), null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun SearchOptionChip(
    label: String,
    labels: List<String>,
    iconColor: Color,
    menuColor: Color,
    menuTextColor: Color,
    fontFamily: FontFamily,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                // Resize the label inside the chip so its border follows every frame. Resizing
                // an outer box instead clips a fully sized chip at the old trailing edge.
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (fadeIn(tween(60, delayMillis = 140)) togetherWith fadeOut(tween(35)))
                            .using(SizeTransform(clip = false) { initialSize, targetSize ->
                                // Let a long outgoing label disappear before the surface contracts;
                                // reveal the incoming label only once its complete width is ready.
                                if (targetSize.width < initialSize.width) {
                                    tween(105, delayMillis = 35)
                                } else {
                                    tween(140)
                                }
                            })
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "search filter label",
                ) { visibleLabel ->
                    Text(visibleLabel, maxLines = 1, softWrap = false)
                }
            },
            border = BorderStroke(1.dp, iconColor),
        )
        HarmonicDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            modifier = Modifier.width(196.dp),
            containerColor = menuColor,
        ) {
            labels.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = menuTextColor,
                            fontFamily = fontFamily,
                            fontSize = 16.sp,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    trailingIcon = { RadioButton(selected = option == label, onClick = null) },
                )
            }
        }
    }
}
