@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.simon.harmonichackernews.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.interop.LocalUIViewController
import com.simon.harmonichackernews.ui.common.LocalStatusBarScrollToTopRegistry
import com.simon.harmonichackernews.ui.common.StatusBarScrollToTopRegistry
import com.simon.harmonichackernews.ui.common.StatusBarScrollToTopTarget
import com.simon.harmonichackernews.ui.common.statusBarScrollToTopTargets
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIDevice
import platform.UIKit.UIEvent
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.accessibilityElementsHidden
import platform.darwin.NSObject

/** A public UIKit scroll-to-top delegate bridges the system gesture into Compose. */
@Composable
internal fun IosStatusBarScrollToTopHost(content: @Composable () -> Unit) {
    val view = LocalUIViewController.current.view
    val density = LocalDensity.current.density
    val registry = remember { StatusBarScrollToTopRegistry() }
    DisposableEffect(view, registry, density) {
        val proxies = mutableMapOf<StatusBarScrollToTopTarget, StatusBarScrollProxy>()
        registry.onChanged = {
            val viewport = view.bounds.useContents {
                Rect(0f, 0f, size.width.toFloat() * density, size.height.toFloat() * density)
            }
            val targets = statusBarScrollToTopTargets(
                registry.targets, viewport,
                multiplePanes = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad,
            )
            for (removed in proxies.keys.toList() - targets.toSet()) {
                proxies.remove(removed)?.removeFromSuperview()
            }
            for (target in targets) {
                val proxy = proxies.getOrPut(target) {
                    StatusBarScrollProxy(target.scroll).also(view::addSubview)
                }
                val bounds = target.bounds.intersect(viewport)
                proxy.setFrame(CGRectMake((bounds.left / density).toDouble(), 0.0,
                    (bounds.width / density).toDouble(), 1.0))
                proxy.setContentSize(CGSizeMake((bounds.width / density).toDouble(), 2.0))
                proxy.setContentOffset(CGPointMake(0.0, 1.0), animated = false)
            }
        }
        registry.onChanged()
        onDispose {
            registry.onChanged = {}
            proxies.values.forEach { it.removeFromSuperview() }
        }
    }
    CompositionLocalProvider(LocalStatusBarScrollToTopRegistry provides registry, content = content)
}

private class StatusBarScrollProxy(onScrollToTop: () -> Unit) : UIScrollView(CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    // UIScrollView's delegate is weak; retain the bridge for the lifetime of its proxy.
    private val scrollDelegate = object : NSObject(), UIScrollViewDelegateProtocol {
        override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
            onScrollToTop()
            // Keep the proxy offset armed for subsequent taps; Compose animates its own content.
            return false
        }
    }

    init {
        delegate = scrollDelegate
        scrollsToTop = true
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        bounces = false
        contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
        panGestureRecognizer.setEnabled(false)
        accessibilityElementsHidden = true
    }

    // This view observes the system gesture without intercepting app touches.
    override fun pointInside(point: CValue<CGPoint>, withEvent: UIEvent?): Boolean = false
}
