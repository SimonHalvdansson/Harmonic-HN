package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

/** Only Duo's asymmetric status strip is a rail; symmetric landscape cutouts are not. */
@Composable
internal fun iosSideRailWidth(insets: WindowInsets = WindowInsets.safeDrawing): Dp {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    // Expanded windows use ordinary pane toolbars. Their asymmetric status inset belongs
    // to the outer window, and must never turn into a rail inside the list pane.
    if (with(density) { minOf(windowSize.width, windowSize.height).toDp() } >= 600.dp) return 0.dp
    val direction = LocalLayoutDirection.current
    val left = insets.getLeft(density, direction)
    val right = insets.getRight(density, direction)
    return with(density) {
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone &&
            (right - left).toDp() >= 56.dp
        ) right.toDp() else 0.dp
    }
}

/** Match the system indicators: the safe-area strip includes a 12dp outer edge margin. */
internal fun Modifier.iosSideRailContent(railWidth: Dp): Modifier =
    width(railWidth).absolutePadding(right = 12.dp)

@Composable
internal fun iosSideRailTopPadding(): Dp {
    val top = with(LocalDensity.current) { WindowInsets.safeDrawing.getTop(this).toDp() }
    return maxOf(top + 80.dp, 160.dp) + 8.dp
}

@Composable
internal fun IosSideRailUpButton(
    railWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
) {
    Box(
        modifier.iosSideRailContent(railWidth).padding(top = iosSideRailTopPadding()),
        contentAlignment = Alignment.TopCenter,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription)
        }
    }
}
