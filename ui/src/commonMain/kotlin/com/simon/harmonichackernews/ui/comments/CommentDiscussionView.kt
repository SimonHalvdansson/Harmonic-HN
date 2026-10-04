package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.summary.CommentDiscussion
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CommentDiscussionSurface(
    controller: CommentsScreenController,
    origin: Rect,
    progress: Float,
    source: GraphicsLayer,
    settings: CommentDisplaySettings,
    color: Color,
    onOpenLink: (String) -> Unit,
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val comment = controller.commentActionOverlay?.comment ?: return
    val scope = rememberCoroutineScope()
    val discussion = remember(comment.id) {
        CommentDiscussion(
            scope, dependencies.network.hackerNewsApi,
            dependencies.network.summaryUseCase, dependencies.aiSummarySettings,
            dependencies.localSummaryEngine, controller.story, comment, controller.comments.toList(),
            mockAnswers = { dependencies.userSettings.debug.mockAiAnswers },
        )
    }
    val state by discussion.state.collectAsState()
    var draft by remember(comment.id) { mutableStateOf("") }
    var expanded by remember(comment.id) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val typography = rememberContentTypography(settings.font, settings.preferredTextSize)
    val questions = remember(comment.id) { CommentDiscussion.suggestedQuestions(comment) }
    val revealedTurns = remember(comment.id) { mutableSetOf<Int>() }
    val resetAlpha = remember(comment.id) { Animatable(1f) }
    var resetting by remember(comment.id) { mutableStateOf(false) }
    val followThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    var followOutput by remember { mutableStateOf(true) }
    // Follow layout growth, not each network chunk: repeated smooth-scroll animations made
    // the existing summary glyph fades look as though the entire answer was moving.
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress to scroll.value }.collect {
            if (scroll.isScrollInProgress) followOutput = scroll.maxValue - scroll.value < followThreshold
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.maxValue }.distinctUntilChanged().collect { end ->
            if (followOutput && !scroll.isScrollInProgress) scroll.scrollTo(end)
        }
    }
    LaunchedEffect(state.turns.size) { followOutput = true }
    LaunchedEffect(controller.commentDiscussionOpen) {
        if (!controller.commentDiscussionOpen) keyboard?.hide()
    }
    fun reset() {
        if (resetting) return
        resetting = true
        discussion.stop()
        keyboard?.hide()
        scope.launch {
            try {
                resetAlpha.animateTo(0f, tween(140))
                discussion.reset()
                revealedTurns.clear()
                draft = ""
                followOutput = true
                scroll.scrollTo(0)
                withFrameNanos { }
                resetAlpha.animateTo(1f, tween(220, easing = CommentDiscussionEasing))
            } finally {
                resetting = false
                resetAlpha.snapTo(1f)
            }
        }
    }
    fun send() {
        if (draft.isBlank() || state.running || resetting) return
        discussion.ask(draft)
        draft = ""
        keyboard?.hide()
    }

    CommentDiscussionContainer(origin, progress, color, source) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = controller::closeCommentDiscussion) {
                    Icon(painterResource(Res.drawable.ic_arrow_back), "Back to comment",
                        tint = HarmonicTheme.colors.contentPrimary)
                }
                Text("Ask about this comment", Modifier.weight(1f),
                    color = HarmonicTheme.colors.contentPrimary,
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = ::reset, enabled = !resetting && (state.turns.isNotEmpty() || draft.isNotBlank())) {
                    Icon(painterResource(Res.drawable.ic_refresh), "Reset discussion",
                        tint = HarmonicTheme.colors.contentPrimary.copy(alpha =
                            if (!resetting && (state.turns.isNotEmpty() || draft.isNotBlank())) 1f else 0.38f))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                    alpha = resetAlpha.value
                    translationY = (1f - resetAlpha.value) * 8.dp.toPx()
                }.verticalScroll(scroll).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val linkColor = HarmonicTheme.colors.link
                val body = remember(comment.expandedAnchorText, linkColor, onOpenLink) {
                    htmlAnnotatedString(comment.expandedAnchorText.orEmpty(), linkColor,
                        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(onOpenLink) })
                }
                val caretRotation by animateFloatAsState(
                    if (expanded) 180f else 0f,
                    tween(260, easing = CommentDiscussionEasing), label = "Comment caret",
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(HarmonicTheme.colors.contentPrimary.copy(alpha = 0.05f))
                        .clickable(role = Role.Button, onClickLabel = "Toggle comment preview") { expanded = !expanded }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .animateContentSize(tween(300, easing = CommentDiscussionEasing))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (settings.userAvatarsEnabled && !comment.by.isNullOrBlank()) {
                            UserAvatar(author = comment.by!!, options = settings.userAvatarOptions,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(comment.by ?: "Unknown user", Modifier.weight(1f),
                            color = HarmonicTheme.colors.link, fontFamily = typography.family,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null,
                            Modifier.size(20.dp).graphicsLayer { rotationZ = caretRotation },
                            tint = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.65f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(body, color = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.8f),
                        fontFamily = typography.family, fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(
                    visible = state.turns.isEmpty(),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(240, easing = CommentDiscussionEasing)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What would you like to understand?",
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                            color = HarmonicTheme.colors.contentPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        questions.forEachIndexed { index, question ->
                            val entrance = remember(question) { Animatable(0f) }
                            val show = progress >= 0.65f && controller.commentDiscussionOpen
                            LaunchedEffect(show) {
                                if (show) {
                                    delay(index * 65L)
                                    entrance.animateTo(1f, tween(280, easing = CommentDiscussionEasing))
                                }
                            }
                            val offset = with(LocalDensity.current) { 12.dp.toPx() }
                            OutlinedButton(
                                onClick = { discussion.ask(question) },
                                enabled = entrance.value > 0.9f && !resetting,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).graphicsLayer {
                                    alpha = entrance.value
                                    translationY = offset * (1f - entrance.value)
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                val questionIcon = when {
                                    question == "Explain this comment" -> Res.drawable.ic_auto_awesome
                                    "example" in question -> Res.drawable.ic_preview
                                    "Summarize" in question -> Res.drawable.ic_subject
                                    "technical" in question -> Res.drawable.ic_code_blocks
                                    "parent" in question -> Res.drawable.ic_forum
                                    else -> Res.drawable.ic_live_help
                                }
                                Icon(painterResource(questionIcon), null, Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(question, Modifier.weight(1f), fontFamily = typography.family,
                                    fontSize = 14.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
                state.turns.forEachIndexed { index, turn ->
                    key(index) {
                        var entered by remember { mutableStateOf(index in revealedTurns) }
                        LaunchedEffect(Unit) {
                            revealedTurns.add(index)
                            entered = true
                        }
                        AnimatedVisibility(
                            entered,
                            enter = fadeIn(tween(180)) +
                                expandVertically(tween(260, easing = CommentDiscussionEasing), Alignment.Top) +
                                slideInVertically(tween(260, easing = CommentDiscussionEasing)) { it / 4 },
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Top) {
                                Text(turn.question, Modifier.weight(1f, fill = false)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(HarmonicTheme.colors.overlayButton).padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = HarmonicTheme.colors.overlayButtonContent,
                                    fontFamily = typography.family,
                                    fontSize = typography.commentTextSize.sp)
                                val accountUser = controller.accountUser
                                if (settings.userAvatarsEnabled && !accountUser.isNullOrBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    UserAvatar(author = accountUser, options = settings.userAvatarOptions, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        // Keep the renderer mounted from the first empty chunk through completion,
                        // using the same glyph-fade implementation and typography as story summaries.
                        SelectionContainer {
                            SummaryMarkdownText(
                                markdown = turn.answer,
                                color = HarmonicTheme.colors.contentPrimary,
                                linkColor = HarmonicTheme.colors.link,
                                fontFamily = typography.family,
                                fontSize = typography.commentTextSize.sp,
                                lineHeight = (typography.commentTextSize + 2f).sp,
                                enableBoldFormatting = settings.enableSummaryBoldFormatting,
                                onOpenLink = onOpenLink,
                                baseUrl = "https://news.ycombinator.com/item?id=${comment.id}",
                                animateStreamingText = state.running && index == state.turns.lastIndex,
                                animationContentKey = comment.id to index,
                            )
                        }
                    }
                }
                if (state.running && state.turns.lastOrNull()?.answer.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HarmonicLoadingIndicator(modifier = Modifier.size(18.dp))
                        Text("Thinking…", Modifier.padding(start = 10.dp),
                            color = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.65f),
                            fontSize = 13.sp)
                    }
                }
                state.error?.let { error ->
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        if (state.contextLimitReached) {
                            TextButton(onClick = ::reset, enabled = !resetting) { Text("Reset discussion") }
                        } else {
                            TextButton(onClick = discussion::retry, enabled = !resetting) { Text("Retry") }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, enabled = !resetting,
                    placeholder = { Text(if (state.turns.isEmpty()) "Ask a question…" else "Ask a follow-up…") },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed) {
                            if (event.type == KeyEventType.KeyDown) send()
                            true
                        } else false
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(24.dp), maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HarmonicTheme.colors.contentPrimary,
                        unfocusedTextColor = HarmonicTheme.colors.contentPrimary,
                    ),
                )
                IconButton(
                    enabled = !resetting && (state.running || draft.isNotBlank()),
                    onClick = { if (state.running) discussion.stop() else send() },
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    if (state.running) {
                        Icon(painterResource(Res.drawable.ic_stop), "Stop response",
                            tint = HarmonicTheme.colors.link)
                    } else {
                        Icon(painterResource(Res.drawable.ic_send), "Send question",
                            tint = HarmonicTheme.colors.link.copy(alpha = if (draft.isNotBlank()) 1f else 0.38f))
                    }
                }
            }
        }
    }
}
