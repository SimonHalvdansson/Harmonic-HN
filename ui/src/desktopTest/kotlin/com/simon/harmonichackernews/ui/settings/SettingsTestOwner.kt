package com.simon.harmonichackernews.ui.settings

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

internal class SettingsTestOwner : ViewModelStoreOwner, SavedStateRegistryOwner {
    override val viewModelStore = ViewModelStore()
    override val lifecycle = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        lifecycle.currentState = Lifecycle.State.RESUMED
    }
}
