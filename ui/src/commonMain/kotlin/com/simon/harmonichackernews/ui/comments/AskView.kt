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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.summary.AskConversation
import com.simon.harmonichackernews.summary.AskSource
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.settings.SettingsSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AskSurface(
    controller: CommentsScreenController,
    subject: AskSource,
    origin: Rect,
    progress: Float,
    source: GraphicsLayer,
    settings: CommentDisplaySettings,
    color: Color,
    onOpenLink: (String) -> Unit,
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val comment = (subject as? AskSource.Comment)?.comment
    val post = subject as? AskSource.Post
    val sourceKey = comment?.id ?: subject.story.id
    val baseUrl = if (post != null) subject.story.url else "https://news.ycombinator.com/item?id=${comment?.id}"
    val scope = rememberCoroutineScope()
    val discussion = remember(subject) {
        AskConversation(
            scope, dependencies.network.hackerNewsApi,
            dependencies.network.summaryUseCase, dependencies.aiSummarySettings,
            dependencies.localSummaryEngine, subject,
            mockAnswers = { dependencies.userSettings.debug.mockAiAnswers },
            builtInModelSelected = { dependencies.localModels?.selectedModel?.downloadable == false },
        )
    }
    val state by discussion.state.collectAsState()
    var draft by remember(subject) { mutableStateOf("") }
    var expanded by remember(subject) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val typography = rememberContentTypography(settings.font, settings.preferredTextSize)
    val questions = remember(subject) { AskConversation.suggestedQuestions(subject) }
    val remainingQuestions = remember(questions, state.suggestedQuestions, state.turns) {
        remainingDiscussionQuestions(questions + state.suggestedQuestions, state.turns.map { it.question })
    }
    val revealedTurns = remember(subject) { mutableSetOf<Int>() }
    val resetAlpha = remember(subject) { Animatable(1f) }
    val density = LocalDensity.current
    var resetting by remember(subject) { mutableStateOf(false) }
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
            if (discussion.state.value.turns.isNotEmpty() && followOutput && !scroll.isScrollInProgress) scroll.scrollTo(end)
        }
    }
    LaunchedEffect(state.turns.size) { followOutput = true }
    LaunchedEffect(controller.askOpen) {
        if (!controller.askOpen) keyboard?.hide()
    }
    LaunchedEffect(controller.askOpen, state.turns.isEmpty()) {
        if (controller.askOpen && state.turns.isEmpty()) discussion.generateSuggestedQuestions()
        else discussion.cancelSuggestedQuestions()
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
                resetAlpha.animateTo(1f, tween(220, easing = AskEasing))
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

    AskContainer(origin, progress, color, source, sourceCornerRadius = if (post != null) 14.dp else 28.dp,
        summarySource = post != null) {
        AskLayout(
            header = {
                HarmonicTopAppBar(
                    title = if (post != null) "Ask about this post" else "Ask about this comment",
                    onBack = controller::closeAsk,
                    navigationContentDescription = if (post != null) "Back to summary" else "Back to comment",
                    toolbarHeight = 64.dp * density.fontScale.coerceAtLeast(1f),
                    navigationContainerColor = lerp(color, HarmonicTheme.colors.onSurface, 0.06f),
                )
            },
            composer = { modifier ->
                AskComposer(
                    draft = draft,
                    onDraftChanged = { draft = it },
                    hasTurns = state.turns.isNotEmpty(),
                    running = state.running,
                    enabled = !resetting,
                    onSend = ::send,
                    onStop = discussion::stop,
                    surfaceColor = color,
                    // Include the settling animation after cancellation, not just gesture progress.
                    showCursor = controller.askOpen && controller.askBackProgress == 0f && progress == 1f,
                    modifier = modifier,
                )
            },
        ) { composerHeight ->
            Column(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = resetAlpha.value
                    translationY = (1f - resetAlpha.value) * 8.dp.toPx()
                }.verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, bottom = composerHeight),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val linkColor = HarmonicTheme.colors.link
                val body = remember(comment?.expandedAnchorText, linkColor, onOpenLink) {
                    htmlAnnotatedString(comment?.expandedAnchorText.orEmpty(), linkColor,
                        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(onOpenLink) })
                }
                val caretRotation by animateFloatAsState(
                    if (expanded) 180f else 0f,
                    tween(260, easing = AskEasing), label = "Comment caret",
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(HarmonicTheme.colors.contentPrimary.copy(alpha = 0.05f))
                        .clickable(role = Role.Button, onClickLabel = if (post != null) "Toggle summary preview" else "Toggle comment preview") { expanded = !expanded }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .animateContentSize(tween(300, easing = AskEasing))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (settings.userAvatarsEnabled && !comment?.by.isNullOrBlank()) {
                            UserAvatar(author = comment.by!!, options = settings.userAvatarOptions,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        if (post != null) {
                            Icon(painterResource(Res.drawable.ic_auto_awesome), null, Modifier.size(18.dp),
                                tint = HarmonicTheme.colors.link)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (post != null) "AI summary" else comment?.by ?: "Unknown user", Modifier.weight(1f),
                            color = HarmonicTheme.colors.link, fontFamily = typography.family,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null,
                            Modifier.size(20.dp).graphicsLayer { rotationZ = caretRotation },
                            tint = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.65f))
                    }
                    Spacer(Modifier.height(6.dp))
                    if (post != null) {
                        Text(subject.story.title.orEmpty(), fontFamily = typography.family,
                            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                            color = HarmonicTheme.colors.contentPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        SummaryMarkdownText(
                            markdown = post.summary,
                            color = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.8f),
                            linkColor = linkColor, fontFamily = typography.family,
                            fontSize = 13.sp, lineHeight = 18.sp, onOpenLink = onOpenLink,
                            baseUrl = baseUrl, maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            enableBoldFormatting = settings.enableSummaryBoldFormatting,
                        )
                    } else {
                        Text(body, color = HarmonicTheme.colors.contentPrimary.copy(alpha = 0.8f),
                            fontFamily = typography.family, fontSize = 13.sp, lineHeight = 18.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(
                    visible = state.turns.isEmpty(),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(240, easing = AskEasing)),
                ) {
                    var fixedQuestionsRevealed by remember { mutableStateOf(false) }
                    val show = progress >= 0.65f && controller.askOpen
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What would you like to understand?",
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                            color = HarmonicTheme.colors.contentPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        questions.forEachIndexed { index, question ->
                            DiscussionQuestionButton(
                                question = question,
                                show = show,
                                index = index,
                                enabled = !resetting,
                                fontFamily = typography.family,
                                onClick = { discussion.ask(question) },
                                onRevealed = { if (index == questions.lastIndex) fixedQuestionsRevealed = true },
                            )
                        }
                        // The completion callback uses the animation clock, including system motion
                        // scaling, so even an immediate model response waits for the fixed questions.
                        AnimatedVisibility(
                            visible = fixedQuestionsRevealed &&
                                (state.loadingSuggestions || state.suggestedQuestions.isNotEmpty()),
                            enter = fadeIn(tween(220)) + expandVertically(tween(300, easing = AskEasing)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(220)),
                        ) {
                            Column(Modifier.animateContentSize(tween(300, easing = AskEasing)),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.suggestedQuestions.isEmpty()) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center) {
                                        HarmonicLoadingIndicator(modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    state.suggestedQuestions.forEachIndexed { index, question ->
                                        key(question) {
                                            DiscussionQuestionButton(
                                                question = question,
                                                show = show,
                                                index = index,
                                                enabled = !resetting,
                                                fontFamily = typography.family,
                                                generated = true,
                                                onClick = { discussion.ask(question) },
                                            )
                                        }
                                    }
                                }
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
                                expandVertically(tween(260, easing = AskEasing), Alignment.Top) +
                                slideInVertically(tween(260, easing = AskEasing)) { it / 4 },
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Top) {
                                Text(turn.question, Modifier.weight(1f, fill = false)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(HarmonicTheme.colors.overlayButton).padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = HarmonicTheme.colors.overlayButtonContent,
                                    fontFamily = typography.family,
                                    fontSize = typography.commentTextSize.sp,
                                    lineHeight = (typography.commentTextSize + 2f).sp)
                            }
                        }
                        // An empty answer must not reserve a text line or an extra item gap.
                        // Once text arrives, retain the renderer through streaming and completion.
                        if (turn.answer.isNotBlank()) {
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
                                baseUrl = baseUrl,
                                animateStreamingText = state.running && index == state.turns.lastIndex,
                                animationContentKey = sourceKey to index,
                            )
                        }
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
                    val presentation = discussionErrorPresentation(error, state.contextLimitReached)
                    AskErrorCard(
                        presentation = presentation,
                        enabled = !resetting,
                        onAction = {
                            when (presentation.action) {
                                DiscussionErrorAction.OpenSettings -> {
                                    keyboard?.hide()
                                    dependencies.navigation.openSettings(SettingsSection.AiSummary.route)
                                }
                                DiscussionErrorAction.Reset -> reset()
                                DiscussionErrorAction.Retry -> discussion.retry()
                            }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = state.turns.isNotEmpty() && !state.running && remainingQuestions.isNotEmpty(),
                    enter = fadeIn(tween(180)) + expandVertically(tween(240, easing = AskEasing)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("More questions", Modifier.padding(top = 4.dp, bottom = 6.dp),
                            color = HarmonicTheme.colors.contentPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        remainingQuestions.forEachIndexed { index, question ->
                            key(question) {
                                DiscussionQuestionButton(
                                    question = question,
                                    show = progress >= 0.65f && controller.askOpen,
                                    index = index,
                                    enabled = !resetting && !state.running,
                                    fontFamily = typography.family,
                                    generated = question !in questions,
                                    onClick = {
                                        keyboard?.hide()
                                        discussion.ask(question)
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}


@Composable
private fun DiscussionQuestionButton(
    question: String,
    show: Boolean,
    index: Int,
    enabled: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit,
    generated: Boolean = false,
    onRevealed: () -> Unit = {},
) {
    val entrance = remember(question) { Animatable(0f) }
    val currentOnRevealed by rememberUpdatedState(onRevealed)
    LaunchedEffect(show) {
        if (show) {
            if (entrance.value < 1f) {
                delay(index * 160L)
                entrance.animateTo(1f, tween(420, easing = AskEasing))
            }
            currentOnRevealed()
        }
    }
    val offset = with(LocalDensity.current) { 12.dp.toPx() }
    OutlinedButton(
        onClick = onClick,
        enabled = entrance.value > 0.9f && enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).graphicsLayer {
            alpha = entrance.value
            translationY = offset * (1f - entrance.value)
        },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HarmonicTheme.colors.contentPrimary),
    ) {
        val questionIcon = when {
            generated || question == "Explain this comment" -> Res.drawable.ic_auto_awesome
            "example" in question -> Res.drawable.ic_preview
            "Summarize" in question -> Res.drawable.ic_subject
            "technical" in question -> Res.drawable.ic_code_blocks
            "parent" in question -> Res.drawable.ic_forum
            else -> Res.drawable.ic_live_help
        }
        Icon(painterResource(questionIcon), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(question, Modifier.weight(1f), fontFamily = fontFamily,
            fontSize = 14.sp, lineHeight = 19.sp)
    }
}

/** Keep unused starter and generated questions available after each turn. */
internal fun remainingDiscussionQuestions(suggestions: List<String>, asked: List<String>): List<String> {
    val askedQuestions = asked.map { it.trim().lowercase() }.toSet()
    return suggestions.distinctBy { it.trim().lowercase() }
        .filter { it.isNotBlank() && it.trim().lowercase() !in askedQuestions }
}
