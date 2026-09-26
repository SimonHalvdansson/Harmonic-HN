package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.navigation.MainStoryRequest

/** Identity belongs to a visit, not a screen type or story/user ID. */
data class MainNavigationSurfaceKey(val destination: MainDestination, val serial: Int = 0)

val MainNavigationEntry.surfaceKey: MainNavigationSurfaceKey
    get() = MainNavigationSurfaceKey(destination, when (this) {
        is MainNavigationEntry.Story -> request.serial
        is MainNavigationEntry.Settings -> request.serial
        is MainNavigationEntry.Submissions -> request.serial
        is MainNavigationEntry.Editor -> request.serial
        else -> 0
    })

/** A list and its selected detail share one surface; every other entry owns its own surface. */
data class MainNavigationSurface(
    val entry: MainNavigationEntry,
    val detail: MainStoryRequest? = null,
) {
    val key: MainNavigationSurfaceKey get() = entry.surfaceKey
}

data class MainNavigationScenePlan(
    val surfaces: List<MainNavigationSurface>,
    val isTwoPane: Boolean,
) {
    val current: MainNavigationSurface get() = surfaces.last()
    val storyUsesTwoPane: Boolean get() = current.detail != null
}

/**
 * Project the logical history into ordered surfaces. This is the only place that groups adaptive
 * panes. Covering a surface never changes its owner or moves its detail into another composition.
 * An external story is the task root: it has no synthetic Stories screen underneath it.
 */
fun mainNavigationScenePlan(
    navigation: MainNavigationSnapshot,
    isTwoPane: Boolean,
    externalStorySerial: Int? = null,
): MainNavigationScenePlan {
    val externalIndex = navigation.destinationStack.indexOfFirst {
        it is MainNavigationEntry.Story && it.request.serial == externalStorySerial
    }
    val entries = navigation.destinationStack.drop(externalIndex.coerceAtLeast(0))
    val surfaces = mutableListOf<MainNavigationSurface>()
    entries.forEach { entry ->
        val owner = surfaces.lastOrNull()
        val joinsList = isTwoPane && entry is MainNavigationEntry.Story && (
            owner?.entry is MainNavigationEntry.Submissions ||
                (owner?.entry == MainNavigationEntry.Stories && owner.detail == null)
            )
        if (joinsList) {
            surfaces[surfaces.lastIndex] = requireNotNull(owner).copy(
                detail = entry.request,
            )
        } else {
            surfaces += MainNavigationSurface(entry)
        }
    }
    return MainNavigationScenePlan(surfaces, isTwoPane)
}

data class MainNavigationBackPreview(
    val source: MainNavigationSurfaceKey,
    val parent: MainNavigationSurfaceKey,
    val enterModifier: Modifier,
    val exitModifier: Modifier,
)

/** Capture the two owners once; committing the gesture must not start moving the new top. */
@Composable
fun rememberMainNavigationBackPreview(
    plan: MainNavigationScenePlan,
    gesture: Any?,
    enterModifier: Modifier,
    exitModifier: Modifier,
): MainNavigationBackPreview? {
    val owners = remember(gesture) {
        if (gesture == null || plan.surfaces.size < 2) null
        else plan.current.key to plan.surfaces[plan.surfaces.lastIndex - 1].key
    }
    return owners?.let { (source, parent) ->
        MainNavigationBackPreview(source, parent, enterModifier, exitModifier)
    }
}
