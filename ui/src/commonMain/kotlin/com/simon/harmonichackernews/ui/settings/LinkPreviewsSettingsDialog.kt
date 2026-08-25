package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.data.LinkPreviewGroup
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun LinkPreviewsSettingsDialog(
    enabledTypes: Set<LinkPreviewType>,
    onEnabledChanged: (LinkPreviewType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 720.dp),
        title = {
            Column {
                SettingsDialogTitle("Link previews")
                Text(
                    text = "Choose which links get a source-aware preview above the comments.",
                    modifier = Modifier.padding(top = 6.dp, end = 16.dp, bottom = 10.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
        },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                LinkPreviewGroup.entries.forEach { group ->
                    val types = LinkPreviewType.entries.filter { it.group == group }
                    item(key = "header-${group.name}") {
                        Text(
                            text = group.label.uppercase(),
                            modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.7.sp,
                        )
                    }
                    items(
                        count = types.size,
                        key = { index -> types[index].name },
                    ) { index ->
                        val type = types[index]
                        LinkPreviewToggleRow(
                            type = type,
                            checked = type in enabledTypes,
                            onCheckedChange = { onEnabledChanged(type, it) },
                        )
                    }
                }
            }
        },
        neutralButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsDialogTextButton(
                    onClick = {
                        LinkPreviewType.entries.forEach { onEnabledChanged(it, true) }
                    },
                ) { Text("All on") }
                SettingsDialogTextButton(
                    onClick = {
                        LinkPreviewType.entries.forEach { onEnabledChanged(it, false) }
                    },
                ) { Text("All off") }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Done") }
        },
        scrollableContent = true,
    )
}

@Composable
private fun LinkPreviewToggleRow(
    type: LinkPreviewType,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 24.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(type.linkPreviewIcon()),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = type.title,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

internal fun LinkPreviewType.linkPreviewIcon(): DrawableResource = when (this) {
    LinkPreviewType.GITHUB_REPOSITORY,
    LinkPreviewType.GITHUB_ISSUE,
    LinkPreviewType.GITHUB_PULL_REQUEST,
    LinkPreviewType.GITHUB_FILE,
    LinkPreviewType.GITHUB_RELEASE,
    LinkPreviewType.GITHUB_DISCUSSION,
    -> Res.drawable.ic_link_preview_github
    LinkPreviewType.GITLAB_PROJECT -> Res.drawable.ic_link_preview_gitlab
    LinkPreviewType.HUGGING_FACE_MODEL,
    LinkPreviewType.HUGGING_FACE_SPACE,
    LinkPreviewType.HUGGING_FACE_PAPER,
    LinkPreviewType.HUGGING_FACE_COLLECTION,
    -> Res.drawable.ic_link_preview_hugging_face_mono
    LinkPreviewType.HUGGING_FACE_DATASET -> Res.drawable.ic_database
    LinkPreviewType.OPENROUTER_MODEL -> Res.drawable.ic_link_preview_openrouter
    LinkPreviewType.STACK_EXCHANGE -> Res.drawable.ic_link_preview_stack_exchange
    LinkPreviewType.ARXIV -> Res.drawable.ic_link_preview_arxiv
    LinkPreviewType.WIKIPEDIA -> Res.drawable.ic_link_preview_wikipedia
    LinkPreviewType.TWITTER_X -> Res.drawable.ic_link_preview_x
    LinkPreviewType.MASTODON_POST,
    LinkPreviewType.BLUESKY_POST,
    LinkPreviewType.REDDIT_POST,
    -> Res.drawable.ic_forum
    LinkPreviewType.NPM_PACKAGE,
    LinkPreviewType.PYPI_PACKAGE,
    LinkPreviewType.CRATES_PACKAGE,
    LinkPreviewType.GO_PACKAGE,
    LinkPreviewType.HOMEBREW_PACKAGE,
    -> Res.drawable.ic_deployed_code
    LinkPreviewType.CROSSREF_ARTICLE -> Res.drawable.ic_newspaper
    LinkPreviewType.USGS_EARTHQUAKE -> Res.drawable.ic_public
    LinkPreviewType.STATUS_PAGE -> Res.drawable.ic_info
    LinkPreviewType.SUBSTACK_ARTICLE -> Res.drawable.ic_subject
}
