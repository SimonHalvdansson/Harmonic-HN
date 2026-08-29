package com.simon.harmonichackernews.ui.editor

import org.jetbrains.compose.resources.DrawableResource
import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import com.simon.harmonichackernews.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorPolicy
import com.simon.harmonichackernews.presentation.formatEditorCodeBlock
import com.simon.harmonichackernews.presentation.formatEditorItalic
import com.simon.harmonichackernews.presentation.validate

private enum class EditorDialog {
    Information,
    Discard,
}

private enum class PostEditorField {
    Title,
    Url,
    Text,
}

@Stable
class EditorComposeController {
    var submitting by mutableStateOf(false)
        private set

    fun updateSubmitting(value: Boolean) {
        submitting = value
    }
}

@Composable
fun EditorScreen(
    type: EditorType,
    parentText: String?,
    postTitle: String?,
    user: String?,
    submitting: Boolean,
    backRequestVersion: Int = 0,
    onClose: () -> Unit,
    onSubmit: (EditorSubmission) -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    val titleMaxLength = EditorPolicy.TITLE_MAX_LENGTH
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
    var focusedPostField by remember { mutableStateOf<PostEditorField?>(null) }
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    var discardConfirmed by rememberSaveable { mutableStateOf(false) }

    // Let the dialog leave composition before starting the editor's parent exit transition.
    LaunchedEffect(discardConfirmed) {
        if (discardConfirmed) onClose()
    }

    val isPost = type == EditorType.POST
    val submission = EditorSubmission(title.text, url.text, text.text, comment.text)
    val validation = submission.validate(type, titleMaxLength)
    val titleTooLong = validation.titleTooLong
    val canSubmit = validation.canSubmit

    fun requestClose() {
        if (canSubmit) dialog = EditorDialog.Discard else onClose()
    }

    LaunchedEffect(backRequestVersion) {
        if (backRequestVersion > 0 && dialog == null) requestClose()
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
            .testTag("compose_editor_container")
            .windowInsetsPadding(topAndSideInsets)
            .padding(horizontal = 0.dp),
    ) {
        ComposeEditorTopBar(
            type = type,
            subtitle = if (isPost) null else postTitle ?: parentText,
            onClose = ::requestClose,
        )

        if (type == EditorType.COMMENT_REPLY) {
            ReplyPreview(
                user = user.orEmpty(),
                parentText = parentText.orEmpty(),
                onOpenLink = onOpenLink,
            )
        }

        if (isPost) {
            KeepImeOpenDuringFieldHandoff {
                PostFields(
                    title = title,
                    onTitleChange = { title = it },
                    url = url,
                    onUrlChange = { url = it },
                    text = text,
                    onTextChange = { text = it },
                    onFieldFocusChange = { field, isFocused ->
                        if (isFocused) {
                            focusedPostField = field
                        } else if (focusedPostField == field) {
                            focusedPostField = null
                        }
                    },
                    titleMaxLength = titleMaxLength,
                    titleTooLong = titleTooLong,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            CommentField(
                value = comment,
                onValueChange = { comment = it },
                reply = type == EditorType.COMMENT_REPLY,
                modifier = Modifier.weight(1f),
            )
        }

        ComposeEditorBottomBar(
            submitEnabled = canSubmit,
            formattingEnabled = !isPost ||
                focusedPostField == null ||
                focusedPostField == PostEditorField.Text,
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
                        submission,
                    )
                }
            },
        )

        // Match the Views editor's animated inset spacer: as the IME reports each animation
        // frame, the spacer changes height and the weighted editor content moves with it.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(bottomInsets),
        )
    }

    when (dialog) {
        EditorDialog.Information -> EditorMessageActionDialog(
            title = "Information",
            message = informationMessage(isPost),
            negativeLabel = "Dismiss",
            addTitleBodySpacing = true,
            keepImeVisible = true,
            onNegative = { dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Discard -> EditorMessageActionDialog(
            message = if (isPost) "Discard post?" else "Discard comment?",
            positiveLabel = "Discard",
            negativeLabel = "Cancel",
            onPositive = {
                dialog = null
                discardConfirmed = true
            },
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
    keepImeVisible: Boolean = false,
    onPositive: () -> Unit = {},
    onNegative: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { value ->
            {
                if (keepImeVisible) EditorInformationDialogImeBehavior()
                Text(
                    text = value,
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                )
            }
        },
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
                TextButton(onClick = onPositive) { Text(label) }
            }
        },
        dismissButton = {
            negativeLabel?.let { label ->
                TextButton(onClick = onNegative) { Text(label) }
            }
        },
    )
}

@Composable
private fun ComposeEditorTopBar(
    type: EditorType,
    subtitle: String?,
    onClose: () -> Unit,
) {
    val title = when (type) {
        EditorType.TOP_LEVEL_COMMENT -> "Top level comment"
        EditorType.COMMENT_REPLY -> "Posting reply"
        EditorType.POST -> "New post"
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
                painter = painterResource(Res.drawable.ic_close),
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
    onOpenLink: (String) -> Unit,
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeight = windowSize.height
    val previewHeight = with(density) {
        (windowHeight / 3f).toDp().coerceIn(112.dp, 180.dp)
    }
    val linkColor = HarmonicTheme.colors.link
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                onOpenLink(annotation.url)
            }
        }
    }
    val formattedParent = remember(parentText, linkColor, linkListener) {
        htmlAnnotatedString(parentText, linkColor, linkListener)
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
                .widthIn(max = with(density) { windowSize.width.toDp() } - 32.dp)
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
    onFieldFocusChange: (PostEditorField, Boolean) -> Unit,
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
                .onFocusChanged { onFieldFocusChange(PostEditorField.Title, it.isFocused) }
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
                .onFocusChanged { onFieldFocusChange(PostEditorField.Url, it.isFocused) }
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
                .onFocusChanged { onFieldFocusChange(PostEditorField.Text, it.isFocused) }
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

@Composable
private fun ComposeEditorBottomBar(
    submitEnabled: Boolean,
    formattingEnabled: Boolean,
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
        Surface(
            modifier = Modifier
                .width(160.dp)
                .height(64.dp)
                .testTag("compose_editor_formatting_toolbar"),
            shape = RoundedCornerShape(32.dp),
            color = HarmonicTheme.colors.settingsSegment,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormattingButton(
                    icon = Res.drawable.ic_format_italic,
                    description = "Italic",
                    enabled = formattingEnabled,
                    onClick = onItalic,
                )
                FormattingButton(
                    icon = Res.drawable.ic_code_blocks,
                    description = "Code block",
                    enabled = formattingEnabled,
                    onClick = onCode,
                )
                FormattingButton(
                    icon = Res.drawable.ic_info,
                    description = "Information",
                    onClick = onInformation,
                )
            }
        }
        EditorTooltip("Submit") {
            SubmitButton(
                enabled = submitEnabled,
                submitting = submitting,
                onClick = onSubmit,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun FormattingButton(
    icon: DrawableResource,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EditorTooltip(description) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(24.dp),
                tint = HarmonicTheme.colors.drawable.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTooltip(
    description: String,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState,
        content = content,
    )
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    submitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonicTheme.colors
    val enabledBackground = colors.accent
    val isDarkTheme = colors.background.luminance() < colors.onSurface.luminance()
    val disabledTarget = if (isDarkTheme) Color.Black else Color.White
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
                    HarmonicLoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = content,
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
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
    val edit = formatEditorItalic(value.text, value.selection.start, value.selection.end)
    return value.copy(
        text = edit.text,
        selection = TextRange(edit.selectionStart, edit.selectionEnd),
    )
}

private fun applyCodeBlockFormatting(value: TextFieldValue): TextFieldValue {
    val edit = formatEditorCodeBlock(value.text, value.selection.start, value.selection.end)
    return value.copy(
        text = edit.text,
        selection = TextRange(edit.selectionStart, edit.selectionEnd),
    )
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

private val editorTextStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily = ProductSansFontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

private val includeFontPaddingStyle = TextStyle()
