package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import com.simon.harmonichackernews.settings.StoryPreviewMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class StorySettingsContent(val model: StoryRowModel, val style: StoryRowStyle) {
    // Mode changes retain the image. Only Off is an image visibility change.
    fun parts(): Map<String, Any?> = mapOf(
        "preview-image" to true.takeIf { style.previewImageMode != StoryPreviewMode.OFF },
        "summary" to model.previewText.takeIf { style.showPreviewText && it.isNotBlank() },
        "categories" to model.categoryLabels.takeIf { it.isNotEmpty() },
        "metadata" to true.takeUnless { style.compact },
        "points" to true.takeIf { style.previewImageMode == StoryPreviewMode.MEDIUM && style.showPoints && !style.compact },
        "comment-count" to true.takeIf { style.showCommentCount && !style.compact },
        "index" to true.takeIf { style.showIndex },
    )
}

/** Settings-only choreography. A cancelled change resumes from the current alpha and layout. */
internal class StorySettingsVisibility(initial: StorySettingsContent) {
    var rendered by mutableStateOf(initial)
        private set
    private val fades = initial.parts().mapValues { Animatable(1f) }
    private val reveals = listOf("summary", "preview-image").associateWith { Animatable(1f) }
    var metadataAnimating by mutableStateOf(false)
        private set
    fun alpha(key: String): Float = fades[key]?.value ?: 1f
    fun revealedHeight(key: String): Float = when {
        key == "preview-image" && rendered.style.previewImageMode != StoryPreviewMode.LARGE -> 1f
        else -> reveals[key]?.value ?: 1f
    }

    suspend fun animateTo(target: StorySettingsContent) {
        // These controls already have continuous span-width / favicon animations. Let those
        // layouts place their neighbours directly instead of chasing them with another tween.
        val oldStyle = rendered.style
        val newStyle = target.style
        val metadataOnly = rendered.model == target.model && oldStyle != newStyle &&
            oldStyle.copy(showPoints = newStyle.showPoints, showFavicon = newStyle.showFavicon,
                compactPoints = newStyle.compactPoints, includeTopLevelDomain = newStyle.includeTopLevelDomain) == newStyle
        metadataAnimating = metadataOnly
        if (metadataOnly) {
            try {
                fades.getValue("points").snapTo(1f)
                reveals.values.forEach { it.snapTo(1f) }
                rendered = target
                coroutineScope {
                    launch { Animatable(0f).animateTo(1f, tween(240)) }
                    target.parts().filterValues { it != null }.keys.forEach { key ->
                        if (alpha(key) < 1f) launch { fades.getValue(key).animateTo(1f, tween(160)) }
                    }
                }
            } finally { metadataAnimating = false }
            return
        }
        val needsRelayout = rendered != target
        val before = rendered.parts()
        val after = target.parts()
        val changed = before.keys.filter { before[it] != after[it] }
        coroutineScope {
            changed.filter { before[it] != null }.forEach { key ->
                launch { fades.getValue(key).animateTo(0f, tween(100)) }
            }
        }
        changed.forEach { fades.getValue(it).snapTo(0f) }
        changed.filter { before[it] == null && after[it] != null }.forEach { reveals[it]?.snapTo(0f) }
        rendered = target
        val entering = after.keys.filter { after[it] != null && alpha(it) < 1f }
        val revealing = reveals.filter { (key, value) -> after[key] != null && value.value < 1f }
        if (entering.isNotEmpty() || revealing.isNotEmpty()) {
            // Use the Compose animation clock (including reduced/slow motion), not wall time.
            coroutineScope {
                // Reveal only the space that layout has made available. This lets text and
                // large images fade in early without painting over the moving content below.
                revealing.forEach { (_, value) ->
                    launch { value.animateTo(1f, tween(StorySettingsMotionDurationMillis, delayMillis = 16,
                        easing = ContentMotionEasing)) }
                }
                entering.forEach { key -> launch {
                    val leadIn = if (!needsRelayout) 0 else when (key) {
                        "index", "comment-count" -> 0
                        "points" -> 100
                        "preview-image" -> 80
                        else -> 60
                    }
                    fades.getValue(key).animateTo(1f, tween(160, delayMillis = leadIn))
                } }
            }
        }
    }
}

@Composable
internal fun rememberStorySettingsVisibility(model: StoryRowModel, style: StoryRowStyle): StorySettingsVisibility {
    val target = StorySettingsContent(model, style)
    val visibility = remember { StorySettingsVisibility(target) }
    LaunchedEffect(target) { visibility.animateTo(target) }
    return visibility
}

internal fun Modifier.storySettingsAlpha(animation: StoryRowPlacementAnimation?, key: String): Modifier =
    if (animation?.visibility == null) this else graphicsLayer { alpha = animation.visibility.alpha(key) }
        .drawWithContent {
            val revealed = animation.visibility.revealedHeight(key)
            if (revealed == 1f) drawContent() else clipRect(bottom = size.height * revealed) { this@drawWithContent.drawContent() }
        }
