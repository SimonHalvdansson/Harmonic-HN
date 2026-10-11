package com.simon.harmonichackernews.ui.navigation

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class RetainedDestinationControllersTest {
    @Test
    fun retainedVisitsKeepIndependentControllers() {
        val controllers = RetainedDestinationControllers<Any>()
        val parent = Any()
        val child = Any()
        val removeParent = controllers.register(1, parent)
        val removeChild = controllers.register(2, child)
        assertSame(parent, controllers[1])
        assertSame(child, controllers[2])
        removeParent()
        assertNull(controllers[1])
        assertSame(child, controllers[2])
        removeChild()
        assertNull(controllers[2])
        assertNull(controllers[null])
    }

    @Test
    fun outgoingOwnerCannotUnregisterItsReplacement() {
        val controllers = RetainedDestinationControllers<Any>()
        val oldOwner = controllers.register(1, Any())
        val replacement = Any()
        val newOwner = controllers.register(1, replacement)
        oldOwner()
        assertSame(replacement, controllers[1])
        newOwner()
        assertNull(controllers[1])
    }
}
