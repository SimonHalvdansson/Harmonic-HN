package com.simon.harmonichackernews.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher

object AndroidLinkNavigation {
    fun openMaybeHackerNews(context: Context?, url: String?) {
        if (context == null || url.isNullOrEmpty()) return
        HackerNewsLinks.parseItemLink(url)?.let { link ->
            openStory(link.itemId, link.scrollToCommentId, context)
            return
        }
        AndroidExternalLinkLauncher.openCustomTab(context, url)
    }

    fun openStory(id: Int, scrollToCommentId: Int, context: Context) {
        if (context is MainActivity && id > 0) {
            context.navigationController.openStory(
                StoryDestination(storyId = id, scrollToCommentId = scrollToCommentId),
            )
            return
        }
        val uri = Uri.parse("https://news.ycombinator.com/item").buildUpon()
            .appendQueryParameter("id", id.toString())
            .apply { if (scrollToCommentId > 0) fragment(scrollToCommentId.toString()) }
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openPdf(context: Context, url: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}
