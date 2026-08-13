package com.simon.harmonichackernews.ui.navigation

import android.content.Intent
import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.ui.settings.SettingsIntents
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract
import com.simon.harmonichackernews.utils.HackerNewsItemLink
import com.simon.harmonichackernews.utils.HackerNewsLinks

/** Converts Android entry intents into portable navigation destinations. */
internal class MainLaunchIntentRouter(
    private val navigation: MainNavigationController,
) {
    fun route(intent: Intent?): Boolean {
        if (intent == null) return false

        if (SettingsIntents.ACTION_OPEN_SETTINGS == intent.action) {
            navigation.openSettings(
                intent.getStringExtra(SettingsIntents.EXTRA_SETTINGS_SECTION),
            )
            return true
        }

        if (ComposeEditorContract.ACTION_OPEN_EDITOR == intent.action) {
            val destination = (intent.extras?.let(::Bundle) ?: Bundle()).toEditorDestination()
            if (!destination.isValid) {
                showMessage("Invalid comment id")
                return false
            }
            navigation.openEditor(destination)
            return true
        }

        if (SubmissionsContract.ACTION_OPEN_SUBMISSIONS == intent.action) {
            val userName = intent.getStringExtra(SubmissionsContract.EXTRA_USER)
            if (userName.isNullOrEmpty()) {
                showMessage("Invalid username")
                return false
            }
            navigation.openSubmissions(userName)
            return true
        }

        if (CoulombGasContract.ACTION_OPEN == intent.action) {
            navigation.openCoulombGas()
            return true
        }

        return routeStory(intent)
    }

    private fun routeStory(intent: Intent): Boolean {
        val arguments = intent.extras?.let(::Bundle) ?: Bundle()
        var hackerNewsLink: HackerNewsItemLink? = null
        var commentsIntent = false

        if (Intent.ACTION_VIEW.equals(intent.action, ignoreCase = true)) {
            commentsIntent = true
            hackerNewsLink = HackerNewsLinks.parseItemLink(intent.data?.toString())
        } else if (Intent.ACTION_SEND.equals(intent.action, ignoreCase = true)) {
            commentsIntent = true
            hackerNewsLink = HackerNewsLinks.findItemLink(
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
        }

        var itemId = arguments.getInt(CommentsContract.EXTRA_ID, -1)
        hackerNewsLink?.let { link ->
            itemId = link.itemId
            if (link.scrollToCommentId > 0) {
                arguments.putInt(
                    CommentsContract.EXTRA_SCROLL_TO_COMMENT,
                    link.scrollToCommentId,
                )
            }
        }

        if (itemId <= 0) {
            if (commentsIntent) showMessage("Unable to parse story")
            return false
        }

        arguments.putInt(CommentsContract.EXTRA_ID, itemId)
        if (!arguments.containsKey(CommentsContract.EXTRA_TITLE)) {
            arguments.putString(CommentsContract.EXTRA_TITLE, "")
        }
        arguments.putBoolean(
            CommentsContract.EXTRA_SHOW_WEBSITE,
            arguments.getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false),
        )
        val destination = arguments.toStoryDestinationOrNull() ?: return false
        navigation.openStory(destination)
        return true
    }

    private fun showMessage(message: String) {
        navigation.showMessage(message)
    }
}
