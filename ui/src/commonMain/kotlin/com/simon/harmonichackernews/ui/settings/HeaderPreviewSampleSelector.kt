package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.*
import org.jetbrains.compose.resources.painterResource

internal enum class HeaderPreviewSample(val label: String) {
    LINK_WITH_IMAGE("Link with image"),
    LINK_WITHOUT_IMAGE("Link without image"),
    TEXT_POST("Text post"),
    LINK_PREVIEW("Link preview");
}

/** Persistent sample navigation, hosted above the scrolling settings. */
@Composable
internal fun HeaderPreviewSampleSelector(sample: HeaderPreviewSample, onSample: (HeaderPreviewSample) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row {
            IconButton(
                onClick = {
                    onSample(HeaderPreviewSample.entries[(sample.ordinal + HeaderPreviewSample.entries.size - 1) % HeaderPreviewSample.entries.size])
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_chevron_left), "Previous preview post")
            }
            IconButton(
                onClick = {
                    onSample(HeaderPreviewSample.entries[(sample.ordinal + 1) % HeaderPreviewSample.entries.size])
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_chevron_right), "Next preview post")
            }
        }
    }
}
