package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.ui.common.TranslucentBackButton

/** A fixed, host-positioned escape hatch from comments back to the stories destination. */
@Composable
fun CommentsUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TranslucentBackButton(onClick = onClick, modifier = modifier)
