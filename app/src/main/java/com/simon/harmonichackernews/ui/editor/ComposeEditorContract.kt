package com.simon.harmonichackernews.ui.editor

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity

/** Navigation contract for the Compose post/comment editor.  */
object ComposeEditorContract {
    const val ACTION_OPEN_EDITOR = "com.simon.harmonichackernews.action.OPEN_EDITOR"
    const val EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID"
    const val EXTRA_PARENT_TEXT = "com.simon.harmonichackernews.EXTRA_PARENT_TEXT"
    const val EXTRA_POST_TITLE = "com.simon.harmonichackernews.EXTRA_POST_TITLE"
    const val EXTRA_USER = "com.simon.harmonichackernews.EXTRA_USER"
    const val EXTRA_TYPE = "com.simon.harmonichackernews.EXTRA_TYPE"

    const val TYPE_TOP_COMMENT = 0
    const val TYPE_COMMENT_REPLY = 1
    const val TYPE_POST = 2

    fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_EDITOR)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
