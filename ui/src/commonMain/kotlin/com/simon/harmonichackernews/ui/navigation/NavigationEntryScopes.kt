package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

private val LocalEntryContent = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/** Nav3 owns state and ViewModels; the surface compositor owns placement and exit animations. */
internal class NavigationEntryScopes<K : Any> {
    var entries: Map<K, NavEntry<K>> by mutableStateOf(emptyMap())

    @Composable
    fun Content(key: K, content: @Composable () -> Unit) {
        // A transition can first compose a layer after its entry was popped. Only mount
        // live entries, then keep the captured scope until the exit leaves composition.
        var retained by remember(this, key) { mutableStateOf(entries[key]) }
        val entry = retained ?: entries[key]?.also { retained = it } ?: return
        CompositionLocalProvider(LocalEntryContent provides content) { entry.Content() }
    }
}

@Composable
internal fun <K : Any> rememberNavigationEntryScopes(keys: List<K>): NavigationEntryScopes<K> {
    val parent = LocalSaveableStateRegistry.current
    val snapshots = remember(parent) { parent?.let(::SnapshotSaveableStateRegistry) }
    val scopes = remember { NavigationEntryScopes<K>() }
    CompositionLocalProvider(LocalSaveableStateRegistry provides snapshots) {
        val entries = rememberDecoratedNavEntries(
            backStack = keys,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { key -> NavEntry(key, contentKey = key.toString()) { LocalEntryContent.current() } },
        )
        scopes.entries = keys.zip(entries).toMap()
    }
    return scopes
}

/**
 * SaveableStateHolder returns its backing map, then updates that map again during disposal.
 * Android can keep the saved object graph until after destroying the old activity. Detaching
 * containers here prevents disposal (including text-field focus loss) from rewriting that snapshot.
 */
private class SnapshotSaveableStateRegistry(private val delegate: SaveableStateRegistry) :
    SaveableStateRegistry by delegate {
    override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry =
        delegate.registerProvider(key) { snapshotContainers(valueProvider()) }
}

@Suppress("UNCHECKED_CAST")
private fun snapshotContainers(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.mapValues { snapshotContainers(it.value) }
    is List<*> -> value.map(::snapshotContainers)
    is MutableIntState -> mutableIntStateOf(value.intValue)
    is MutableLongState -> mutableLongStateOf(value.longValue)
    is MutableFloatState -> mutableFloatStateOf(value.floatValue)
    is MutableDoubleState -> mutableDoubleStateOf(value.doubleValue)
    is SnapshotMutableState<*> -> mutableStateOf(
        snapshotContainers(value.value), value.policy as SnapshotMutationPolicy<Any?>,
    )
    else -> value
}
