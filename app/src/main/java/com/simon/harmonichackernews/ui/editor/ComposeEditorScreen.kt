package com.simon.harmonichackernews.ui.editor

import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import kotlin.math.max
import kotlin.math.min

data class ComposeEditorSubmission(
    val title: String,
    val url: String,
    val text: String,
    val comment: String,
)

@Stable
class ComposeEditorController internal constructor(
    private val activity: ComponentActivity,
) {
    internal var submitting by mutableStateOf(false)

    fun setSubmitting(submitting: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this.submitting = submitting
        } else {
            activity.runOnUiThread { this.submitting = submitting }
        }
    }
}

private enum class EditorDialog {
    Information,
    Discard,
}

@Composable
internal fun ComposeEditorScreen(
    type: Int,
    parentText: String?,
    postTitle: String?,
    user: String?,
    titleMaxLength: Int,
    submitting: Boolean,
    onClose: () -> Unit,
    onSubmit: (ComposeEditorSubmission) -> Unit,
) {
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var url by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var comment by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }

    val isPost = type == ComposeEditorContract.TYPE_POST
    val titleTooLong = title.text.length > titleMaxLength
    val canSubmit = if (isPost) {
        title.text.isNotEmpty() && !titleTooLong &&
            (url.text.isNotEmpty() || text.text.isNotEmpty())
    } else {
        comment.text.isNotEmpty()
    }

    fun requestClose() {
        if (canSubmit) dialog = EditorDialog.Discard else onClose()
    }

    BackHandler(enabled = canSubmit && dialog == null) {
        dialog = EditorDialog.Discard
    }

    val topAndSideInsets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val bottomInsets = WindowInsets.navigationBars
        .union(WindowInsets.ime)
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .semantics { testTagsAsResourceId = true }
            .testTag("compose_editor_container")
            .windowInsetsPadding(topAndSideInsets)
            .padding(horizontal = dimensionResource(R.dimen.single_view_side_margin)),
    ) {
        ComposeEditorTopBar(
            type = type,
            subtitle = if (isPost) null else postTitle ?: parentText,
            onClose = ::requestClose,
        )

        if (type == ComposeEditorContract.TYPE_COMMENT_REPLY) {
            ReplyPreview(
                user = user.orEmpty(),
                parentText = parentText.orEmpty(),
            )
        }

        if (isPost) {
            PostFields(
                title = title,
                onTitleChange = { title = it },
                url = url,
                onUrlChange = { url = it },
                text = text,
                onTextChange = { text = it },
                titleMaxLength = titleMaxLength,
                titleTooLong = titleTooLong,
                modifier = Modifier.weight(1f),
            )
        } else {
            CommentField(
                value = comment,
                onValueChange = { comment = it },
                reply = type == ComposeEditorContract.TYPE_COMMENT_REPLY,
                modifier = Modifier.weight(1f),
            )
        }

        ComposeEditorBottomBar(
            enabled = canSubmit,
            submitting = submitting,
            onItalic = {
                if (isPost) text = applyItalicFormatting(text)
                else comment = applyItalicFormatting(comment)
            },
            onCode = {
                if (isPost) text = applyCodeBlockFormatting(text)
                else comment = applyCodeBlockFormatting(comment)
            },
            onInformation = { dialog = EditorDialog.Information },
            onSubmit = {
                if (canSubmit && !submitting) {
                    onSubmit(
                        ComposeEditorSubmission(
                            title = title.text,
                            url = url.text,
                            text = text.text,
                            comment = comment.text,
                        ),
                    )
                }
            },
            modifier = Modifier.windowInsetsPadding(bottomInsets),
        )
    }

    when (dialog) {
        EditorDialog.Information -> EditorMessageActionDialog(
            title = "Information",
            message = informationMessage(isPost),
            negativeLabel = "Dismiss",
            addTitleBodySpacing = true,
            onNegative = { dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Discard -> EditorMessageActionDialog(
            message = if (isPost) "Discard post?" else "Discard comment?",
            positiveLabel = "Discard",
            negativeLabel = "Cancel",
            onPositive = onClose,
            onNegative = { dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun EditorMessageActionDialog(
    message: String,
    title: String? = null,
    positiveLabel: String? = null,
    negativeLabel: String? = null,
    addTitleBodySpacing: Boolean = false,
    onPositive: () -> Unit = {},
    onNegative: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { value -> { SettingsDialogTitle(value) } },
        text = {
            Text(
                text = message,
                modifier = if (addTitleBodySpacing && title != null) {
                    Modifier.padding(top = 16.dp)
                } else {
                    Modifier
                },
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                style = includeFontPaddingStyle,
            )
        },
        confirmButton = {
            positiveLabel?.let { label ->
                SettingsDialogTextButton(onClick = onPositive) { Text(label) }
            }
        },
        dismissButton = {
            negativeLabel?.let { label ->
                SettingsDialogTextButton(onClick = onNegative) { Text(label) }
            }
        },
        edgeToEdgeContent = false,
        keepImeVisible = true,
    )
}

@Composable
private fun ComposeEditorTopBar(
    type: Int,
    subtitle: String?,
    onClose: () -> Unit,
) {
    val title = when (type) {
        ComposeEditorContract.TYPE_TOP_COMMENT -> "Top level comment"
        ComposeEditorContract.TYPE_COMMENT_REPLY -> "Posting reply"
        else -> "New post"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("compose_editor_top_app_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(56.dp)
                .testTag("compose_editor_close"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close",
                modifier = Modifier.size(24.dp),
                tint = HarmonicTheme.colors.storyNormal,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 16.dp)
                .semantics { heading() },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = includeFontPaddingStyle,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = HarmonicTheme.colors.textSecondary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = includeFontPaddingStyle,
                )
            }
        }
    }
}

@Composable
private fun ReplyPreview(
    user: String,
    parentText: String,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val previewHeight = with(density) {
        (windowHeight / 3f).toDp().coerceIn(112.dp, 180.dp)
    }
    val linkColor = HarmonicTheme.colors.link
    val linkStyles = remember(linkColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    val linkListener = remember(context) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                Utils.openLinkMaybeHN(context, annotation.url)
            }
        }
    }
    val formattedParent = remember(parentText, linkStyles, linkListener) {
        AnnotatedString.fromHtml(
            htmlString = parentText,
            linkStyles = linkStyles,
            linkInteractionListener = linkListener,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 6.dp)
            .testTag("compose_editor_replying_scrollview"),
    ) {
        Surface(
            modifier = Modifier
                .padding(start = 16.dp, top = 6.dp, end = 16.dp)
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp - 32.dp)
                .testTag("compose_editor_replying_header"),
            shape = RoundedCornerShape(28.dp),
            color = HarmonicTheme.colors.overlayButton,
            shadowElevation = 10.dp,
        ) {
            Text(
                text = "Replying to $user's comment:",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = includeFontPaddingStyle,
            )
        }
        Text(
            text = formattedParent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp)
                .testTag("compose_editor_replying_text"),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            style = includeFontPaddingStyle,
        )
    }
}

@Composable
private fun PostFields(
    title: TextFieldValue,
    onTitleChange: (TextFieldValue) -> Unit,
    url: TextFieldValue,
    onUrlChange: (TextFieldValue) -> Unit,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    titleMaxLength: Int,
    titleTooLong: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                .height(80.75.dp)
                .testTag("compose_editor_title"),
            label = { Text("Title") },
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (titleTooLong) {
                        Text(
                            text = "Title must be $titleMaxLength characters or less",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text("${title.text.length}/$titleMaxLength")
                }
            },
            isError = titleTooLong,
            textStyle = editorTextStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            singleLine = true,
            shape = editorFieldShape,
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .height(61.25.dp)
                .testTag("compose_editor_url"),
            label = { Text("URL") },
            textStyle = editorTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            shape = editorFieldShape,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .testTag("compose_editor_text"),
            label = { Text("Text") },
            textStyle = editorTextStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            shape = editorFieldShape,
        )
    }
}

@Composable
private fun CommentField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    reply: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = if (reply) 12.dp else 8.dp, end = 16.dp)
            .semantics {
                contentDescription = if (reply) "Reply text" else "Comment text"
            }
            .testTag("compose_editor_comment"),
        label = { Text(if (reply) "Reply" else "Comment") },
        textStyle = editorTextStyle,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
        ),
        shape = editorFieldShape,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposeEditorBottomBar(
    enabled: Boolean,
    submitting: Boolean,
    onItalic: () -> Unit,
    onCode: () -> Unit,
    onInformation: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp)
            .testTag("compose_editor_bottom_toolbar_row"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .width(160.dp)
                .height(64.dp)
                .testTag("compose_editor_formatting_toolbar"),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = HarmonicTheme.colors.settingsSegment,
                toolbarContentColor = HarmonicTheme.colors.drawable,
            ),
            expandedShadowElevation = 4.dp,
            collapsedShadowElevation = 4.dp,
        ) {
            FormattingButton(R.drawable.ic_format_italic, "Italic", onItalic)
            FormattingButton(R.drawable.ic_code_blocks, "Code block", onCode)
            FormattingButton(R.drawable.ic_info, "Information", onInformation)
        }
        SubmitButton(
            enabled = enabled,
            submitting = submitting,
            onClick = onSubmit,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun FormattingButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = HarmonicTheme.colors.drawable,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmitButton(
    enabled: Boolean,
    submitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabledBackground = HarmonicTheme.colors.accent
    val disabledTarget = if (ThemeUtils.isDarkMode(context)) Color.Black else Color.White
    val disabledBackground = lerp(enabledBackground, disabledTarget, 0.72f)
    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (enabled) enabledBackground else disabledBackground,
        animationSpec = tween(180),
        label = "editor submit background",
    )
    val content by androidx.compose.animation.animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onPrimary else enabledBackground,
        animationSpec = tween(180),
        label = "editor submit content",
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .testTag("compose_editor_submit_slot"),
        contentAlignment = Alignment.Center,
    ) {
        FloatingActionButton(
            onClick = { if (enabled && !submitting) onClick() },
            modifier = Modifier
                .size(60.dp)
                .then(
                    if (enabled && !submitting) Modifier
                    else Modifier.semantics { disabled() },
                )
                .testTag("compose_editor_submit"),
            shape = RoundedCornerShape(20.dp),
            containerColor = background,
            contentColor = content,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 4.dp,
                focusedElevation = 4.dp,
                hoveredElevation = 6.dp,
            ),
        ) {
            AnimatedContent(
                targetState = submitting,
                transitionSpec = {
                    fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                },
                label = "editor submitting",
            ) { loading ->
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(34.dp), color = content)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = "Submit",
                        modifier = Modifier.size(30.dp),
                        tint = content,
                    )
                }
            }
        }
    }
}

private fun applyItalicFormatting(value: TextFieldValue): TextFieldValue {
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    return if (start == end) {
        value.copy(
            text = value.text.substring(0, start) + "**" + value.text.substring(start),
            selection = TextRange(start + 1),
        )
    } else {
        value.copy(
            text = value.text.substring(0, start) + "*" +
                value.text.substring(start, end) + "*" + value.text.substring(end),
            selection = TextRange(start + 1, end + 1),
        )
    }
}

private fun applyCodeBlockFormatting(value: TextFieldValue): TextFieldValue {
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    if (start == end) {
        val prefix = codeBlockPrefix(value.text, start)
        val suffix = codeBlockSuffix(value.text, start)
        val snippet = prefix + "  code" + suffix
        return value.copy(
            text = value.text.substring(0, start) + snippet + value.text.substring(start),
            selection = TextRange(start + prefix.length + 2, start + prefix.length + 6),
        )
    }

    val lineStart = value.text.lastIndexOf('\n', startIndex = start - 1).let {
        if (it < 0) 0 else it + 1
    }
    val nextNewline = value.text.indexOf('\n', startIndex = end)
    val lineEnd = if (nextNewline < 0) value.text.length else nextNewline
    val prefix = codeBlockPrefix(value.text, lineStart)
    val indented = value.text.substring(lineStart, lineEnd)
        .split('\n')
        .joinToString("\n") { "  $it" }
    val suffix = codeBlockSuffix(value.text, lineEnd)
    return value.copy(
        text = value.text.substring(0, lineStart) + prefix + indented + suffix +
            value.text.substring(lineEnd),
        selection = TextRange(
            lineStart + prefix.length,
            lineStart + prefix.length + indented.length,
        ),
    )
}

private fun codeBlockPrefix(text: String, position: Int): String {
    if (position == 0) return ""
    val newlines = text.substring(0, position).takeLastWhile { it == '\n' }.length
    return when {
        newlines >= 2 -> ""
        newlines == 1 -> "\n"
        else -> "\n\n"
    }
}

private fun codeBlockSuffix(text: String, position: Int): String {
    if (position >= text.length) return ""
    val newlines = text.substring(position).takeWhile { it == '\n' }.length
    return when {
        newlines >= 2 -> ""
        newlines == 1 -> "\n"
        else -> "\n\n"
    }
}

private fun informationMessage(isPost: Boolean): String = buildString {
    if (isPost) {
        append(
            "Leave URL blank to submit a question for discussion. If there is no URL, " +
                "text will appear at the top of the thread. If there is a URL, text is optional.\n\n",
        )
    }
    append("Blank lines separate paragraphs.\n\n")
    append(
        "Text surrounded by asterisks is italicized, if the character after the first " +
            "asterisk isn't whitespace.\n\n",
    )
    append(
        "Text after a blank line that is indented by two or more spaces is reproduced " +
            "verbatim. (This is intended for code.)\n\n",
    )
    append("URLs become links, except in the text field of a submission.")
}

private val editorFieldShape = RoundedCornerShape(4.dp)

private val editorTextStyle = TextStyle(
    fontFamily = ProductSansFontFamily,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

private val includeFontPaddingStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Preview(name = "New post", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun NewPostPreview() {
    HarmonicTheme {
        ComposeEditorScreen(
            type = ComposeEditorContract.TYPE_POST,
            parentText = null,
            postTitle = null,
            user = null,
            titleMaxLength = 80,
            submitting = false,
            onClose = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Top-level comment", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun TopCommentPreview() {
    HarmonicTheme {
        ComposeEditorScreen(
            type = ComposeEditorContract.TYPE_TOP_COMMENT,
            parentText = "A sample story",
            postTitle = "A sample story",
            user = null,
            titleMaxLength = 80,
            submitting = false,
            onClose = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Comment reply", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun CommentReplyPreview() {
    HarmonicTheme {
        ComposeEditorScreen(
            type = ComposeEditorContract.TYPE_COMMENT_REPLY,
            parentText = "This is a sample comment with an <a href=\"https://example.com\">example link</a>.",
            postTitle = "A sample story",
            user = "pg",
            titleMaxLength = 80,
            submitting = false,
            onClose = {},
            onSubmit = {},
        )
    }
}
