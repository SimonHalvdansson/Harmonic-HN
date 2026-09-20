package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import com.simon.harmonichackernews.settings.UserAvatarColors
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.settings.userAvatarSeed

/** Cached paths in a 100 x 100 coordinate space, shared by runtime avatars and previews. */
internal class GeneratedAvatarArtwork(author: String, style: UserAvatarStyle, options: UserAvatarOptions) {
    private data class Mark(val path: Path, val color: Color, val stroke: Float = 0f)
    private val marks = mutableListOf<Mark>()
    private var rotation = 0f
    private val name = author
    private val seed = userAvatarSeed(name)
    private val hue = (seed.toUInt() % 360u).toFloat()
    private val saturation = when (options.colors) {
        UserAvatarColors.VIVID -> 1f
        UserAvatarColors.MUTED -> .4f
        UserAvatarColors.MONOCHROME -> 0f
    }
    private fun color(h: Float, s: Float, l: Float) = Color.hsl(h, s * saturation, l)
    val background = color(hue, .45f, .90f)
    private val ink = color(hue, .55f, .25f)
    private val mid = color(hue, .58f, .48f)
    private val accent = color((hue + 65f) % 360f, .76f, .62f)
    private val light = color(hue, .35f, .97f)

    init {
        when (style) {
            UserAvatarStyle.MOSAIC -> mosaic()
            UserAvatarStyle.PIXELS -> pixels(AvatarRandom(seed))
            UserAvatarStyle.ORBITAL -> orbital(AvatarRandom(userAvatarSeed("$name:design:0")))
            UserAvatarStyle.ROBOT -> robot(AvatarRandom(userAvatarSeed("$name:design:1")))
            UserAvatarStyle.LANDSCAPE -> landscape(AvatarRandom(userAvatarSeed("$name:design:2")))
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        rotate(rotation, Offset(50f, 50f)) {
            marks.forEach { mark ->
                drawPath(mark.path, mark.color, style = if (mark.stroke == 0f) Fill else
                    Stroke(mark.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }

    private fun c(x: Number, y: Number, r: Number, color: Color) = e(x, y, r, r, color)
    private fun e(x: Number, y: Number, rx: Number, ry: Number, color: Color) {
        marks += Mark(Path().apply {
            addOval(Rect(x.toFloat() - rx.toFloat(), y.toFloat() - ry.toFloat(),
                x.toFloat() + rx.toFloat(), y.toFloat() + ry.toFloat()))
        }, color)
    }
    private fun rect(x: Number, y: Number, w: Number, h: Number, radius: Number, color: Color) {
        marks += Mark(Path().apply {
            addRoundRect(RoundRect(Rect(x.toFloat(), y.toFloat(), x.toFloat() + w.toFloat(),
                y.toFloat() + h.toFloat()), CornerRadius(radius.toFloat())))
        }, color)
    }
    private fun p(path: String, color: Color, stroke: Float = 0f) {
        marks += Mark(PathParser().parsePathString(path).toPath(), color, stroke)
    }

    private fun mosaic() {
        // Preserve the original palette, tile geometry and overlapping hash bits.
        rect(0, 0, 100, 100, 0, color(hue, .48f, .88f))
        val foreground = color(hue, .62f, .38f)
        val highlight = color((hue + 55f) % 360f, .72f, .59f)
        rotation = ((seed ushr 9) % 4) * 90f
        for (row in 0..2) for (column in 0..1) {
            val bits = seed ushr (row * 6 + column * 3)
            if (bits and 3 == 0 && column != 0) continue
            val fill = if (bits and 4 == 0) foreground else highlight
            for (x in if (column == 0) listOf(2) else listOf(1, 3)) {
                val ox = x * 20 + 1.2f; val oy = (row + 1) * 20 + 1.2f
                when ((seed ushr (row + column + 22)) and 3) {
                    0 -> c(ox + 8.8f, oy + 8.8f, 8.8f, fill)
                    1 -> p("M${ox + 8.8f} $oy L${ox + 17.6f} ${oy + 17.6f} L$ox ${oy + 17.6f}Z", fill)
                    else -> rect(ox, oy, 17.6f, 17.6f, 4, fill)
                }
            }
        }
    }

    private fun pixels(r: AvatarRandom) {
        for (y in 0..4) for (x in 0..2) {
            if (r.next() < .48f) continue
            val fill = if (r.next() < .22f) accent else ink
            for (xx in if (x == 2) listOf(2) else listOf(x, 4 - x)) rect(20 + xx * 12, 20 + y * 12, 12, 12, 0, fill)
        }
        if (marks.isEmpty()) rect(44, 44, 12, 12, 0, ink)
    }

    private fun orbital(r: AvatarRandom) {
        val type = r.choice(6)
        rotation = r.choice(12) * 30f
        when (type) {
            0 -> { c(42,47,30,ink); c(42,47,18,background); rect(53,22,19,61,9,accent); c(25,71,10,mid) }
            1 -> { e(50,50,37,15,ink); e(50,50,25,7,background); c(49,49,21,mid); p("M14 50 Q50 79 86 50",accent,9f); c(76,33,8,ink) }
            2 -> { c(36,39,25,ink); c(42,33,20,background); c(65,61,22,accent); c(62,58,10,background); c(27,75,7,mid) }
            3 -> { rect(21,19,17,60,8,ink); rect(43,32,17,51,8,mid); rect(65,17,17,45,8,accent); c(72,76,7,ink) }
            4 -> { p("M22 62 A31 31 0 1 1 75 70",ink,14f); p("M35 55 A16 16 0 1 1 63 58",accent,10f); c(25,77,9,mid); c(70,28,7,light) }
            else -> { p("M19 25 L59 25 L80 49 L59 73 L19 73 L39 49Z",ink); c(61,49,17,accent); rect(17,78,41,9,4,mid) }
        }
    }

    private fun robot(r: AvatarRandom) {
        val head = r.choice(6); val ears = r.choice(4); val antenna = r.choice(5)
        val eyes = r.choice(6); val mouth = r.choice(5)
        val body = if (r.next() < .5f) ink else mid
        val face = if (r.next() < .3f) accent else light
        when (antenna) {
            0 -> { p("M50 29 V13",body,5f); c(50,12,6,accent) }
            1 -> { p("M35 30 L26 15 M65 30 L74 15",body,4f); c(25,13,5,accent); c(75,13,5,accent) }
            2 -> p("M43 28 L43 9 L58 28Z",accent)
            3 -> { p("M50 29 V18 H66",body,5f); c(68,18,5,accent) }
        }
        when (ears) {
            0 -> { rect(10,43,13,21,5,accent); rect(77,43,13,21,5,accent) }
            1 -> { c(19,53,10,ink); c(81,53,10,ink) }
            2 -> p("M22 39 L8 31 L14 66 L24 63Z M78 39 L92 31 L86 66 L76 63Z",accent)
        }
        when (head) {
            0 -> rect(22,27,56,53,6,body)
            1 -> e(50,53,32,28,body)
            2 -> rect(29,23,42,62,19,body)
            3 -> p("M29 27 H71 L84 47 L71 80 H29 L16 47Z",body)
            4 -> p("M20 30 H80 L70 81 H30Z",body)
            else -> p("M24 79 V49 A26 26 0 0 1 52 24 Q79 24 79 51 V79Z",body)
        }
        when (eyes) {
            0 -> { c(39,49,9,face); c(61,49,9,face); c(40,49,4,ink); c(62,49,4,ink) }
            1 -> { rect(30,39,40,20,9,ink); rect(34,46,32,6,3,accent) }
            2 -> { c(50,49,15,face); c(50,49,8,ink); c(53,46,3,light) }
            3 -> { rect(31,39,14,19,3,face); rect(55,39,14,19,3,face); rect(36,45,4,9,2,ink); rect(60,45,4,9,2,ink) }
            4 -> p("M32 50 L39 43 L46 50 M55 50 L62 43 L69 50",face,5f)
            else -> { c(39,48,10,face); c(39,48,4,ink); p("M56 48 H68",face,5f) }
        }
        when (mouth) {
            0 -> rect(39,67,22,4,2,accent)
            1 -> p("M39 64 Q50 77 61 64",face,4f)
            2 -> { rect(38,63,24,10,3,face); p("M44 65 V71 M50 65 V71 M56 65 V71",body,2f) }
            3 -> c(50,68,5,accent)
            else -> p("M39 69 L45 65 L51 69 L59 64",face,3f)
        }
    }

    private fun landscape(r: AvatarRandom) {
        val type = r.choice(6); val night = r.next() < .32f
        val sky = if (night) ink else background
        val near = if (night) light else ink
        val sx = 23 + r.next() * 54; val sy = 20 + r.next() * 17
        rect(0,0,100,100,0,sky); c(sx,sy,9 + r.next() * 7,if (night) light else accent)
        if (night) {
            c(sx + 6,sy - 5,11,sky)
            repeat(6) { c(12 + r.next() * 76,12 + r.next() * 35,1.4f,light) }
        }
        when (type) {
            0 -> {
                val peak = 30 + r.next() * 30
                p("M-5 85 L$peak 24 L82 80 L109 59 V105 H-5Z",mid)
                p("M${peak-10} 39 L$peak 24 L${peak+12} 43 L${peak+2} 37 L${peak-4} 42Z",light)
                p("M-5 92 L20 62 L53 99 L85 55 L110 90 V105 H-5Z",near)
            }
            1 -> { p("M-5 71 Q24 33 62 64 T110 57 V105 H-5Z",mid); p("M-5 88 Q40 45 110 84 V105 H-5Z",accent); p("M-5 97 Q56 71 110 98 V110 H-5Z",near) }
            2 -> { rect(0,58,100,42,0,mid); p("M0 71 Q23 55 48 72 T100 72 V105 H0Z",near); p("M31 64 H60 M44 77 H75 M20 89 H49",accent,3f); p("M70 58 V32 L87 58Z",light) }
            3 -> {
                p("M0 69 Q34 39 100 66 V105 H0Z",mid)
                repeat(4) { i ->
                    val x = 12 + i * 25; val y = 37 + r.next() * 22
                    rect(x-2,y+18,4,38,1,near); p("M$x $y L${x+13} ${y+28} H${x-13}Z",near)
                }
                p("M0 92 Q45 75 100 90 V105 H0Z",accent)
            }
            4 -> { p("M0 50 H24 V67 H42 V105 H0Z M100 39 H74 V58 H64 V105 H100Z",mid); p("M0 72 H17 V88 H34 V105 H0Z M100 70 H82 V85 H72 V105 H100Z",near); p("M48 61 Q66 76 45 87 Q36 97 59 105 H72 Q45 94 57 87 Q72 73 53 61Z",accent) }
            else -> { rect(0,67,100,40,0,mid); p("M-5 94 Q22 74 51 79 Q73 83 108 92 V108 H-5Z",near); p("M58 81 Q63 58 57 40",near,5f); p("M58 42 Q34 26 27 45 Q43 37 58 46 Q69 23 85 34 Q70 34 59 46 Q83 36 86 51 Q69 43 58 46Z",near) }
        }
    }
}

/** Mulberry32 with defined integer overflow; independent of platform Random implementations. */
private class AvatarRandom(private var seed: Int) {
    fun next(): Float {
        seed += 0x6d2b79f5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = t xor (t + (t xor (t ushr 7)) * (61 or t))
        return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
    }
    fun choice(count: Int): Int = (next() * count).toInt().coerceAtMost(count - 1)
}
