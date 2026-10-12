package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import org.jetbrains.compose.resources.painterResource

internal enum class HeaderPreviewSample(val label: String) {
    LINK_WITH_IMAGE("Link with image"),
    LINK_WITHOUT_IMAGE("Link without image"),
    TEXT_POST("Text post"),
    LINK_PREVIEW("Link preview");
}

/** Local sample navigation, deliberately outside the preference cards and sticky preview. */
@Composable
internal fun HeaderPreviewSampleSelector(sample: HeaderPreviewSample, onSample: (HeaderPreviewSample) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onSample(HeaderPreviewSample.entries[(sample.ordinal + HeaderPreviewSample.entries.size - 1) % HeaderPreviewSample.entries.size])
            }) {
                Icon(painterResource(Res.drawable.ic_chevron_left), "Previous preview post")
            }
            AnimatedContent(sample, Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "preview post type") { visibleSample ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(visibleSample.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${visibleSample.ordinal + 1} of ${HeaderPreviewSample.entries.size}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = {
                onSample(HeaderPreviewSample.entries[(sample.ordinal + 1) % HeaderPreviewSample.entries.size])
            }) {
                Icon(painterResource(Res.drawable.ic_chevron_right), "Next preview post")
            }
        }
    }
}
