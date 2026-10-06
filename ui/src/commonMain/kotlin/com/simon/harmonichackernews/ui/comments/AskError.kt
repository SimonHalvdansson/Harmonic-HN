package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_info
import com.simon.harmonichackernews.resources.ic_key
import com.simon.harmonichackernews.resources.ic_settings
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal enum class DiscussionErrorAction(val label: String) {
    OpenSettings("Open settings"),
    Reset("Reset discussion"),
    Retry("Retry"),
}

internal data class DiscussionErrorPresentation(
    val title: String,
    val message: String,
    val icon: DrawableResource,
    val action: DiscussionErrorAction,
)

internal fun discussionErrorPresentation(
    error: String, contextLimitReached: Boolean, inputTooLarge: Boolean = false,
): DiscussionErrorPresentation {
    val localDetail = error.removePrefix("Local summarization failed: ")
    return when {
        inputTooLarge -> DiscussionErrorPresentation(
            "Too much context", error,
            Res.drawable.ic_info, DiscussionErrorAction.OpenSettings,
        )
        contextLimitReached -> DiscussionErrorPresentation(
            "Discussion is full",
            "This conversation has reached the model’s context limit. Reset it to start fresh.",
            Res.drawable.ic_info, DiscussionErrorAction.Reset,
        )
        error.equals("API Key missing", ignoreCase = true) -> DiscussionErrorPresentation(
            "API key missing", "Add your API key in AI settings to continue.",
            Res.drawable.ic_key, DiscussionErrorAction.OpenSettings,
        )
        error.startsWith("Model missing.") -> DiscussionErrorPresentation(
            "Choose an AI model", "Select a model in AI settings to continue.",
            Res.drawable.ic_settings, DiscussionErrorAction.OpenSettings,
        )
        error == "No local AI model is available. Configure AI in Settings." ||
            localDetail == "Download the selected local model before using it" ||
            localDetail == "Install the selected model runtime before using it" ||
            localDetail.startsWith("Gemini Nano's custom prompt feature is unavailable") -> DiscussionErrorPresentation(
            "Local AI needs setup",
            "Open AI settings to check your model’s availability and finish setting it up, or choose another model.",
            Res.drawable.ic_settings, DiscussionErrorAction.OpenSettings,
        )
        else -> DiscussionErrorPresentation(
            "Couldn’t generate a reply", error,
            Res.drawable.ic_info, DiscussionErrorAction.Retry,
        )
    }
}

@Composable
internal fun AskErrorCard(
    presentation: DiscussionErrorPresentation,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.error
    Column(
        modifier.fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(accent.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(presentation.icon), contentDescription = null,
                    modifier = Modifier.size(22.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(presentation.title, color = HarmonicTheme.colors.contentPrimary,
                    fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(presentation.message, Modifier.padding(top = 4.dp),
                    color = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        // Align the label with the copy while letting the ripple extend 12dp on each side.
        TextButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.padding(start = 36.dp, top = 4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = accent),
        ) {
            Text(presentation.action.label, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
