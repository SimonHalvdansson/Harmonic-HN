package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.settings_section_quote_highlighting
import com.simon.harmonichackernews.settings.QuoteSpacing
import com.simon.harmonichackernews.settings.QuoteHighlightColor
import com.simon.harmonichackernews.settings.QuoteHighlightStyle
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

@Composable
fun QuoteHighlightingSettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val state = presenter.state(settings)
    val comment = remember {
        val body = "&gt; Small details can make a big difference.<p>Especially when they make a conversation easier to follow.<p>&gt; Keep the original context close to the reply.<p>That makes it clear what each response is referring to."
        PortableCommentItem(
            comment = CommentSnapshot(
                id = -394,
                author = "willow",
                text = body,
                expandedAnchorText = body,
                createdAtEpochSeconds = Clock.System.now().epochSeconds.toInt() - 3600,
            ),
            presentation = CommentPresentationSnapshot(expanded = true, depth = 0),
        )
    }
    SettingsPage(
        title = stringResource(Res.string.settings_section_quote_highlighting),
        showNavigation = true,
        onBack = onBack,
        contentVersion = state.hashCode(),
    ) {
        item {
            SettingsMainToggle(
                title = "Highlight quotes",
                checked = state.highlightQuotes,
                enabled = true,
                onCheckedChange = {
                    repository.setCommentBoolean(CommentBooleanPreference.HIGHLIGHT_QUOTES, it)
                },
            )
            Text(
                text = "Distinguish quoted text from the reply with a subtle background, side line, or both.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        item {
            CommentRow(
                modifier = Modifier.padding(top = 8.dp),
                comment = comment,
                style = state.toPreviewCommentRowStyle(),
                storyAuthor = null,
                accountUser = null,
                userTag = null,
                subtreeReplyCount = 0,
                collapseParent = state.collapseParent,
                showTopLevelIndicator = false,
                nextCommentDepth = null,
                enableLongClick = false,
                onToggleExpanded = {},
                onShowActions = {},
                onLinkLongClick = { _, _, _ -> },
                onReferenceLongClick = { _, _, _ -> },
            )
        }
        item {
            SettingsCategory("Appearance", titleTopPadding = 8.dp) {
                SegmentedSetting(
                    title = "Color",
                    options = QuoteHighlightColor.entries.map { it to it.label },
                    selected = state.quoteHighlightColor,
                    enabled = state.highlightQuotes,
                    onSelected = repository::setQuoteHighlightColor,
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Style",
                    options = QuoteHighlightStyle.entries.map { it to it.label },
                    optionWeights = mapOf(QuoteHighlightStyle.BACKGROUND to 1.35f, QuoteHighlightStyle.SIDE_LINE to 1.1f),
                    selected = state.quoteHighlightStyle,
                    enabled = state.highlightQuotes,
                    onSelected = repository::setQuoteHighlightStyle,
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Quote spacing",
                    options = QuoteSpacing.entries.map { it to it.label },
                    selected = state.quoteSpacing,
                    enabled = state.highlightQuotes,
                    onSelected = repository::setQuoteSpacing,
                )
            }
        }
    }
}
