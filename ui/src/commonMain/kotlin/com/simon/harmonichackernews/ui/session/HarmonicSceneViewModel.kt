package com.simon.harmonichackernews.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.navigation.MainNavigationRestoration

/** Owns a window's shared scene across host recreation, with route-only process restoration. */
class HarmonicSceneViewModel(
    app: HarmonicAppComposition,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val scene = app.createScene()

    init {
        savedStateHandle.get<SavedState>(NAVIGATION)?.let {
            scene.navigation.restore(decodeFromSavedState<MainNavigationRestoration>(it))
        }
        savedStateHandle.setSavedStateProvider(NAVIGATION) {
            encodeToSavedState(scene.navigation.restoration())
        }
    }

    override fun onCleared() = scene.close()

    companion object {
        private const val NAVIGATION = "navigation"
        fun factory(app: HarmonicAppComposition) = viewModelFactory {
            initializer { HarmonicSceneViewModel(app, createSavedStateHandle()) }
        }
    }
}

/** Native windows explicitly own and clear their ViewModels; Android uses its Activity owner. */
class HarmonicSceneOwner(app: HarmonicAppComposition) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val viewModel = ViewModelProvider.create(this, viewModelFactory {
        initializer { HarmonicSceneViewModel(app, SavedStateHandle()) }
    })[HarmonicSceneViewModel::class]
    val scene get() = viewModel.scene
    fun close() = viewModelStore.clear()
}
