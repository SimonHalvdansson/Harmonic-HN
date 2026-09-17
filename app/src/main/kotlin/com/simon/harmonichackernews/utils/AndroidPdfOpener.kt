package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Native fallback for handing a PDF to an installed Android viewer. */
object AndroidPdfOpener {
    fun open(context: Context, url: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}
