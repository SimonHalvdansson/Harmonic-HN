package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun HeaderChipScrollContainer(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable (Modifier) -> Unit,
) = content(modifier)
