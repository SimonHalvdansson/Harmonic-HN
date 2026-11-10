package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
internal fun ArticleCategoryIntroduction(onHowItWorks: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CategoryHeaderIllustration(Modifier.widthIn(max = 340.dp).fillMaxWidth().height(144.dp))
        Text("Tag stories automatically", fontFamily = AppUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp)
        Text("Set up categories, compare items, and find matches using text embeddings.",
            Modifier.padding(top = 4.dp), fontFamily = AppUiFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onHowItWorks, modifier = Modifier.align(Alignment.End)) {
            Icon(painterResource(Res.drawable.ic_info), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("How this works", fontFamily = AppUiFontFamily, fontSize = 14.sp)
        }
    }
}

/** A theme-aware illustration of embeddings grouped into categories. */
@Composable
internal fun CategoryHeaderIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val font = AppUiFontFamily
    val accents = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    val fills = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    val inks = listOf(scheme.onPrimaryContainer, scheme.onTertiaryContainer, scheme.onSecondaryContainer)
    val clouds = remember {
        listOf(18, 16, 21).mapIndexed { index, count ->
            val random = Random(71 + index * 113)
            List(count) {
                val angle = random.nextFloat() * 6.283185f
                val radius = 7f + sqrt(random.nextFloat()) * 26f
                Offset(cos(angle) * radius * if (index == 1) 1.12f else 0.9f,
                    sin(angle) * radius * if (index == 2) 1.05f else 0.85f)
            }
        }
    }
    val labels = remember(measurer, font, inks) { listOf("AI", "Space", "Climate").mapIndexed { index, label ->
        measurer.measure(label, TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = inks[index]), density = Density(1f))
    } }
    Canvas(modifier.clearAndSetSemantics { }) {
        withTransform({ scale(size.width / 340f, size.height / 144f, pivot = Offset.Zero) }) {
            fun chip(index: Int, x: Float, y: Float, width: Float) {
                drawRoundRect(fills[index], Offset(x, y), Size(width, 28f), CornerRadius(10f))
                val label = labels[index]
                drawText(label, topLeft = Offset(x + (width - label.size.width) / 2, y + (28 - label.size.height) / 2))
            }
            listOf(Offset(65f, 61f), Offset(170f, 48f), Offset(275f, 66f)).forEachIndexed { index, center ->
                drawCircle(fills[index].copy(alpha = 0.55f), 43f, center)
                clouds[index].forEachIndexed { dot, offset ->
                    val point = center + offset
                    if (dot % 4 == 0) drawLine(accents[index].copy(alpha = 0.18f), center, point, 1f)
                    drawCircle(accents[index].copy(alpha = 0.65f + dot % 3 * 0.12f), if (dot % 5 == 0) 3.5f else 2.5f, point)
                }
                drawCircle(scheme.surface, 5f, center)
                drawCircle(accents[index], 3f, center)
            }
            chip(0, 41f, 108f, 48f); chip(1, 138f, 104f, 64f); chip(2, 236f, 110f, 78f)
        }
    }
}
