@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_cloud_off
import com.simon.harmonichackernews.resources.ic_library_books
import com.simon.harmonichackernews.resources.ic_refresh
import com.simon.harmonichackernews.resources.ic_search
import org.jetbrains.compose.resources.DrawableResource
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

internal const val SavedListTransitionDurationMillis = 220

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
fun StoryListStatus(
    state: StoryListStatusState,
    searchMode: Boolean,
    normalColor: Color,
    disabledColor: Color,
    fontFamily: FontFamily,
    loadingIndicator: @Composable () -> Unit = {
        HarmonicLoadingIndicator(modifier = Modifier.size(48.dp))
    },
    centerFailure: Boolean = false,
    showFailure: Boolean = true,
    onRetry: () -> Unit,
    onShowCached: () -> Unit,
) {
    var visibleEmptySavedListText by remember { mutableStateOf(state.emptySavedListText) }
    val failureVisible = showFailure && (state.loadingFailed || state.serverError)
    var retainedFailureState by remember { mutableStateOf(state) }
    LaunchedEffect(failureVisible, state) {
        if (failureVisible) retainedFailureState = state
    }
    // Keep the heading and cached action in the outgoing layout until it has fully collapsed.
    val failureState = if (failureVisible) state else retainedFailureState
    LaunchedEffect(state.showEmptySavedList, state.emptySavedListText) {
        visibleEmptySavedListText = retainedEmptySavedListText(
            current = visibleEmptySavedListText,
            next = state.emptySavedListText,
            visible = state.showEmptySavedList,
        )
    }
    AnimatedVisibility(
        visible = state.loading,
        enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = tween(220),
            expandFrom = Alignment.Top,
            clip = false,
        ),
        exit = fadeOut(tween(140)) + shrinkVertically(
            animationSpec = tween(180),
            shrinkTowards = Alignment.Top,
            clip = false,
        ),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
            loadingIndicator()
        }
    }
    AnimatedVisibility(
        failureVisible,
        enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = tween(220),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(140)) + shrinkVertically(
            animationSpec = tween(220),
            shrinkTowards = Alignment.Top,
        ),
        modifier = if (centerFailure) Modifier.fillMaxSize() else Modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (centerFailure) Modifier.fillMaxSize() else Modifier)
                .padding(top = if (centerFailure) 0.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (centerFailure) Arrangement.Center else Arrangement.Top,
        ) {
            Icon(painterResource(Res.drawable.ic_cloud_off), null, Modifier.size(48.dp), tint = normalColor)
            Text(
                if (failureState.serverError) "Server error" else failureState.failureMessage,
                color = normalColor,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                Modifier.padding(
                    top = 8.dp,
                    bottom = if (failureState.showCachedAction && !searchMode) 12.dp else 0.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HarmonicTheme.colors.overlayButton,
                        contentColor = HarmonicTheme.colors.overlayButtonContent,
                    ),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Retry",
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                if (failureState.showCachedAction && !searchMode) {
                    OutlinedButton(
                        onClick = onShowCached,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_library_books),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Show cached stories",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
    AnimatedVisibility(
        !searchMode && state.showEmptySavedList,
        // Lazy rows keep drawing while animateItem runs its exit fade. Keep this header content at
        // zero height until that fade completes so the header background cannot cover the rows.
        enter = fadeIn(
            tween(
                durationMillis = 180,
                delayMillis = SavedListTransitionDurationMillis,
            ),
        ) + expandVertically(
            animationSpec = tween(
                durationMillis = 1,
                delayMillis = SavedListTransitionDurationMillis,
            ),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(140)) + shrinkVertically(
            animationSpec = tween(durationMillis = 1),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        EmptyState(
            text = visibleEmptySavedListText,
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
        EmptyState(
            text = "No stories found",
            icon = Res.drawable.ic_search,
            color = normalColor,
            fontFamily = fontFamily,
            large = true,
        )
    }
}

internal fun retainedEmptySavedListText(
    current: String,
    next: String,
    visible: Boolean,
): String = if (visible) next else current

@Composable
private fun EmptyState(
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
