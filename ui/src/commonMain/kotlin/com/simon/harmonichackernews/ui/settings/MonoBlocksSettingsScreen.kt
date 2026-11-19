package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_account_tree
import com.simon.harmonichackernews.resources.ic_code
import com.simon.harmonichackernews.resources.ic_data_table
import com.simon.harmonichackernews.resources.settings_section_mono_blocks
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowModel
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
    val preview = remember(example) {
        val html = "${example.intro}<p><pre><code>${example.block}</code></pre>"
        CommentRowModel(
            author = example.author,
            age = "1h",
            body = StoryTextProcessor.preprocessHtml(html).orEmpty(),
            referenceMarker = "",
            referenceUrl = "",
        )
    }
    val title = stringResource(Res.string.settings_section_mono_blocks)
    SettingsPage(title = title, showNavigation = true, onBack = onBack) {
        item {
            SettingsMainToggle(
                title = "Scroll blocks horizontally",
                checked = state.scrollMonoBlocks,
                enabled = true,
                onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.SCROLL_MONO_BLOCKS, it) },
            )
        }
        item {
            SettingsCategory("Preview") {
                key(example) {
                    CommentRow(
                        model = preview,
                        style = state.toPreviewCommentRowStyle().copy(depthIndicatorMode = CommentDepthPreferences.NONE),
                    )
                }
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
