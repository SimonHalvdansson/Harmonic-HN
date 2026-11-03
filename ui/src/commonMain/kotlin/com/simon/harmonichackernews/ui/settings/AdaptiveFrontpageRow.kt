package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keep all frontpage actions available without consuming the name's entire width. */
@Composable
internal fun AdaptiveFrontpageRow(
    modifier: Modifier = Modifier,
    label: @Composable (Modifier) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth < 360.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)) {
            Column(Modifier.fillMaxWidth()) {
                label(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                label(Modifier.weight(1f))
                actions()
            }
        }
    }
}
