package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*

import android.text.Html
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import java.util.Locale

@Composable
fun CommentsSearchDialog(
    comments: List<Comment>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onDismiss: () -> Unit,
    onCommentSelected: (Comment) -> Unit,
) {
    var searchTerm by rememberSaveable { mutableStateOf("") }
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    val searchableComments = remember(comments) {
        if (comments.size > 1) comments.drop(1) else emptyList()
    }
    val renderedText = remember(searchableComments) {
        searchableComments.associateWith { comment ->
            @Suppress("DEPRECATION")
            Html.fromHtml(comment.expandedAnchorText.orEmpty())
                .toString()
                .lowercase(Locale.ROOT)
        }
    }
    val visibleComments = remember(searchableComments, renderedText, searchTerm) {
        val normalizedTerm = searchTerm.trim().lowercase(Locale.ROOT)
        if (normalizedTerm.isEmpty()) {
            searchableComments
        } else {
            searchableComments.filter { renderedText[it]?.contains(normalizedTerm) == true }
        }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = Modifier.heightIn(max = maxDialogHeight),
        text = {
            CommentsSearchContent(
                searchTerm = searchTerm,
                onSearchTermChanged = { searchTerm = it },
                visibleComments = visibleComments,
                settings = settings,
                storyAuthor = storyAuthor,
                accountUser = accountUser,
                onCommentSelected = onCommentSelected,
                requestFocus = true,
            )
        },
        edgeToEdgeContent = true,
        showButtons = false,
    )
}

@Composable
private fun CommentsSearchContent(
    searchTerm: String,
    onSearchTermChanged: (String) -> Unit,
    visibleComments: List<Comment>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onCommentSelected: (Comment) -> Unit,
    requestFocus: Boolean,
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
    val itemStyle = remember(settings) {
        CommentItemStyle(
            cardStyle = settings.cardStyle,
            showCardBorder = settings.cardBorder,
            textSize = settings.preferredTextSize,
            collectLinks = false,
            emphasizeMeta = settings.highlightCommentMeta,
            depthIndicatorMode = CommentDepthIndicatorUtils.MODE_NONE,
            showDivider = false,
            preferredFont = settings.font,
            animateChanges = false,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "(${visibleComments.size} $matchLabel)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 2.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = ProductSansFontFamily,
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
            leadingIcon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Search,
            ),
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = 24.dp,
            ),
        ) {
            items(
                items = visibleComments,
                key = { it.id },
            ) { comment ->
                CommentItem(
                    comment = comment,
                    style = itemStyle,
                    storyAuthor = storyAuthor,
                    accountUser = accountUser,
                    userTag = null,
                    hiddenReplyCount = 0,
                    collapseParent = false,
                    showTopLevelIndicator = false,
                    flattenHierarchy = true,
                    forceExpanded = true,
                    searchTerm = searchTerm,
                    onToggleExpanded = { onCommentSelected(comment) },
                    onShowActions = { onCommentSelected(comment) },
                    onLinkLongClick = { _, _, _ -> },
                    onReferenceLongClick = { _, _ -> },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommentsSearchContentPreview() {
    val comment = remember {
        Comment().apply {
            id = 1
            by = "pg"
            text = "Compose makes the state transition easier to follow."
            expanded = true
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    HarmonicTheme {
        CommentsSearchContent(
            searchTerm = "state",
            onSearchTermChanged = {},
            visibleComments = listOf(comment),
            settings = CommentDisplaySettings.from(context, false, false, false, false),
            storyAuthor = "dang",
            accountUser = null,
            onCommentSelected = {},
            requestFocus = false,
        )
    }
}
