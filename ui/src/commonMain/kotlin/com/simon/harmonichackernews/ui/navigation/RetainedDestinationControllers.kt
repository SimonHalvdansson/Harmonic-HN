package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf

/** Controllers belong to retained visits; the host selects one using its current request serial. */
class RetainedDestinationControllers<T : Any> {
    private val controllers = mutableStateMapOf<Int, T>()

    operator fun get(serial: Int?): T? = serial?.let(controllers::get)

    internal fun register(serial: Int, controller: T): () -> Unit {
        controllers[serial] = controller
        return {
            // An outgoing composition must not clear a replacement registered for the same visit.
            if (controllers[serial] === controller) controllers.remove(serial)
        }
    }
}

@Composable
fun <T : Any> RegisterDestinationController(
    controllers: RetainedDestinationControllers<T>,
    serial: Int,
    controller: T,
) {
    DisposableEffect(controllers, serial, controller) {
        val unregister = controllers.register(serial, controller)
        onDispose(unregister)
    }
}
