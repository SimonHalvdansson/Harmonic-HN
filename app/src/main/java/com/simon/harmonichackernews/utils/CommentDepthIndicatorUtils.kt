package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import kotlin.math.abs

object CommentDepthIndicatorUtils {
    const val MODE_THEME_DEFAULT = "theme_default"
    const val MODE_MATERIAL_YOU = "material_you"
    const val MODE_COLORS = "colors"
    const val MODE_MONOCHROME = "monochrome"
    const val MODE_NONE = "none"

    const val COMMENT_DEPTH_COLOR_COUNT = 7

    private val COMMENT_DEPTH_COLORS_DARK = intArrayOf(
        R.color.commentIndentIndicatorColor1,
        R.color.commentIndentIndicatorColor2,
        R.color.commentIndentIndicatorColor3,
        R.color.commentIndentIndicatorColor4,
        R.color.commentIndentIndicatorColor5,
        R.color.commentIndentIndicatorColor6,
        R.color.commentIndentIndicatorColor7
    )

    private val COMMENT_DEPTH_COLORS_MATERIAL = intArrayOf(
        R.color.material_you_thread_depth_1,
        R.color.material_you_thread_depth_2,
        R.color.material_you_thread_depth_3,
        R.color.material_you_thread_depth_4,
        R.color.material_you_thread_depth_5,
        R.color.material_you_thread_depth_6,
        R.color.material_you_thread_depth_7
    )

    private val COMMENT_DEPTH_COLORS_LIGHT = intArrayOf(
        R.color.commentIndentIndicatorColor1light,
        R.color.commentIndentIndicatorColor2light,
        R.color.commentIndentIndicatorColor3light,
        R.color.commentIndentIndicatorColor4light,
        R.color.commentIndentIndicatorColor5light,
        R.color.commentIndentIndicatorColor6light,
        R.color.commentIndentIndicatorColor7light
    )

    fun getColorResource(ctx: Context, mode: String, theme: String?, index: Int): Int {
        val safeIndex = abs(index) % COMMENT_DEPTH_COLOR_COUNT
        val safeMode = sanitizeMode(mode)

        if (MODE_MONOCHROME == safeMode) {
            return R.color.commentIndentIndicatorColorMonochrome
        }

        if (MODE_MATERIAL_YOU == safeMode) {
            return COMMENT_DEPTH_COLORS_MATERIAL[safeIndex]
        }

        if (MODE_COLORS == safeMode) {
            return getStandardColorResource(ctx, theme, safeIndex)
        }

        if (theme?.startsWith("material") == true) {
            return COMMENT_DEPTH_COLORS_MATERIAL[safeIndex]
        }
        return getStandardColorResource(ctx, theme, safeIndex)
    }

    fun sanitizeMode(mode: String): String {
        return CommentDepthPreferences.sanitizeMode(mode)
    }

    fun shouldShowIndicators(mode: String): Boolean =
        CommentDepthPreferences.shouldShowIndicators(mode)

    fun getModeLabel(mode: String): String = CommentDepthPreferences.modeLabel(mode)

    private fun getStandardColorResource(ctx: Context, theme: String?, index: Int): Int {
        return if (ThemeUtils.isDarkMode(
                ctx,
                theme
            )
        ) COMMENT_DEPTH_COLORS_DARK[index] else COMMENT_DEPTH_COLORS_LIGHT[index]
    }
}
