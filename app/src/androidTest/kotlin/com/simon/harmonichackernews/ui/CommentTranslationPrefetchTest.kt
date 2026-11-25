@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.platform.CommentTranslationResult
import com.simon.harmonichackernews.platform.TranslationLanguages
import com.simon.harmonichackernews.platform.CommentTranslationService
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.ui.comments.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommentTranslationPrefetchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun collapsingALongThreadRevealsAnAlreadyTranslatedSibling() {
        fun row(id: Int, parent: Int, depth: Int) = PortableVisibleComment(id,
            PortableCommentItem(CommentSnapshot(id, parentId = parent, expandedAnchorText = "Comment $id"),
                CommentPresentationSnapshot(depth = depth, expanded = true)), 0)
        val parent = row(1, 100, 0)
        val sibling = row(20, 100, 0)
        val rows = mutableStateOf(listOf(parent) + (2..19).map { row(it, 1, 1) } + sibling)
        val calls = mutableListOf<String>()
        val service = object : CommentTranslationService {
            override suspend fun languageOptions() = TranslationLanguages("fr", mapOf("fr" to "French"))
            override suspend fun translate(html: String, targetLanguage: String): CommentTranslationResult {
                calls += html
                return CommentTranslationResult.Translated("Traduction de $html")
            }
        }
        val translations = CommentTranslations()
        compose.setContent {
            val list = rememberLazyListState()
            CompositionLocalProvider(LocalCommentTranslation provides service) {
                TranslateVisibleComments(translations, "fr", list, rows.value, true)
                LazyColumn(Modifier.height(180.dp), state = list) {
                    item { Text("Story", Modifier.height(40.dp)) }
                    items(rows.value.size) { index ->
                        Text(rows.value[index].comment.expandedAnchorText.orEmpty(), Modifier.height(100.dp))
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(listOf("Comment 1", "Comment 20", "Comment 2"), calls.take(3))
            rows.value = listOf(parent, sibling)
        }
        compose.runOnIdle {
            assertEquals("Traduction de Comment 20", translations[sibling.comment.translationKey("fr")]?.visibleHtml)
            assertEquals(1, calls.count { it == "Comment 20" })
        }
    }
}
