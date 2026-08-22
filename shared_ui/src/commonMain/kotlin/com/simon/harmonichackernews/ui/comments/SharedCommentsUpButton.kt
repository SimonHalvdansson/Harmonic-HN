package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.ui.common.SharedTranslucentBackButton

/** A fixed, host-positioned escape hatch from comments back to the stories destination. */
@Composable
fun SharedCommentsUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SharedTranslucentBackButton(onClick = onClick, modifier = modifier)
