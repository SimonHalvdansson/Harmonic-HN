package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.settings.UserAvatarMode
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** A stable, local identity: no network requests or platform-dependent random generator. */
internal fun avatarSeed(author: String): Int = author.fold(0x811c9dc5.toInt()) { hash, char ->
    (hash xor char.code) * 0x01000193
}

@Composable
fun UserAvatar(author: String, mode: UserAvatarMode, modifier: Modifier = Modifier) {
    if (mode == UserAvatarMode.NONE) return
    if (mode == UserAvatarMode.GENERIC) {
        Icon(
            painterResource(Res.drawable.ic_person),
            contentDescription = null,
            tint = HarmonicTheme.colors.storyDisabled,
            modifier = modifier.clip(CircleShape)
                .background(HarmonicTheme.colors.surfaceContainerHighest).padding(2.dp),
        )
        return
    }
    val seed = remember(author) { avatarSeed(author) }
    val hue = (seed.toUInt() % 360u).toFloat()
    val background = Color.hsl(hue, 0.48f, 0.88f)
    val foreground = Color.hsl(hue, 0.62f, 0.38f)
    val accent = Color.hsl((hue + 55f) % 360f, 0.72f, 0.59f)
    Canvas(modifier.clip(CircleShape).background(background)) {
        val unit = size.minDimension / 5f
        rotate(((seed ushr 9) % 4) * 90f) {
            // Mirrored, rounded mosaic tiles give each username a recognizable silhouette.
            for (row in 0..2) for (column in 0..1) {
                val bits = seed ushr (row * 6 + column * 3)
                if (bits and 3 == 0 && column != 0) continue
                val color = if (bits and 4 == 0) foreground else accent
                val xs = if (column == 0) listOf(2) else listOf(1, 3)
                xs.forEach { x ->
                    val offset = Offset(x * unit + unit * 0.06f, (row + 1) * unit + unit * 0.06f)
                    val tile = unit * 0.88f
                    when ((seed ushr (row + column + 22)) and 3) {
                        0 -> drawCircle(color, tile / 2, offset + Offset(tile / 2, tile / 2))
                        1 -> drawPath(Path().apply {
                            moveTo(offset.x + tile / 2, offset.y)
                            lineTo(offset.x + tile, offset.y + tile)
                            lineTo(offset.x, offset.y + tile)
                            close()
                        }, color)
                        else -> drawRoundRect(color, offset, Size(tile, tile), CornerRadius(unit * 0.2f))
                    }
                }
            }
        }
    }
}
