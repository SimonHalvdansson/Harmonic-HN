package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.settings.UserAvatarColors
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarShape
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

private data class AvatarVisual(
    val author: String,
    val style: UserAvatarStyle?,
    val colors: UserAvatarColors,
)

@Composable
fun UserAvatar(
    author: String,
    modifier: Modifier = Modifier,
    options: UserAvatarOptions = UserAvatarOptions(),
) {
    val cornerPercent by animateFloatAsState(
        targetValue = when (options.shape) {
            UserAvatarShape.CIRCLE -> 50f
            UserAvatarShape.ROUNDED -> 25f
            UserAvatarShape.SQUARE -> 0f
        },
        animationSpec = tween(300),
        label = "avatar frame corners",
    )
    val corner = CornerSize(cornerPercent)
    val shape = RoundedCornerShape(corner, corner, corner, corner)
    val visual = remember(author, options) {
        if (options.generic) AvatarVisual("", null, UserAvatarColors.VIVID)
        else AvatarVisual(author, options.styleFor(author), options.colors)
    }
    // The frame morphs independently, while identity, style and palette changes crossfade inside it.
    Crossfade(
        targetState = visual,
        modifier = modifier.clip(shape),
        animationSpec = tween(300),
        label = "user avatar artwork",
    ) { content ->
        if (content.style == null) {
            Icon(
                painterResource(Res.drawable.ic_person), contentDescription = null,
                tint = HarmonicTheme.colors.storyDisabled,
                modifier = Modifier.fillMaxSize()
                    .background(HarmonicTheme.colors.surfaceContainerHighest).padding(2.dp),
            )
        } else {
            val artwork = remember(content) {
                GeneratedAvatarArtwork(content.author, content.style, UserAvatarOptions(colors = content.colors))
            }
            Canvas(Modifier.fillMaxSize().background(artwork.background)) {
                scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) { artwork.draw(this) }
            }
        }
    }
}
