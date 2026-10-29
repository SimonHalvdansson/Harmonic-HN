package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared chip layout, with pointer scrolling and a scrollbar supplied by desktop hosts. */
@Composable
internal fun HeaderChipRow(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    HeaderChipScrollContainer(state, modifier, contentPadding) { rowModifier ->
        LazyRow(
            state = state,
            modifier = rowModifier.headerChipEndScrim(state),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal expect fun HeaderChipScrollContainer(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable (Modifier) -> Unit,
)
