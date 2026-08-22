package com.simon.harmonichackernews.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Color
import java.awt.EventQueue
import java.awt.Window
import javax.swing.RootPaneContainer

/** Applies Harmonic's resolved theme to the desktop window and supported native frame. */
internal data object DesktopWindowAppearance {
    fun apply(window: Window, dark: Boolean, backgroundArgb: Int) {
        EventQueue.invokeLater {
            val background = Color(backgroundArgb, true)
            window.background = background
            (window as? RootPaneContainer)?.contentPane?.background = background

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
