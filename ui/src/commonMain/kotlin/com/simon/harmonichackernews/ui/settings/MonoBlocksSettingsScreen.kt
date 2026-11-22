package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_account_tree
import com.simon.harmonichackernews.resources.ic_code
import com.simon.harmonichackernews.resources.ic_data_table
import com.simon.harmonichackernews.resources.settings_section_mono_blocks
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.stringResource

private enum class MonoExample(val label: String, val author: String, val intro: String, val block: String) {
    TABLE("Table", "HJain13", "So if I am getting this right?",
        """
        |  +-------------------------------+--------------+--------+--------+----------+-------+
        |  |              Lab              |      XL      |   L    |   M+   |    M     | S/XS  |
        |  +-------------------------------+--------------+--------+--------+----------+-------+
        |  | Google (Gemini 4 Family #1)   |              | Argon  |        |          |       |
        |  | Google (Gemini 4 Family #2)   |              | Carbon | Neon   | Hydrogen |       |
        |  | Anthropic (Claude 5.2 Family) | Fable/Mythos |        |        |          |       |
        |  | Anthropic (Claude 5.5 Family) |              | Opus   |        | Sonnet   | Haiku |
        |  | OpenAI (GPT 6.2 Family)       | Astra        | Sol    |        |          |       |
        |  | OpenAI (GPT 6.1 Family)       |              |        |        | Terra    |       |
        |  | OpenAI (GPT 6 Family)         |              |        |        |          | Luna  |
        |  +-------------------------------+--------------+--------+--------+----------+-------+
        """.trimMargin(),
    ),
    CODE("Code", "Harmonic", "Here’s how the feed keeps track of stories it has already loaded.",
        """
        |val target = activeStore
        |val previous = feedCache?.takeIf { append &amp;&amp; isCurrentCache(it) }
        |val existing = target.stories.mapTo(mutableSetOf(), Story::id)
        |val existingLoaded = target.stories.filter(Story::loaded).mapTo(mutableSetOf(), Story::id)
        """.trimMargin(),
    ),
    // https://news.ycombinator.com/item?id=9286106
    DIAGRAM("Diagram", "icebraining", "This is network diagram described in the article:",
        """
        |                                                        +----------------------+
        |                                                        |                      |
        |                                          +-----------&gt; | Neighbor's router    |
        |                                          |             |                      |
        |  +-----------+      +------------------+ |             +----------------------+
        |  |           |      |                  | +
        |  | OP's PC   +----&gt; | Buffalo Router   |               +----------------------+
        |  |           |      |                  | +             |                      |
        |  +-----------+      +------------------+ |             | OP Comcast connection|
        |                                          +-----------&gt; |                      |
        |                                                        +----------------------+
        """.trimMargin(),
    ),
}

@Composable
fun MonoBlocksSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val state = presenter.state(settings)
    var example by rememberSaveable { mutableStateOf(MonoExample.TABLE) }
    val title = stringResource(Res.string.settings_section_mono_blocks)
    SettingsPage(title = title, showNavigation = true, onBack = onBack) {
        item {
            SettingsMainToggle(
                title = "Scroll blocks horizontally",
                checked = state.scrollMonoBlocks,
                enabled = true,
                onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.SCROLL_MONO_BLOCKS, it) },
            )
            Text(
                text = "Swipe sideways to read wide code blocks, tables, and diagrams without wrapping their lines.",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = AppUiFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        item {
            SettingsCategoryHeading("Preview")
            MonoBlockPreview(example, state.toPreviewCommentRowStyle())
            SettingsCard {
                SegmentedSetting(
                    options = MonoExample.entries.map { it to it.label },
                    optionIcons = mapOf(
                        MonoExample.TABLE to Res.drawable.ic_data_table,
                        MonoExample.CODE to Res.drawable.ic_code,
                        MonoExample.DIAGRAM to Res.drawable.ic_account_tree,
                    ),
                    selected = example,
                    onSelected = { example = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MonoBlockPreview(example: MonoExample, style: CommentRowStyle) {
    val motion = MaterialTheme.motionScheme
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val resize = motion.defaultSpatialSpec<IntSize>()
    val enterFade = motion.defaultEffectsSpec<Float>()
    val exitFade = motion.fastEffectsSpec<Float>()
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val slideDistance = with(LocalDensity.current) { 24.dp.roundToPx() } * forward
    AnimatedContent(
        targetState = example to style.scrollMonoBlocks,
        transitionSpec = {
            val direction = targetState.first.ordinal.compareTo(initialState.first.ordinal)
            val enter = slideInHorizontally(slide) { direction * slideDistance } + fadeIn(enterFade)
            val exit = slideOutHorizontally(slide) { -direction * slideDistance } + fadeOut(exitFade)
            (enter togetherWith exit).using(SizeTransform { _, _ -> resize })
        },
        label = "code block preview",
    ) { (visibleExample, scrollBlocks) ->
        val comment = remember(visibleExample) {
            val html = "${visibleExample.intro}<p><pre><code>${visibleExample.block}</code></pre>"
            val body = StoryTextProcessor.preprocessHtml(html)
            PortableCommentItem(
                comment = CommentSnapshot(
                    id = -400 - visibleExample.ordinal,
                    author = visibleExample.author,
                    text = body,
                    expandedAnchorText = body,
                ),
                presentation = CommentPresentationSnapshot(expanded = true, depth = 0),
            )
        }
        CommentRow(
            comment = comment,
            style = style.copy(scrollMonoBlocks = scrollBlocks),
            storyAuthor = null,
            accountUser = null,
            userTag = null,
            subtreeReplyCount = 0,
            collapseParent = false,
            showTopLevelIndicator = false,
            enableLongClick = false,
            age = "1h",
            onToggleExpanded = {},
            onShowActions = {},
            onLinkLongClick = { _, _, _ -> },
            onReferenceLongClick = { _, _, _ -> },
        )
    }
}
