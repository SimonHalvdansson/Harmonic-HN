package com.simon.harmonichackernews.desktop

import com.sun.jna.Native
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Color
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.util.WeakHashMap
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/** Applies Harmonic's resolved theme to the desktop window and supported native frame. */
internal data object DesktopWindowAppearance {
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val windowDecorations = if (
        isWindows || System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    ) JBR.getWindowDecorations() else null
    private val titleBars = WeakHashMap<Window, WindowDecorations.CustomTitleBar>()

    val usesIntegratedTitleBar: Boolean =
        windowDecorations != null

    val integratedTitleBarHeight = if (isWindows) 40f else 28f
    const val windowsCaptionControlsWidth = 138f

    /** Keep native controls and window gestures, but let Compose paint the title bar surface. */
    fun configureTitleBar(window: Window): () -> Unit {
        val decorations = windowDecorations ?: return {}
        if (window !is Frame) return {}
        val titleBar = decorations.createCustomTitleBar().apply {
            height = integratedTitleBarHeight
            if (isWindows) putProperty("controls.width", windowsCaptionControlsWidth)
        }
        decorations.setCustomTitleBar(window, titleBar)
        titleBars[window] = titleBar
        // Compose's AWT canvas has mouse listeners, so JBR needs an explicit native hit test
        // on each event on both macOS and Windows. The reserved strip remains draggable.
        // JBR restricts the override to the title bar, preserving content input and resizing.
        val mouseListener = AWTEventListener { event ->
            if (event is MouseEvent && event.id != MouseEvent.MOUSE_EXITED &&
                event.id != MouseEvent.MOUSE_WHEEL
            ) {
                val source = event.source as? Component
                if (source === window || source?.let(SwingUtilities::getWindowAncestor) === window) {
                    titleBar.forceHitTest(false)
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(
            mouseListener,
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
        )
        return {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener)
            titleBars.remove(window)
            decorations.setCustomTitleBar(window, null)
        }
    }

    fun apply(window: Window, dark: Boolean, backgroundArgb: Int) {
        EventQueue.invokeLater {
            val background = Color(backgroundArgb, true)
            window.background = background
            (window as? RootPaneContainer)?.contentPane?.background = background
            if (usesIntegratedTitleBar && !isWindows) {
                (window as? RootPaneContainer)?.rootPane?.putClientProperty(
                    "apple.awt.windowAppearance",
                    if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
                )
            }
            if (isWindows) titleBars[window]?.putProperty("controls.dark", dark)

            if (
                window.isDisplayable &&
                System.getProperty("os.name").contains("win", ignoreCase = true)
            ) {
                runCatching {
                    val handle = HWND(Native.getComponentPointer(window))
                    val enabled = IntByReference(if (dark) 1 else 0)
                    val result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                        handle,
                        DWMWA_USE_IMMERSIVE_DARK_MODE,
                        enabled,
                        Int.SIZE_BYTES,
                    )
                    if (result != 0) {
                        DwmApi.INSTANCE.DwmSetWindowAttribute(
                            handle,
                            DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                            enabled,
                            Int.SIZE_BYTES,
                        )
                    }
                }
            }
            window.repaint()
        }
    }

    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(
            window: HWND,
            attribute: Int,
            value: IntByReference,
            valueSize: Int,
        ): Int

        companion object {
            val INSTANCE: DwmApi = Native.load(
                "dwmapi",
                DwmApi::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
}
