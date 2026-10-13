package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationEntryScopesTest {
    @Test
    fun disposingAnEntryCannotRewriteAnAlreadySavedActivitySnapshot() = runTest {
        val owner = Owner()
        var registry = SaveableStateRegistry(null) { true }
        lateinit var selection: MutableState<String>
        lateinit var integer: MutableIntState
        lateinit var long: MutableLongState
        lateinit var float: MutableFloatState
        lateinit var double: MutableDoubleState
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        var composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalLifecycleOwner provides owner,
                LocalSavedStateRegistryOwner provides owner,
                LocalSaveableStateRegistry provides registry,
            ) {
                val scopes = rememberNavigationEntryScopes(listOf("editor"))
                scopes.Content("editor") {
                    selection = rememberSaveable { mutableStateOf("selected range") }
                    integer = rememberSaveable { mutableIntStateOf(1) }
                    long = rememberSaveable { mutableLongStateOf(2L) }
                    float = rememberSaveable { mutableFloatStateOf(3f) }
                    double = rememberSaveable { mutableDoubleStateOf(4.0) }
                }
            }
        }
        try {
            composition.setContent(content)
            runCurrent()
            val saved = registry.performSave()
            // Text fields collapse their selection when the old activity loses focus.
            selection.value = "collapsed cursor"
            integer.intValue = 10
            long.longValue = 20L
            float.floatValue = 30f
            double.doubleValue = 40.0
            composition.dispose()
            registry = SaveableStateRegistry(saved) { true }
            composition = Composition(EmptyApplier(), recomposer)
            composition.setContent(content)
            runCurrent()
            assertEquals("selected range", selection.value)
            assertEquals(1, integer.intValue)
            assertEquals(2L, long.longValue)
            assertEquals(3f, float.floatValue)
            assertEquals(4.0, double.doubleValue)
        } finally {
            composition.dispose()
            owner.viewModelStore.clear()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun visitsHaveIndependentViewModelsAndClearOnlyAfterTheirExitFinishes() = runTest {
        val owner = Owner()
        var backStack by mutableStateOf(listOf("first"))
        var rendered by mutableStateOf(backStack)
        val models = mutableMapOf<String, Probe>()
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            clock.sendFrame(time.also { time += 16_666_667 })
            runCurrent()
        }
        try {
            composition.setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalLifecycleOwner provides owner,
                    LocalSavedStateRegistryOwner provides owner,
                ) {
                    val scopes = rememberNavigationEntryScopes(backStack)
                    rendered.forEach { visit -> key(visit) {
                        scopes.Content(visit) {
                            models[visit] = viewModel { Probe(createSavedStateHandle()) }
                        }
                    } }
                }
            }
            repeat(4) { frame() }
            val first = checkNotNull(models["first"])
            backStack = listOf("first", "second")
            rendered = backStack
            repeat(4) { frame() }
            val second = checkNotNull(models["second"])
            assertNotSame(first, second)
            assertSame(first, models["first"])
            backStack = listOf("first")
            repeat(4) { frame() }
            assertFalse(second.cleared, "Popped content still needs its model during exit")
            rendered = backStack
            repeat(4) { frame() }
            assertTrue(second.cleared)
            assertFalse(first.cleared)
            assertSame(first, models["first"])
        } finally {
            composition.dispose()
            owner.viewModelStore.clear()
            recomposer.cancel()
            runner.join()
        }
        assertTrue(models.values.all { it.cleared })
    }

    private class Probe(val handle: SavedStateHandle) : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private class Owner : ViewModelStoreOwner, SavedStateRegistryOwner {
        override val viewModelStore = ViewModelStore()
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = controller.savedStateRegistry
        init {
            controller.performAttach()
            controller.performRestore(null)
            lifecycle.currentState = Lifecycle.State.RESUMED
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
