@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.simon.harmonichackernews.ui.common.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.resources.ic_cloud_off
import com.simon.harmonichackernews.resources.ic_history
import com.simon.harmonichackernews.resources.ic_search
import org.jetbrains.compose.resources.DrawableResource
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
    val onlyClicked: Boolean,
)

private val StoriesRootEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Shared root transition between a primary story feed and its search results. */
@Composable
fun SharedStoriesRoot(
    searching: Boolean,
    suppressSearchAutoFocus: Boolean,
    predictiveBackActive: Boolean,
    predictiveBackProgress: Float,
    backgroundColor: Color,
    mainLayer: @Composable () -> Unit,
    searchLayer: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val standardSearchProgress by animateFloatAsState(
        targetValue = if (searching) 1f else 0f,
        animationSpec = if (!searching && suppressSearchAutoFocus) {
            snap()
        } else {
            tween(180, easing = StoriesRootEasing)
        },
        label = "stories search transition",
    )
    val progress = predictiveBackProgress.coerceIn(0f, 1f)
    val predictiveSearchFade = (progress / 0.5f).coerceIn(0f, 1f)
    val predictiveMainFade = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val searchAlpha = if (predictiveBackActive) {
        1f - predictiveSearchFade
    } else {
        standardSearchProgress
    }
    val mainAlpha = if (predictiveBackActive) {
        predictiveMainFade
    } else {
        1f - standardSearchProgress
    }
    val mainActive = !predictiveBackActive && !searching
    val searchActive = !predictiveBackActive && searching

    Box(Modifier.fillMaxSize().background(backgroundColor)) {
        StableStoryLayer(
            active = mainActive,
            modifier = Modifier
                .matchParentSize()
                .zIndex(if (mainActive) 1f else 0f)
                .graphicsLayer {
                    alpha = mainAlpha
                    translationY = if (predictiveBackActive) {
                        24.dp.toPx() * (1f - predictiveMainFade)
                    } else {
                        24.dp.toPx() * standardSearchProgress
                    }
                },
            content = mainLayer,
        )
        StableStoryLayer(
            active = searchActive,
            modifier = Modifier
                .matchParentSize()
                .zIndex(if (searchActive) 1f else 0f)
                .graphicsLayer {
                    alpha = searchAlpha
                    translationY = if (predictiveBackActive) {
                        24.dp.toPx() * predictiveSearchFade
                    } else {
                        24.dp.toPx() * (1f - standardSearchProgress)
                    }
                },
            content = searchLayer,
        )
        overlay()
    }
}

@Composable
private fun StableStoryLayer(
    active: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (!active) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
        }
    }
}

@Composable
fun SharedStorySearchHeader(
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
    onToggleOnlyClicked: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
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
        Row(
            modifier = Modifier.padding(start = sideStart, end = sideEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = state.draft,
                onValueChange = onDraftChanged,
                placeholder = { Text("Search posts") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    onSearch(state.draft)
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                shape = RoundedCornerShape(32.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            IconButton(
                onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onClose()
                },
            ) {
                Icon(painterResource(Res.drawable.ic_close), "Close search", tint = iconColor)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = PaddingValues(start = sideStart + 4.dp, end = sideEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SharedSearchOptionChip(state.sortLabel, state.sortLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.SORT, it) } }
            item { SharedSearchOptionChip(state.dateLabel, state.dateLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.DATE, it) } }
            item { SharedSearchOptionChip(state.pointsLabel, state.pointsLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.POINTS, it) } }
            item { SharedSearchOptionChip(state.commentsLabel, state.commentsLabels, iconColor, menuColor, menuTextColor, fontFamily) { onOptionSelected(StorySearchOption.COMMENTS, it) } }
            item {
                FilterChip(
                    selected = state.onlyClicked,
                    onClick = onToggleOnlyClicked,
                    label = { Text("From history") },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_history), null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun SharedSearchOptionChip(
    label: String,
    labels: List<String>,
    iconColor: Color,
    menuColor: Color,
    menuTextColor: Color,
    fontFamily: FontFamily,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.animateContentSize(tween(220))) {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(label) },
            border = BorderStroke(1.dp, iconColor),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(196.dp),
            shape = RoundedCornerShape(16.dp),
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

data class StoryListStatusState(
    val loading: Boolean,
    val loadingFailed: Boolean,
    val serverError: Boolean,
    val failureMessage: String,
    val showCachedAction: Boolean,
    val showEmptySavedList: Boolean,
    val emptySavedListText: String,
    val emptySavedListIcon: DrawableResource,
    val showEmptySearch: Boolean,
)

@Composable
fun SharedStoryListStatus(
    state: StoryListStatusState,
    searchMode: Boolean,
    normalColor: Color,
    disabledColor: Color,
    fontFamily: FontFamily,
    loadingIndicator: @Composable () -> Unit = {
        HarmonicLoadingIndicator(modifier = Modifier.size(48.dp))
    },
    onRetry: () -> Unit,
    onShowCached: () -> Unit,
) {
    AnimatedVisibility(state.loading, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
            loadingIndicator()
        }
    }
    AnimatedVisibility(
        state.loadingFailed || state.serverError,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(Res.drawable.ic_cloud_off), null, Modifier.size(40.dp))
            Text(
                if (state.serverError) "Server error" else state.failureMessage,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                if (state.showCachedAction && !searchMode) {
                    OutlinedButton(onClick = onShowCached) { Text("Show cached") }
                }
            }
        }
    }
    AnimatedVisibility(
        !searchMode && state.showEmptySavedList,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        SharedEmptyState(
            text = state.emptySavedListText,
            icon = state.emptySavedListIcon,
            color = normalColor,
            fontFamily = fontFamily,
            large = true,
        )
    }
    AnimatedVisibility(
        searchMode && state.showEmptySearch,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        SharedEmptyState(
            text = "No stories found",
            icon = Res.drawable.ic_search,
            color = disabledColor,
            fontFamily = fontFamily,
            large = false,
        )
    }
}

@Composable
private fun SharedEmptyState(
    text: String,
    icon: DrawableResource,
    color: Color,
    fontFamily: FontFamily,
    large: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (large) 56.dp else 32.dp, bottom = if (large) 36.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), null, Modifier.size(48.dp), tint = color)
        Text(
            text = text,
            color = color,
            fontFamily = fontFamily,
            fontSize = if (large) 24.sp else 14.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = if (large) 4.dp else 8.dp),
        )
    }
}
