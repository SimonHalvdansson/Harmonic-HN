package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Default Android cross-activity predictive-back motion.
 *
 * Gesture progress uses AOSP's BackGestureInterpolator, while the motion and geometry follow
 * Decompose's Android V2 predictive-back implementation:
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/animation/BackGestureInterpolator.java
 * https://github.com/arkivanov/Decompose/blob/master/extensions-compose/src/commonMain/kotlin/com/arkivanov/decompose/extensions/compose/stack/animation/predictiveback/AndroidPredictiveBackAnimatableV2.kt
 */
internal class DefaultActivityPredictiveBackAnimation(
    private val initialEvent: BackEventCompat,
) {
    private val finishProgressAnimatable = Animatable(initialValue = 0f)
    private val progressAnimatable = Animatable(
        initialValue = BackGestureEasing.transform(initialEvent.progress),
    )
    private var edge by mutableIntStateOf(initialEvent.swipeEdge)
    private var touchY by mutableFloatStateOf(initialEvent.touchY)

    val exitModifier: Modifier
        get() = Modifier.composed {
            withLayoutCorners { corners ->
                exitModifier(corners.toShape(progressAnimatable.value))
            }
        }

    val enterModifier: Modifier
        get() = Modifier
            .drawWithContent {
                drawContent()
                drawRect(
                    color = Color.Black.copy(
                        alpha = (1f - finishProgressAnimatable.value) * RevealedContentScrimAlpha,
                    ),
                )
            }
            .composed {
                val shapeProgress = lerp(
                    start = progressAnimatable.value,
                    stop = 0f,
                    fraction = finishProgressAnimatable.value,
                )
                withLayoutCorners { corners ->
                    enterModifier(corners.toShape(shapeProgress))
                }
            }

    @Composable
    private fun Modifier.enterModifier(shape: Shape): Modifier {
        var size by remember { mutableStateOf(Size.Zero) }
        val scaleFactor = scaleFactor()
        val density = LocalDensity.current

        return this
            .onPlaced { size = it.size.toSize() }
            .graphicsLayer(
                scaleX = scaleFactor,
                scaleY = scaleFactor,
                translationX = lerp(
                    start = -size.width * FinishTranslationFraction,
                    stop = 0f,
                    fraction = finishProgressAnimatable.value,
                ),
                translationY = density.exitOffsetY(height = size.height),
                shape = shape,
                clip = true,
                compositingStrategy = CompositingStrategy.Offscreen,
            )
    }

    @Composable
    private fun Modifier.exitModifier(shape: Shape): Modifier {
        var size by remember { mutableStateOf(Size.Zero) }
        val scaleFactor = scaleFactor()
        val density = LocalDensity.current

        return this
            .onPlaced { size = it.size.toSize() }
            .graphicsLayer(
                scaleX = scaleFactor,
                scaleY = scaleFactor,
                alpha = 1f - finishProgressAnimatable.value,
                translationX = density.exitOffsetX(width = size.width),
                translationY = density.exitOffsetY(height = size.height),
                shape = shape,
                clip = true,
                compositingStrategy = CompositingStrategy.Offscreen,
            )
    }

    private fun Density.exitOffsetX(width: Float): Float {
        if (width == 0f) return 0f

        val initialOffsetX = when (edge) {
            BackEventCompat.EDGE_LEFT ->
                (width - width * initialScaleFactor()) / 2f -
                    GestureEdgeInset.toPx() * progressAnimatable.value

            BackEventCompat.EDGE_RIGHT,
            BackEventCompat.EDGE_NONE,
            -> 0f

            else -> 0f
        }

        return lerp(
            start = initialOffsetX,
            stop = width * FinishTranslationFraction,
            fraction = finishProgressAnimatable.value,
        )
    }

    private fun initialScaleFactor(): Float = lerp(
        start = 1f,
        stop = GestureTargetScale,
        fraction = progressAnimatable.value,
    )

    private fun scaleFactor(): Float = lerp(
        start = initialScaleFactor(),
        stop = 1f,
        fraction = finishProgressAnimatable.value,
    )

    private fun Density.exitOffsetY(height: Float): Float {
        if (height == 0f) return 0f

        val translationYLimit = height / 20f - GestureEdgeInset.toPx()
        val translationYFactor =
            ((touchY - initialEvent.touchY) / height) *
                (progressAnimatable.value * 3f).coerceAtMost(1f)

        return lerp(
            start = translationYLimit * translationYFactor,
            stop = 0f,
            fraction = finishProgressAnimatable.value,
        )
    }

    suspend fun animate(event: BackEventCompat) {
        edge = event.swipeEdge
        touchY = event.touchY
        progressAnimatable.animateTo(BackGestureEasing.transform(event.progress))
    }

    suspend fun finish() {
        val velocityFactor = progressAnimatable.velocity.coerceAtMost(1f)
        val progress = progressAnimatable.value

        coroutineScope {
            joinAll(
                launch {
                    progressAnimatable.animateTo(
                        progress + (1f - progress) * velocityFactor,
                    )
                },
                launch { finishProgressAnimatable.animateTo(targetValue = 1f) },
            )
        }
    }

    suspend fun cancel() {
        progressAnimatable.animateTo(0f)
    }

    private companion object {
        const val GestureTargetScale = 0.85f
        const val FinishTranslationFraction = 0.2f
        const val RevealedContentScrimAlpha = 0.25f
        val GestureEdgeInset = 8.dp
        val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    }
}

private data class LayoutCorners(
    val topStart: LayoutCorner = LayoutCorner(),
    val topEnd: LayoutCorner = LayoutCorner(),
    val bottomEnd: LayoutCorner = LayoutCorner(),
    val bottomStart: LayoutCorner = LayoutCorner(),
)

private data class LayoutCorner(
    val radius: Dp = 16.dp,
    val isFixed: Boolean = false,
)

private fun LayoutCorners.toShape(progress: Float): RoundedCornerShape = RoundedCornerShape(
    topStart = topStart.progressRadius(progress),
    topEnd = topEnd.progressRadius(progress),
    bottomEnd = bottomEnd.progressRadius(progress),
    bottomStart = bottomStart.progressRadius(progress),
)

private fun LayoutCorner.progressRadius(progress: Float): Dp =
    if (isFixed) radius else radius * progress

@Composable
private fun Modifier.withLayoutCorners(
    block: @Composable Modifier.(LayoutCorners) -> Modifier,
): Modifier {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenInfo = remember(context, density) {
        context.getScreenInfo(density)
    }

    if (screenInfo == null) {
        return block(LayoutCorners())
    }

    val rootView = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    var positionOnScreen by remember {
        mutableStateOf<Rect?>(null)
    }
    val corners = getLayoutCorners(screenInfo, positionOnScreen, layoutDirection)

    return onGloballyPositioned { coordinates ->
        positionOnScreen = getBoundsOnScreen(
            rootView = rootView,
            boundsInRoot = coordinates.boundsInRoot(),
        )
    }.block(corners)
}

private fun Context.getScreenInfo(density: Density): ScreenInfo? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    val windowMetrics =
        getSystemService(WindowManager::class.java)?.maximumWindowMetrics ?: return null
    val insets = windowMetrics.windowInsets

    return with(density) {
        ScreenInfo(
            cornerRadii = CornerRadii(
                topLeft = insets
                    .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius
                    ?.toDp(),
                topRight = insets
                    .getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                    ?.radius
                    ?.toDp(),
                bottomRight = insets
                    .getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                    ?.radius
                    ?.toDp(),
                bottomLeft = insets
                    .getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                    ?.radius
                    ?.toDp(),
            ),
            width = windowMetrics.bounds.width(),
            height = windowMetrics.bounds.height(),
        )
    }
}

private fun getLayoutCorners(
    screenInfo: ScreenInfo,
    positionOnScreen: Rect?,
    layoutDirection: LayoutDirection,
): LayoutCorners {
    if (positionOnScreen == null) return LayoutCorners()

    val (cornerRadii, screenWidth, screenHeight) = screenInfo
    val (left, top, right, bottom) = positionOnScreen

    val topLeft = layoutCorner(
        radius = cornerRadii.topLeft,
        isFixed = left <= 0 && top <= 0,
    )
    val topRight = layoutCorner(
        radius = cornerRadii.topRight,
        isFixed = right >= screenWidth && top <= 0,
    )
    val bottomRight = layoutCorner(
        radius = cornerRadii.bottomRight,
        isFixed = right >= screenWidth && bottom >= screenHeight,
    )
    val bottomLeft = layoutCorner(
        radius = cornerRadii.bottomLeft,
        isFixed = left <= 0 && bottom >= screenHeight,
    )

    return when (layoutDirection) {
        LayoutDirection.Ltr -> LayoutCorners(
            topStart = topLeft,
            topEnd = topRight,
            bottomEnd = bottomRight,
            bottomStart = bottomLeft,
        )

        LayoutDirection.Rtl -> LayoutCorners(
            topStart = topRight,
            topEnd = topLeft,
            bottomEnd = bottomLeft,
            bottomStart = bottomRight,
        )
    }
}

private fun layoutCorner(radius: Dp?, isFixed: Boolean): LayoutCorner =
    if (radius == null) LayoutCorner() else LayoutCorner(radius = radius, isFixed = isFixed)

private fun getBoundsOnScreen(rootView: View, boundsInRoot: Rect): Rect {
    val rootViewLeftTopOnScreen = IntArray(2)
    rootView.getLocationOnScreen(rootViewLeftTopOnScreen)
    val (rootViewLeftOnScreen, rootViewTopOnScreen) = rootViewLeftTopOnScreen

    return Rect(
        left = rootViewLeftOnScreen + boundsInRoot.left,
        top = rootViewTopOnScreen + boundsInRoot.top,
        right = rootViewLeftOnScreen + boundsInRoot.right,
        bottom = rootViewTopOnScreen + boundsInRoot.bottom,
    )
}

private data class ScreenInfo(
    val cornerRadii: CornerRadii,
    val width: Int,
    val height: Int,
)

private data class CornerRadii(
    val topLeft: Dp? = null,
    val topRight: Dp? = null,
    val bottomRight: Dp? = null,
    val bottomLeft: Dp? = null,
)
