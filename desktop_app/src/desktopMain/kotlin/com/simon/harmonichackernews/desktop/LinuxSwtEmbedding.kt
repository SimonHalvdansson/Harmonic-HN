package com.simon.harmonichackernews.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.Toolkit

/** SWT_AWT embeds an X11 child; native Wayland surfaces cannot be attached to an AWT Canvas. */
internal fun prepareLinuxSwtEmbedding() {
    if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) return
    check(!System.getenv("DISPLAY").isNullOrBlank() &&
        Toolkit.getDefaultToolkit().javaClass.name.contains("XToolkit")) {
        "The Linux CAPTCHA browser requires X11 or XWayland; native Wayland embedding is unavailable."
    }
    // Set this before SWT creates its first GTK display, so mixed Wayland/X11 desktops use
    // the same display protocol as the enclosing AWT window. This affects only this process.
    check(Native.load("c", LinuxGtkEnvironment::class.java).setenv("GDK_BACKEND", "x11", 1) == 0) {
        "The Linux CAPTCHA browser could not select the X11 GTK backend."
    }
}

internal interface LinuxGtkEnvironment : Library {
    fun setenv(name: String, value: String, overwrite: Int): Int
}
