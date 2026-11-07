package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ZoomablePreviewImage(
    state: CommentLinkPreviewOverlayState.Image,
    onDismissRequest: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val userAgent = LocalHarmonicUiDependencies.current.network.userAgent
    val model = remember(context, state.imageUrl, userAgent) {
        ImageRequest.Builder(context).data(state.imageUrl)
            .networkHeader("User-Agent", userAgent).build()
    }
    var ratio by remember(state.imageUrl) { mutableFloatStateOf(state.imageAspectRatio ?: 16f / 9f) }
    ZoomableImageViewport(
        aspectRatio = ratio,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(),
    ) { imageModifier ->
        AsyncImage(
            model = model,
            contentDescription = state.description,
            modifier = imageModifier,
            contentScale = ContentScale.Fit,
            onSuccess = {
                val image = it.result.image
                if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
            },
        )
    }
}

/** The viewport grows with zoom; the base image and gesture handlers retain stable geometry. */
@Composable
internal fun ZoomableImageViewport(
    aspectRatio: Float,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var transform by remember { mutableStateOf(PreviewImageTransform()) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val geometry = remember(constraints, aspectRatio) {
            PreviewImageGeometry(
                Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), aspectRatio,
            )
        }
        val image = geometry.image
        LaunchedEffect(geometry) {
            motion?.cancel()
            transform = transform.bounded(geometry.viewport(transform.scale), image)
        }
        Box(
            Modifier.fillMaxWidth().height(with(density) { geometry.viewport(transform.scale).height.toDp() })
                .clipToBounds()
                .pointerInput(geometry) {
                    detectTapGestures(
                        onTap = { if (!transform.imageBounds(geometry.viewport(transform.scale), image).contains(it)) dismiss() },
                        onDoubleTap = { position ->
                            if (!transform.imageBounds(geometry.viewport(transform.scale), image).contains(position)) {
                                dismiss()
                            } else {
                                motion?.cancel()
                                val start = transform
                                val target = if (start.scale > 1.01f) PreviewImageTransform() else {
                                    geometry.zoomBy(start, 3f, position, Offset.Zero)
                                }
                                motion = scope.launch {
                                    Animatable(0f).animateTo(1f, tween(240)) {
                                        val scale = start.scale + (target.scale - start.scale) * value
                                        transform = PreviewImageTransform(
                                            scale,
                                            start.offset + (target.offset - start.offset) * value,
                                        ).bounded(geometry.viewport(scale), image)
                                    }
                                }
                            }
                        },
                    )
                }
                .pointerInput(geometry) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        motion?.cancel()
                        var pastSlop = false
                        var hadMultiplePointers = false
                        var totalZoom = 1f
                        var totalPan = Offset.Zero
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val multiplePointers = event.changes.count { it.pressed } > 1
                            hadMultiplePointers = hadMultiplePointers || multiplePointers
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            totalZoom *= zoom
                            totalPan += pan
                            if (!pastSlop) {
                                val zoomMotion = abs(1f - totalZoom) * event.calculateCentroidSize(useCurrent = false)
                                pastSlop = zoomMotion > viewConfiguration.touchSlop ||
                                    totalPan.getDistance() > viewConfiguration.touchSlop
                            }
                            if (pastSlop && event.changes.any { it.pressed } &&
                                (hadMultiplePointers || transform.scale > 1f)
                            ) {
                                transform = geometry.zoomBy(
                                    transform, zoom, event.calculateCentroid(useCurrent = false), pan,
                                )
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                            event.changes.firstOrNull { it.id == down.id }?.let {
                                velocity.addPosition(it.uptimeMillis, it.position)
                            }
                        } while (event.changes.any { it.pressed })

                        // A pinch should stop under the fingers; only one-finger pans carry momentum.
                        if (pastSlop && !hadMultiplePointers && transform.scale > 1f) {
                            val speed = velocity.calculateVelocity()
                            motion = scope.launch {
                                val start = transform
                                val viewport = geometry.viewport(start.scale)
                                val limit = start.copy(offset = Offset(Float.MAX_VALUE, Float.MAX_VALUE))
                                    .bounded(viewport, image).offset
                                val animation = Animatable(start.offset, Offset.VectorConverter)
                                animation.updateBounds(lowerBound = -limit, upperBound = limit)
                                animation.animateDecay(Offset(speed.x, speed.y), exponentialDecay()) {
                                    transform = start.copy(offset = value).bounded(viewport, image)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Measuring against the expanding viewport would refit tall images and amplify zoom.
            content(Modifier.requiredSize(
                with(density) { geometry.baseViewport.width.toDp() },
                with(density) { geometry.baseViewport.height.toDp() },
            ).graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = transform.offset.x
                translationY = transform.offset.y
            })
        }
    }
}
