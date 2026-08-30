package com.simon.harmonichackernews

import android.content.Context
import android.util.Base64
import android.util.Log
import com.simon.harmonichackernews.presentation.ReaderModeFontResource
import com.simon.harmonichackernews.presentation.ReaderModeFontResourcePolicy
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import com.simon.harmonichackernews.presentation.ReaderModeTheme
import com.simon.harmonichackernews.presentation.WebContentAssets
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.theme.ReaderModeFontData
import com.simon.harmonichackernews.ui.theme.ReaderModeThemeFactory
import com.simon.harmonichackernews.ui.theme.harmonicColors
import com.simon.harmonichackernews.utils.ThemeUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Loads and caches the Android assets used by the portable reader-mode controller. */
internal class AndroidReaderModeResources {
    private var cachedScript: String? = null

    fun script(context: Context): String? {
        cachedScript?.let { return it }
        return try {
            ReaderModeSourceAssembler.script(
                readabilitySource = readAssetFile(context, READABILITY_SCRIPT_ASSET),
                readerModeSource = readAssetFile(context, READER_MODE_SCRIPT_ASSET),
            ).also { cachedScript = it }
        } catch (error: IOException) {
            Log.e(TAG, "Failed to load reader mode script", error)
            null
        }
    }

    fun theme(context: Context, preferences: ReadingPreferences): ReaderModeTheme =
        ReaderModeThemeFactory.create(
            colors = harmonicColors(context),
            light = ThemeUtils.isLightMode(context),
            font = preferences.readerModeFont,
            fontSizePx = preferences.readerModeFontSize,
            fontData = fontData(context, preferences.readerModeFont),
        )

    private fun fontData(context: Context, font: String?): ReaderModeFontData? {
        val resources = ReaderModeFontResourcePolicy.resolve(font) ?: return null
        val regular = fontBase64(context, fontAsset(resources.regular))
        val bold = fontBase64(context, fontAsset(resources.bold))
        if (regular.isEmpty() || bold.isEmpty()) return null
        return ReaderModeFontData(regular, bold)
    }

    private fun fontAsset(resource: ReaderModeFontResource): String {
        val fileName = when (resource) {
            ReaderModeFontResource.PRODUCT_SANS_REGULAR -> "product_sans_regular.ttf"
            ReaderModeFontResource.PRODUCT_SANS_BOLD -> "product_sans_bold.ttf"
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_REGULAR ->
                "google_sans_flex_rounded_regular.ttf"
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_BOLD ->
                "google_sans_flex_rounded_bold.ttf"
            ReaderModeFontResource.GOOGLE_SANS_REGULAR -> "google_sans_regular.ttf"
            ReaderModeFontResource.GOOGLE_SANS_BOLD -> "google_sans_bold.ttf"
            ReaderModeFontResource.VERDANA_REGULAR -> "verdana_regular.ttf"
            ReaderModeFontResource.VERDANA_BOLD -> "verdana_bold.ttf"
            ReaderModeFontResource.ROBOTO_SLAB_REGULAR -> "roboto_slab_regular.ttf"
            ReaderModeFontResource.ROBOTO_SLAB_BOLD -> "roboto_slab_bold.ttf"
            ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR -> "google_sans_code_regular.ttf"
            ReaderModeFontResource.JETBRAINS_MONO_REGULAR -> "jetbrains_mono_regular.ttf"
            ReaderModeFontResource.JETBRAINS_MONO_BOLD -> "jetbrains_mono_bold.ttf"
            ReaderModeFontResource.GEORGIA_REGULAR -> "georgia_regular.ttf"
            ReaderModeFontResource.GEORGIA_BOLD -> "georgia_bold.ttf"
        }
        return sharedResourceAsset("font/$fileName")
    }

    private fun fontBase64(context: Context, asset: String): String = try {
        context.assets.open(asset).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while ((input.read(buffer).also { bytesRead = it }) != -1) {
                output.write(buffer, 0, bytesRead)
            }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    } catch (error: IOException) {
        Log.e(TAG, "Failed to load reader mode font", error)
        ""
    }

    @Throws(IOException::class)
    private fun readAssetFile(context: Context, asset: String): String =
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val TAG = "ReaderModeResources"
        const val ANDROID_ASSET_URI_PREFIX = "file:///android_asset/"
        val READABILITY_SCRIPT_ASSET = sharedWebAsset(WebContentAssets.READABILITY_SCRIPT)
        val READER_MODE_SCRIPT_ASSET = sharedWebAsset(WebContentAssets.READER_MODE_SCRIPT)

        fun sharedWebAsset(path: String): String = sharedResourceAsset("files/web/$path")

        fun sharedResourceAsset(path: String): String {
            val uri = Res.getUri(path)
            check(uri.startsWith(ANDROID_ASSET_URI_PREFIX)) {
                "Expected an Android asset URI for shared resource $path, got $uri"
            }
            return uri.removePrefix(ANDROID_ASSET_URI_PREFIX)
        }
    }
}
