package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.UserAvatarColors
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarShape
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_shuffle
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.resources.ic_palette
import org.jetbrains.compose.resources.painterResource
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.CommentRowModel
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay
import kotlin.random.Random

private val avatarPreviewNames = listOf(
    "pg", "dang", "sama", "simon", "alice", "bob", "willow", "compass",
    "otter", "jules", "carol", "dave", "eve", "fern", "mira", "atlas",
    "cedar", "robin", "pixel_fox", "cloudwalker", "raven", "kai", "sage", "river",
    "rowan", "nova", "juniper", "finch", "quinn", "ember", "hugo", "luna",
    "latent_space", "gradient", "show_hn", "ycombinator", "euler", "noether", "quark", "photon",
)

@Composable
fun UserAvatarSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    UserAvatarSettingsScreen(
        enabled = settings.comments.userAvatarsEnabled,
        options = settings.comments.userAvatarOptions,
        previewStyle = presenter.state(settings).toPreviewCommentRowStyle(),
        onEnabledChanged = repository::setUserAvatarsEnabled,
        onOptionsChanged = repository::setUserAvatarOptions,
        onBack = onBack,
    )
}

@Composable
fun UserAvatarSettingsScreen(
    enabled: Boolean,
    options: UserAvatarOptions,
    previewStyle: CommentRowStyle,
    onEnabledChanged: (Boolean) -> Unit,
    onOptionsChanged: (UserAvatarOptions) -> Unit,
    onBack: () -> Unit,
) {
    var exampleBatch by rememberSaveable { mutableIntStateOf(0) }
    // Only sample identities change on reroll; real users' images and preferences are untouched.
    val authors = remember(exampleBatch) {
        avatarPreviewNames.shuffled(Random(exampleBatch))
    }
    SettingsPage(
        title = "User profile images", showNavigation = true, onBack = onBack,
        headerContent = {
            SettingsMainToggle(
                "Show user profile images", checked = enabled, enabled = true,
                onCheckedChange = onEnabledChanged,
            )
        },
        pinnedContent = {
            Column(Modifier.testTag("avatar-comment-preview")) {
                repeat(3) { row ->
                    AvatarCommentPreview(
                        authors = authors, row = row,
                        style = previewStyle.copy(
                            userAvatarsEnabled = enabled,
                            userAvatarOptions = options,
                            depthIndicatorMode = "none",
                        ),
                    )
                }
            }
        },
    ) {
        item {
            SettingsCategory("Style") {
                SegmentedSetting(
                    options = listOf("generic" to "Generic", "expressive" to "Expressive"),
                    optionIcons = mapOf("generic" to Res.drawable.ic_person, "expressive" to Res.drawable.ic_palette),
                    selected = if (options.generic) "generic" else "expressive",
                    onSelected = { onOptionsChanged(options.copy(generic = it == "generic")) },
                )
                AnimatedVisibility(
                    visible = !options.generic,
                    enter = expandVertically(tween(300)) + fadeIn(tween(180, delayMillis = 60)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(120)),
                ) {
                    Column {
                        UserAvatarStyle.entries.forEach { style ->
                            SettingsDivider()
                            val selected = style in options.selectedStyles
                            val canToggle = !selected || options.selectedStyles.size > 1
                            Column(
                                Modifier.fillMaxWidth().background(itemBackgroundColor())
                                    .toggleable(
                                        value = selected, enabled = canToggle, role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            onOptionsChanged(options.copy(styles = if (checked)
                                                options.selectedStyles.toSet() + style else options.selectedStyles.toSet() - style))
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        style.label, modifier = Modifier.weight(1f),
                                        color = HarmonicTheme.colors.textPrimary,
                                        fontFamily = ProductSansFontFamily, fontSize = 16.sp,
                                    )
                                    Checkbox(checked = selected, onCheckedChange = null, enabled = canToggle)
                                }
                                AvatarSamples(options.copy(styles = setOf(style), generic = false), authors)
                            }
                        }
                        SettingsDivider()
                        Row(
                            Modifier.fillMaxWidth().background(itemBackgroundColor())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Button(onClick = { exampleBatch++ }, modifier = Modifier.fillMaxWidth()) {
                                Icon(painterResource(Res.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Reroll examples")
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsCategory("Customize") {
                SegmentedSetting(
                    title = "Frame shape",
                    options = UserAvatarShape.entries.map { it.name to it.label },
                    selected = options.shape.name,
                    onSelected = { onOptionsChanged(options.copy(shape = UserAvatarShape.valueOf(it))) },
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Colors",
                    options = UserAvatarColors.entries.map { it.name to it.label },
                    selected = options.colors.name,
                    enabled = !options.generic,
                    onSelected = { onOptionsChanged(options.copy(colors = UserAvatarColors.valueOf(it))) },
                )
            }
        }
    }
}

@Composable
private fun AvatarCommentPreview(authors: List<String>, row: Int, style: CommentRowStyle) {
    var exampleIndex by remember(authors, row) { mutableIntStateOf(row) }
    LaunchedEffect(authors, style.userAvatarsEnabled, style.userAvatarOptions.generic) {
        if (style.userAvatarsEnabled && !style.userAvatarOptions.generic) {
            delay(2_000L + row * 667L)
            while (true) {
                exampleIndex = (exampleIndex + 3) % authors.size
                delay(2_000)
            }
        }
    }
    CommentRow(
        model = CommentRowModel(
            author = authors[exampleIndex], age = "1h",
            body = when (row) {
                0 -> "Small details make a difference."
                1 -> "I like seeing different perspectives."
                else -> "A little personality goes a long way."
            },
            referenceMarker = "", referenceUrl = "",
        ),
        style = style,
        modifier = Modifier.testTag("avatar-comment-preview-$row"),
        verticalPadding = if (style.hasBackground) 0.dp else 8.dp,
    )
}

@Composable
private fun AvatarSamples(options: UserAvatarOptions, authors: List<String>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // Give each 32dp image at least 48dp of space, then distribute the spare width evenly.
        val sampleCount = (maxWidth / 48.dp).toInt().coerceIn(1, authors.size)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            authors.take(sampleCount).forEach { author ->
                UserAvatar(author, Modifier.size(32.dp), options)
            }
        }
    }
}
