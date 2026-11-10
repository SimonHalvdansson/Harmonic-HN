package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_check
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.content.userTagColor
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun UserTagColorChoices(
    selectedColor: UserTagColor,
    enabled: Boolean,
    onSelected: (UserTagColor) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag("user-tag-colors"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UserTagColor.entries.forEach { color ->
            val accent = userTagColor(color)
            val selected = selectedColor == color
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(color) },
                    )
                    .semantics { contentDescription = "${color.label} tag color" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape)
                        .border(if (selected) 2.dp else 1.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun UserTagPreview(userName: String, tag: String, color: UserTagColor) {
    val accent = userTagColor(color)
    val preview = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(userName.ifBlank { "username" })
        }
        withStyle(SpanStyle(color = accent)) {
            append(" • ")
            append(tag.trim().ifBlank { "Your tag" })
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("user-tag-preview")
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Preview", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
        // One paragraph wraps both identity and tag, so a long username cannot consume the tag's width.
        Text(
            preview,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AppUiFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}
