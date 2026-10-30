package com.simon.harmonichackernews.ui.navigation

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal actual class LayoutCornerResolver actual constructor() {
    private var cachedContext: Context? = null
    private var cachedDensity: Density? = null
    private var cachedConfiguration: android.content.res.Configuration? = null
    private var cachedWindowSize: androidx.compose.ui.unit.IntSize? = null
    private var screenInfo: ScreenInfo? = null
    private val rootLocation = IntArray(2)

    actual fun resolve(node: CompositionLocalConsumerModifierNode): LayoutCorners {
        val context = node.currentValueOf(LocalContext)
        val density = node.currentValueOf(LocalDensity)
        val configuration = node.currentValueOf(LocalConfiguration)
        val windowSize = node.currentValueOf(LocalWindowInfo).containerSize
        if (context !== cachedContext || density != cachedDensity ||
            configuration != cachedConfiguration || windowSize != cachedWindowSize
        ) {
            cachedContext = context
            cachedDensity = density
            cachedConfiguration = android.content.res.Configuration(configuration)
            cachedWindowSize = windowSize
            screenInfo = context.getScreenInfo(density)
        }
        val info = screenInfo ?: return LayoutCorners()
        val rootView = node.currentValueOf(LocalView)
        rootView.getLocationOnScreen(rootLocation)
        val bounds = node.requireLayoutCoordinates().boundsInRoot()
            .translate(rootLocation[0].toFloat(), rootLocation[1].toFloat())
        return getLayoutCorners(info, bounds, node.currentValueOf(LocalLayoutDirection))
    }
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
    radius?.let { LayoutCorner(radius = it, isFixed = isFixed) } ?: LayoutCorner()

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
