package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Each Submissions visit owns its list/detail scene throughout a nested visit and Back. */
@Composable
fun SubmissionsNavigationStack(
    scenes: List<MainSubmissionsScene>,
    predictiveBackActive: Boolean,
    completedPredictiveBack: Boolean,
    enterModifier: Modifier,
    exitModifier: Modifier,
    content: @Composable (MainSubmissionsScene) -> Unit,
) {
    // The outer destination compositor owns the first visit's enter/exit. Retain its
    // final scene while that exit runs, just as ActivityNavigationStack retains its entries.
    var retainedRoot by remember { mutableStateOf(scenes.firstOrNull()) }
    val root = scenes.firstOrNull() ?: retainedRoot ?: return
    SideEffect { retainedRoot = root }
    val nested = scenes.drop(1)
    val preview = remember(predictiveBackActive) {
        nested.lastOrNull()?.takeIf { predictiveBackActive }?.let {
            it.request.serial to nested.dropLast(1).lastOrNull()?.request?.serial
        }
    }
    ActivityNavigationStack(
        entries = nested,
        entryKey = { it.request.serial },
        completedPredictiveBack = completedPredictiveBack,
        preview = preview?.let { (source, parent) ->
            ActivityBackPreview(source, parent, enterModifier, exitModifier)
        },
        hideCoveredRoot = true,
        root = { content(root) },
        content = content,
    )
}
