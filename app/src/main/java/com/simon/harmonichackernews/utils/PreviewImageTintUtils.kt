package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors

/** Android cache/theme adapter around the shared palette extraction and tint-state policy. */
object PreviewImageTintUtils {
    fun getTintBaseColor(context: Context): Int = MaterialColors.getColor(
        context,
        com.simon.harmonichackernews.R.attr.storyCardBackgroundColor,
        MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            Color.TRANSPARENT,
        ),
    )
}
