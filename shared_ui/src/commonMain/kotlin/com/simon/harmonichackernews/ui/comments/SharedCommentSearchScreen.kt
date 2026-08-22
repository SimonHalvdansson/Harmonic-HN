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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_search
import org.jetbrains.compose.resources.painterResource

/** Stateless comment-search surface backed by the shared comment-thread store. */
@Composable
fun SharedCommentSearchScreen(
    searchTerm: String,
    visibleComments: List<PortableCommentItem>,
    mutedColor: Color,
    fontFamily: FontFamily,
    onSearchTermChanged: (String) -> Unit,
    requestFocus: Boolean,
    commentContent: @Composable (PortableCommentItem) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Keep selection/composition state local to the field. On desktop, feeding the asynchronously
    // published String back into the String overload can reset the cursor to the start between
    // closely spaced key events, making input such as "ab" appear as "ba".
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = searchTerm,
                selection = TextRange(searchTerm.length),
            ),
        )
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 2.dp),
            color = mutedColor,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { value ->
                fieldValue = value
                onSearchTermChanged(value.text)
            },
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
            items(visibleComments, key = PortableCommentItem::id) { comment -> commentContent(comment) }
        }
    }
}
