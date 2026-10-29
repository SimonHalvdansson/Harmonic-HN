package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.ui.navigation.NavigationEntryScopes
import com.simon.harmonichackernews.ui.navigation.rememberNavigationEntryScopes

@Composable
internal fun rememberSettingsRetainedContent(
    state: SettingsNavigationState,
    twoPane: Boolean,
): SettingsRetainedContent {
    val sections = state.detailStack.ifEmpty {
        listOfNotNull(SettingsSection.Appearance.takeIf { twoPane })
    }
    val scopes = rememberNavigationEntryScopes(
        listOf(SettingsNavigationStore.LIST_ROUTE) + sections.map { it.route },
    )
    return remember(scopes) { SettingsRetainedContent(scopes) }
}

/** One state owner per Settings destination; layouts only choose where to render its slot. */
internal class SettingsRetainedContent(private val scopes: NavigationEntryScopes<String>) {
    private val slots = mutableMapOf<String, @Composable (@Composable () -> Unit) -> Unit>()

    @Composable
    fun List(content: @Composable () -> Unit) = Content(SettingsNavigationStore.LIST_ROUTE, content)

    @Composable
    fun Section(section: SettingsSection, content: @Composable () -> Unit) = Content(section.route, content)

    @Composable
    private fun Content(key: String, content: @Composable () -> Unit) {
        val slot = slots.getOrPut(key) {
            // Pass the current rendering inputs through the slot rather than capturing them
            // when it is first created. Moving a slot preserves its composition and native views.
            movableContentOf { render: @Composable () -> Unit ->
                DisposableEffect(key) { onDispose { slots.remove(key) } }
                scopes.Content(key, render)
            }
        }
        slot(content)
    }
}
