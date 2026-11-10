package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import kotlin.math.roundToInt

/** Same 0–100 scale as the cutoff and map, with a fixed width while inference is pending. */
@Composable
internal fun CategoryCosineScore(similarity: Float?) {
    val value = similarity?.let { "${(it * 1000).roundToInt() / 10f}" } ?: "—"
    Text(value, Modifier.widthIn(min = 42.dp)
        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(5.dp))
        .padding(horizontal = 5.dp, vertical = 2.dp).clearAndSetSemantics {
            contentDescription = if (similarity == null) "Cosine similarity unavailable" else "Cosine similarity $value out of 100"
        }, fontFamily = AppUiFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        lineHeight = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer)
}
