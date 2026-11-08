package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.simon.harmonichackernews.ui.content.contentTween

/** Only settings previews allocate visibility transitions; real headers compose content directly. */
@Composable
internal fun HeaderSettingsVisibility(
    visible: Boolean,
    animate: Boolean,
    animateSize: Boolean = true,
    vertical: Boolean = false,
    clip: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (!animate) {
        if (visible) content(modifier)
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.testTag("header-settings-visibility"),
        enter = fadeIn(contentTween()) + if (animateSize) {
            if (vertical) expandVertically(contentTween(), expandFrom = Alignment.Top, clip = clip)
            else expandHorizontally(contentTween(), expandFrom = Alignment.Start, clip = clip)
        } else EnterTransition.None,
        exit = fadeOut(contentTween()) + if (animateSize) {
            if (vertical) shrinkVertically(contentTween(), shrinkTowards = Alignment.Top, clip = clip)
            else shrinkHorizontally(contentTween(), shrinkTowards = Alignment.Start, clip = clip)
        } else ExitTransition.None,
    ) { content(Modifier) }
}
