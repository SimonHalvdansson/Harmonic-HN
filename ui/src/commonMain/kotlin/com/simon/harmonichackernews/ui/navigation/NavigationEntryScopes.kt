package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
    var entries: Map<K, NavEntry<K>> = emptyMap()

    @Composable
    fun Content(key: K, content: @Composable () -> Unit) {
        // Capture the decorated entry while it is in the stack. A removed entry keeps this scope
        // through its exit animation; Nav3 clears it after both stack and composition release it.
        val entry = remember(this, key) { checkNotNull(entries[key]) }
        CompositionLocalProvider(LocalEntryContent provides content) { entry.Content() }
    }
}

@Composable
internal fun <K : Any> rememberNavigationEntryScopes(keys: List<K>): NavigationEntryScopes<K> {
    val parent = LocalSaveableStateRegistry.current
    val snapshots = remember(parent) { parent?.let(::SnapshotSaveableStateRegistry) }
    var entries: List<NavEntry<K>> = emptyList()
    CompositionLocalProvider(LocalSaveableStateRegistry provides snapshots) {
        entries = rememberDecoratedNavEntries(
            backStack = keys,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { key -> NavEntry(key, contentKey = key.toString()) { LocalEntryContent.current() } },
        )
    }
    return remember { NavigationEntryScopes<K>() }.also { it.entries = keys.zip(entries).toMap() }
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
