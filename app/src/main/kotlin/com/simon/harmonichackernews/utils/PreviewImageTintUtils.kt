package com.simon.harmonichackernews.utils

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.ui.theme.harmonicColors

/** Android cache/theme adapter around the shared palette extraction and tint-state policy. */
object PreviewImageTintUtils {
    fun getTintBaseColor(context: Context): Int = harmonicColors(context).storyCardBackground.toArgb()
}
