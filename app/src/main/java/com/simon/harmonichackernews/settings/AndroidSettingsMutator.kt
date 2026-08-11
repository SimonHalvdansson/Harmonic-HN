package com.simon.harmonichackernews.settings

import android.content.Context
import com.simon.harmonichackernews.utils.PreviewImageTintUtils.clearTintColorCaches

/** Android persistence side of the shared typed settings contract. */
object AndroidSettingsMutator {
    fun setFont(context: Context, font: String) {
        mutator(context).setFont(font)
    }

    fun setReaderModeFont(context: Context, font: String) {
        mutator(context).setReaderModeFont(font)
    }

    fun setCommentDepthIndicatorMode(context: Context, mode: String) {
        mutator(context).setCommentDepthIndicatorMode(mode)
    }

    fun setPaletteTint(
        context: Context,
        mode: String?,
        strength: Int,
        colorfulness: Int,
        tone: Int,
    ) {
        if (mutator(context).setPaletteTint(mode, strength, colorfulness, tone)) {
            clearTintColorCaches(context)
        }
    }

    fun clearPaletteTint(context: Context) {
        if (mutator(context).clearPaletteTint()) clearTintColorCaches(context)
    }

    private fun mutator(context: Context) =
        StoredSettingsMutator(AndroidKeyValueStore.defaults(context))
}
