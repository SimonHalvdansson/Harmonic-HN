package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.app.AppMetadata
import java.util.Properties

internal object DesktopRuntimeMetadata {
    fun load(
        debug: Boolean = System.getProperty("harmonic.desktop.debug")
            ?.toBooleanStrictOrNull() == true,
    ): AppMetadata {
        val properties = Properties().also { values ->
            checkNotNull(
                DesktopRuntimeMetadata::class.java.classLoader
                    .getResourceAsStream(RESOURCE_NAME),
            ) { "Missing $RESOURCE_NAME" }.use(values::load)
        }
        val versionName = properties.getProperty("versionName")
            ?.takeIf(String::isNotBlank)
            ?: error("Missing desktop versionName")
        val versionCode = properties.getProperty("versionCode")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: error("Invalid desktop versionCode")
        return AppMetadata(
            name = "Harmonic",
            versionName = versionName,
            versionCode = versionCode,
            buildNumber = versionCode.toString(),
            buildType = if (debug) "debug" else "release",
            debug = debug,
            debugSettingsEnabled = debug,
        )
    }

    private const val RESOURCE_NAME = "harmonic-desktop.properties"
}
