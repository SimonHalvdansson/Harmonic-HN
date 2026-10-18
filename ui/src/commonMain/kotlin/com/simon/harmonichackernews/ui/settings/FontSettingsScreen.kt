package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.settings_section_font
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowModel
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyle
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import org.jetbrains.compose.resources.stringResource

private val FontPreviewComment = CommentRowModel(
    author = "simon",
    age = "1h",
    body = "I still use a text file for this. Every few years I try something better " +
        "and eventually export it back to a text file.",
    referenceMarker = "",
    referenceUrl = "",
)

@Composable
fun FontSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val commentsPresenter = remember(repository) { CommentsSettingsPresenter(repository) }
    FontSettingsScreen(
        selectedFont = settings.story.fontChoice,
        storyStyle = StoryDisplaySettings.from(settings.story).toStoryRowStyle(
            StoryRowStyleContext(
                score = SettingsStoryPreviewModel.points,
                commentCount = SettingsStoryPreviewModel.commentCount,
                isRead = false,
            ),
        ),
        commentStyle = commentsPresenter.state(settings).toPreviewCommentRowStyle(),
        onFontSelected = repository::setFont,
        onBack = onBack,
    )
}

@Composable
fun FontSettingsScreen(
    selectedFont: AppFont,
    storyStyle: StoryRowStyle,
    commentStyle: CommentRowStyle,
    onFontSelected: (AppFont) -> Unit,
    onBack: () -> Unit,
) {
    val sideMargin = HarmonicDimens.settings_list_segment_horizontal_margin
    SettingsPage(
        title = stringResource(Res.string.settings_section_font),
        showNavigation = true,
        onBack = onBack,
        contentVersion = selectedFont.hashCode() + storyStyle.hashCode() + commentStyle.hashCode(),
        pinnedContent = {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                LargestFontPreview(selectedFont) { font ->
                    StoryRow(
                        model = SettingsStoryPreviewModel,
                        // Keep the font samples visible together even when large story images are enabled.
                        style = storyStyle.copy(
                            preferredFont = font.storedValue,
                            previewImageMode = StoryPreviewMode.OFF,
                            showPreviewText = false,
                        ),
                        animateChanges = false,
                        // StoryRow already adds an 8dp outer margin.
                        cardPadding = PaddingValues(horizontal = sideMargin - 8.dp, vertical = 4.dp),
                    )
                }
                LargestFontPreview(selectedFont) { font ->
                    CommentRow(
                        model = FontPreviewComment,
                        // Account for the row's 8dp margin and its optional 4dp shadow gutter.
                        modifier = Modifier.padding(
                            horizontal = sideMargin - 8.dp - if (commentStyle.hasBackground) 4.dp else 0.dp,
                        ),
                        style = commentStyle.copy(
                            preferredFont = font.storedValue,
                            animateChanges = false,
                            depthIndicatorMode = CommentDepthPreferences.NONE,
                        ),
                    )
                }
            }
        },
    ) {
        item {
            SettingsCategory("Font") {
                Column(Modifier.selectableGroup()) {
                    AppFont.entries.forEachIndexed { index, font ->
                        if (index > 0) SettingsDivider()
                        FontOptionRow(font, selectedFont == font) { onFontSelected(font) }
                    }
                }
            }
        }
    }
}

/** Measure every font at the current width and font scale, but only place the selected sample. */
@Composable
private fun LargestFontPreview(selectedFont: AppFont, content: @Composable (AppFont) -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            AppFont.entries.forEach { font ->
                Box { content(font) }
            }
        },
    ) { measurables, constraints ->
        val samples = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        // Reserve each row separately so the comment's starting position is stable too.
        val width = constraints.constrainWidth(samples.maxOf { it.width })
        val height = constraints.constrainHeight(samples.maxOf { it.height })
        layout(width, height) {
            samples[selectedFont.ordinal].placeRelative(0, 0)
        }
    }
}

@Composable
private fun FontOptionRow(font: AppFont, selected: Boolean, onClick: () -> Unit) {
    val typography = rememberContentTypography(font.storedValue)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBackgroundColor())
            .defaultMinSize(minHeight = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRadioButton(selected = selected)
        Spacer(Modifier.width(8.dp))
        Text(
            text = font.label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = typography.family,
            fontSize = 16.sp,
        )
    }
}
