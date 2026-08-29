package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.sin

private const val DialogPredictiveBackScale = 0.82f
private const val DialogPredictiveBackAlpha = 0.08f
private const val DialogPredictiveBackGestureProgressFraction = 0.42f
private const val DialogPredictiveBackTranslationXDp = 32f
private const val DialogPredictiveBackTranslationYDp = 10f
private const val DialogPredictiveBackCommitDurationMillis = 90
private const val DialogPredictiveBackCancelDurationMillis = 180

internal data class DialogPredictiveBackVisuals(
    val scale: Float,
    val alpha: Float,
    val backgroundDimAmountFraction: Float,
    val translationXDp: Float,
    val translationYDp: Float,
)

internal fun dialogPredictiveBackGestureProgress(progress: Float): Float =
    predictiveBackVisualProgress(progress) * DialogPredictiveBackGestureProgressFraction

internal fun dialogPredictiveBackVisuals(
    progress: Float,
    swipeDirection: Float = 1f,
): DialogPredictiveBackVisuals {
    val visualProgress = progress.coerceIn(0f, 1f)
    // Gesture progress is mapped entirely into the first phase. Keep the dialog fully opaque
    // until that phase is complete so fading can only begin after a committed gesture is released.
    val fadeProgress = ((visualProgress - DialogPredictiveBackGestureProgressFraction) /
        (1f - DialogPredictiveBackGestureProgressFraction)).coerceIn(0f, 1f)
    // Unlike the dialog itself, the background starts returning to full brightness as soon as the
    // gesture begins and is fully undimmed at the end of the held-gesture phase.
    val backgroundDimAmountFraction =
        (1f - visualProgress / DialogPredictiveBackGestureProgressFraction).coerceIn(0f, 1f)
    // Translation peaks during the gesture and returns to the center as the committed dialog
    // finishes shrinking away. Cancellation follows the same path in reverse.
    val translationProgress = sin(visualProgress * PI).toFloat()
    return DialogPredictiveBackVisuals(
        scale = 1f - (1f - DialogPredictiveBackScale) * visualProgress,
        alpha = 1f - (1f - DialogPredictiveBackAlpha) * fadeProgress,
        backgroundDimAmountFraction = backgroundDimAmountFraction,
        translationXDp = DialogPredictiveBackTranslationXDp *
            translationProgress * swipeDirection.coerceIn(-1f, 1f),
        translationYDp = DialogPredictiveBackTranslationYDp * translationProgress,
    )
}

internal data class DialogPredictiveBackEvent(
    val progress: Float,
    val swipeDirection: Float,
)

/**
 * Shared dialog host for dialogs without a source element to morph back into.
 *
 * Android predictive back translates and shrinks the dialog while the gesture is held, brightens
 * the background throughout the pull, then fades the dialog after the gesture commits. Other
 * platforms retain their native back handling.
 */
@Composable
fun PredictiveBackDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val visualProgress = remember { Animatable(0f) }
    var swipeDirection by remember { mutableFloatStateOf(1f) }
    val predictiveBackEnabled = properties.dismissOnBackPress &&
        platformDialogPredictiveBackSupported
    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress && !predictiveBackEnabled,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
        ),
    ) {
        PlatformDialogEdgeToEdge()
        // This must live inside the Dialog composition so Android registers it with the dialog's
        // OnBackPressedDispatcherOwner instead of the activity underneath.
        PlatformDialogPredictiveBackHandler(
            enabled = predictiveBackEnabled,
            onProgress = { event ->
                swipeDirection = event.swipeDirection
                visualProgress.snapTo(dialogPredictiveBackGestureProgress(event.progress))
            },
            onCancelled = {
                visualProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = DialogPredictiveBackCancelDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
            },
            onCommitted = {
                visualProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = DialogPredictiveBackCommitDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
                onDismissRequest()
            },
        )

        val visuals = dialogPredictiveBackVisuals(
            progress = visualProgress.value,
            swipeDirection = swipeDirection,
        )
        PlatformDialogBackgroundDimAmount(visuals.backgroundDimAmountFraction)
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = visuals.scale
                scaleY = visuals.scale
                alpha = visuals.alpha
                translationX = with(density) { visuals.translationXDp.dp.toPx() }
                translationY = with(density) { visuals.translationYDp.dp.toPx() }
                transformOrigin = TransformOrigin.Center
            },
        ) {
            content()
        }
    }
}

internal expect val platformDialogPredictiveBackSupported: Boolean

@Composable
internal expect fun PlatformDialogEdgeToEdge()

@Composable
internal expect fun PlatformDialogPredictiveBackHandler(
    enabled: Boolean,
    onProgress: suspend (DialogPredictiveBackEvent) -> Unit,
    onCancelled: suspend () -> Unit,
    onCommitted: suspend () -> Unit,
)

@Composable
internal expect fun PlatformDialogBackgroundDimAmount(fraction: Float)
