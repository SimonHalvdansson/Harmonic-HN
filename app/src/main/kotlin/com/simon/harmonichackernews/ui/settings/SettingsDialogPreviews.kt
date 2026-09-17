package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

@Preview(name = "Settings single-choice dialog")
@Composable
private fun SettingsSingleChoiceDialogPreview() {
    HarmonicTheme {
        SingleChoiceDialog(
            title = "Starting page",
            options = listOf(
                "top" to "Top Stories",
                "new" to "New Stories",
                "best" to "Best Stories",
            ),
            selected = "top",
            onDismiss = {},
            onSelected = {},
        )
    }
}
