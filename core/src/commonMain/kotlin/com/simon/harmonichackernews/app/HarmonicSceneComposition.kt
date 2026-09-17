package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.navigation.AppLaunchRouter
import com.simon.harmonichackernews.navigation.AppLinkNavigator
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.presentation.ScreenSessionRegistry
import com.simon.harmonichackernews.presentation.UserMessageStore

/**
 * State and routing owned by one Android task, iOS scene, or desktop window.
 *
 * A scene shares repositories and persistent services through [app], but never shares navigation
 * history or retained screen state with another scene. Hosts retain this object for the scene
 * lifetime and call [close] when that scene is permanently destroyed.
 */
class HarmonicSceneComposition internal constructor(
    val app: HarmonicAppComposition,
    val userMessages: UserMessageStore,
) {
    val sessions = ScreenSessionRegistry()
    val navigation = MainNavigationStore()
    val launches = AppLaunchRouter(navigation)
    val links = AppLinkNavigator(
        navigation = navigation,
        externalLinks = app.externalLinks,
        userMessages = userMessages,
    )

    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        userMessages.close()
    }
}
