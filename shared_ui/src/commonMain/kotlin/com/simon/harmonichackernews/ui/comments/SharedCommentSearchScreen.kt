package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_search
import org.jetbrains.compose.resources.painterResource

/** Stateless comment-search surface backed by the shared comment-thread store. */
@Composable
fun SharedCommentSearchScreen(
    searchTerm: String,
    visibleComments: List<Comment>,
    mutedColor: Color,
    fontFamily: FontFamily,
    onSearchTermChanged: (String) -> Unit,
    requestFocus: Boolean,
    commentContent: @Composable (Comment) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val matchLabel = if (visibleComments.size == 1) "MATCH" else "MATCHES"

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "(${visibleComments.size} $matchLabel)",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 2.dp),
            color = mutedColor,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchTermChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search comments") },
            leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null) },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Search,
            ),
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            items(visibleComments, key = Comment::id) { comment -> commentContent(comment) }
        }
    }
}
