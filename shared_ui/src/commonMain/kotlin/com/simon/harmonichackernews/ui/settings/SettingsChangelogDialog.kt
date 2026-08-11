package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.format.ChangelogBlock
import com.simon.harmonichackernews.format.parseChangelogMarkdown
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private const val CHANGELOG_RESOURCE = "files/changelog.md"
private const val FALLBACK_CHANGELOG = "Changelog unavailable."
private val ChangelogBodyFontSize = 13.8.sp
private val ChangelogBodyLineHeight = 20.sp
private var cachedChangelogMarkdown: String? = null

@Composable
fun SettingsChangelogDialog(
    onDismiss: () -> Unit,
    onOpenGithub: (() -> Unit)? = null,
) {
    val markdown by produceState(initialValue = "") {
        value = readChangelogMarkdown()
    }
    val scrollState = rememberScrollState()
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = HarmonicDimens.compose_settings_changelog_max_height),
        title = { SettingsDialogTitle("Changelog") },
        text = {
            ChangelogMarkdown(
                markdown = markdown,
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            )
        },
        scrollableContent = true,
        neutralButton = onOpenGithub?.let { openGithub ->
            { SettingsDialogTextButton(onClick = openGithub) { Text("GitHub") } }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Done") }
        },
        confirmButton = {},
    )
}

@Composable
private fun ChangelogMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseChangelogMarkdown(markdown) }
    Column(
        modifier = modifier.padding(
            top = HarmonicDimens.compose_settings_changelog_content_top_padding,
        ),
    ) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                val previous = blocks[index - 1]
                val spacing = when {
                    block is ChangelogBlock.Heading ->
                        HarmonicDimens.compose_settings_changelog_heading_spacing

                    block is ChangelogBlock.Bullet && previous is ChangelogBlock.Bullet -> 0.dp
                    else -> HarmonicDimens.compose_settings_changelog_block_spacing
                }
                Spacer(Modifier.height(spacing))
            }

            when (block) {
                is ChangelogBlock.Heading -> ChangelogHeading(block.text)
                is ChangelogBlock.Paragraph -> ChangelogBodyText(block.text)
                is ChangelogBlock.Bullet -> ChangelogBullet(block.text)
            }
        }
    }
}

@Composable
private fun ChangelogHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        color = HarmonicTheme.colors.textPrimary,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.SemiBold,
        ),
    )
    HorizontalDivider(color = HarmonicTheme.colors.outlineVariant, thickness = 1.dp)
}

@Composable
private fun ChangelogBodyText(text: String) {
    Text(
        text = text,
        color = HarmonicTheme.colors.textPrimary,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = ProductSansFontFamily,
            fontSize = ChangelogBodyFontSize,
            lineHeight = ChangelogBodyLineHeight,
        ),
    )
}

@Composable
private fun ChangelogBullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.width(HarmonicDimens.compose_settings_changelog_bullet_width),
            contentAlignment = Alignment.TopCenter,
        ) {
            ChangelogBodyText("•")
        }
        Spacer(Modifier.width(HarmonicDimens.compose_settings_changelog_bullet_gap))
        Box(Modifier.weight(1f)) { ChangelogBodyText(text) }
    }
}

object ChangelogDialogController {
    private var visible by mutableStateOf(false)

    fun show() {
        visible = true
    }

    @Composable
    fun Content() {
        if (visible) SettingsChangelogDialog(onDismiss = { visible = false })
    }
}

private suspend fun readChangelogMarkdown(): String {
    cachedChangelogMarkdown?.let { return it }
    return runCatching {
        Res.readBytes(CHANGELOG_RESOURCE).decodeToString().removePrefix("\uFEFF")
    }.getOrDefault(FALLBACK_CHANGELOG).also { cachedChangelogMarkdown = it }
}
