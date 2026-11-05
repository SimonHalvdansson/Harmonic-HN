@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

internal const val AskQuestionMorphDuration = 500

internal class AskQuestionMorph(val question: String, val turnIndex: Int, val outgoingQuestions: List<String>) {
    val progress = Animatable(0f)
    var inFlight by mutableStateOf(true)

    suspend fun animate() {
        // Start alongside the shared bounds after the new turn enters composition.
        withFrameNanos { }
        progress.animateTo(1f, tween(AskQuestionMorphDuration, easing = AskEasing))
    }
}

@Composable
internal fun Modifier.askQuestionBounds(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    question: String,
    text: Boolean = false,
    incoming: Boolean = false,
    morph: AskQuestionMorph? = null,
    backdrop: Color? = null,
): Modifier = with(shared) {
    val state = rememberSharedContentState("ask-question-${if (text) "text" else "surface"}-$question")
    sharedBounds(
        state,
        animatedVisibilityScope = visibility,
        boundsTransform = BoundsTransform { _, _ -> tween(AskQuestionMorphDuration, easing = AskEasing) },
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        // Keep the outgoing icon on its moving surface, with the single text layer above both.
        zIndexInOverlay = if (text) 2f else if (incoming) 0f else 1f,
        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
    ).then(if (text) Modifier.graphicsLayer { alpha = if (!incoming && state.isMatchFound) 0f else 1f } else Modifier)
        .then(if (backdrop != null) Modifier.askQuestionSurface(morph, incoming, backdrop,
            visible = { incoming || !state.isMatchFound }) else Modifier)
}

@Composable
internal fun Modifier.askQuestionSurface(
    morph: AskQuestionMorph?,
    sent: Boolean,
    backdrop: Color,
    visible: () -> Boolean = { true },
): Modifier {
    val bubble = askUserBubbleColors(MaterialTheme.colorScheme).first
    val outline = MaterialTheme.colorScheme.outlineVariant
    return drawWithCache {
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            if (!visible()) return@onDrawBehind
            val p = morph?.progress?.value ?: if (sent) 1f else 0f
            val radius = (16f + 2f * p).dp.toPx()
            drawRoundRect(lerp(backdrop, bubble, p), cornerRadius = CornerRadius(radius))
            if (p < 1f) drawRoundRect(outline.copy(alpha = outline.alpha * (1f - p)),
                cornerRadius = CornerRadius(radius), style = stroke)
        }
    }
}

internal fun Modifier.askQuestionIcon(morph: AskQuestionMorph?): Modifier =
    if (morph == null) this else graphicsLayer {
        alpha = (1f - morph.progress.value * 2f).coerceIn(0f, 1f)
    }

@Composable
internal fun AskQuestionText(
    question: String,
    morph: AskQuestionMorph?,
    sent: Boolean,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val sourceColor = MaterialTheme.colorScheme.onSurface
    val targetColor = askUserBubbleColors(MaterialTheme.colorScheme).second
    BasicText(question, modifier,
        style = LocalTextStyle.current.copy(fontFamily = fontFamily, fontSize = fontSize, lineHeight = lineHeight),
        color = ColorProducer { lerp(sourceColor, targetColor, morph?.progress?.value ?: if (sent) 1f else 0f) })
}
