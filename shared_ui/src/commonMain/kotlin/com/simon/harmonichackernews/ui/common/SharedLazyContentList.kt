package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Stateless list container shared by the stories and comments presentation shells. */
@Composable
fun <T> SharedLazyContentList(
    items: List<T>,
    state: LazyListState,
    key: (T) -> Any,
    contentType: (T) -> Any?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    headerKey: Any = "shared-list-header",
    header: (@Composable () -> Unit)? = null,
    footerKey: Any = "shared-list-footer",
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        if (header != null) item(key = headerKey, contentType = "header") { header() }
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
            contentType = { _, item -> contentType(item) },
        ) { index, item -> itemContent(index, item) }
        if (footer != null) item(key = footerKey, contentType = "footer") { footer() }
    }
}
