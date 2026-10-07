package com.simon.harmonichackernews.ui.reader

import androidx.compose.material3.ColorScheme
import com.simon.harmonichackernews.presentation.ReaderModeFontResourcePolicy
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import com.simon.harmonichackernews.presentation.WebContentAssets
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.theme.ReaderModeFontData
import com.simon.harmonichackernews.ui.theme.ReaderModeThemeFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64

/** Shared bundled assets for native browser readers and their settings previews. */
object ReaderModeResources {
    private val lock = Mutex()
    private var script: String? = null
    private val fonts = mutableMapOf<String, ReaderModeFontData>()

    suspend fun script(): String = lock.withLock {
        script ?: ReaderModeSourceAssembler.script(
            Res.readBytes("files/web/${WebContentAssets.READABILITY_SCRIPT}").decodeToString(),
            Res.readBytes("files/web/${WebContentAssets.READER_MODE_SCRIPT}").decodeToString(),
        ).also { script = it }
    }

    suspend fun fontData(font: String): ReaderModeFontData? = lock.withLock {
        val pair = ReaderModeFontResourcePolicy.resolve(font) ?: return@withLock null
        fonts[font] ?: ReaderModeFontData(
            Base64.encode(Res.readBytes("font/${pair.regular.name.lowercase()}.ttf")),
            Base64.encode(Res.readBytes("font/${pair.bold.name.lowercase()}.ttf")),
        ).also { fonts[font] = it }
    }

    suspend fun theme(colors: ColorScheme, light: Boolean, reading: ReadingPreferences) =
        ReaderModeThemeFactory.create(colors, light, reading.readerModeFont.storedValue,
            reading.readerModeFontSize, fontData(reading.readerModeFont.storedValue), reading.readerModeLineHeight.multiplier)
}
