package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLaunchPreferenceKeys {
    const val STORE_NAME = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY"
    const val WELCOME_DIALOG_SHOWN =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val LAST_VERSION = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
    const val STORIES_MENU_HINT_PENDING = "stories_menu_hint_pending"
}

enum class AppLaunchDialog {
    NONE,
    WELCOME,
    CHANGELOG,
}

/** Portable one-time welcome and app-upgrade state backed by a platform key-value store. */
class AppLaunchStateStore(
    private val preferences: KeyValueStore,
) {
    private val menuHintPending = MutableStateFlow(
        preferences.getBoolean(AppLaunchPreferenceKeys.STORIES_MENU_HINT_PENDING, false),
    )
    val storiesMenuHintPending = menuHintPending.asStateFlow()

    val shouldShowWelcomeDialog: Boolean
        get() = !preferences.getBoolean(AppLaunchPreferenceKeys.WELCOME_DIALOG_SHOWN, false)

    fun markWelcomeDialogShown() {
        preferences.putBoolean(AppLaunchPreferenceKeys.WELCOME_DIALOG_SHOWN, true)
    }

    /** Only accepting the initial welcome offers the hint; upgrades and style changes do not. */
    fun completeWelcome() {
        if (!shouldShowWelcomeDialog) return
        preferences.update {
            putBoolean(AppLaunchPreferenceKeys.WELCOME_DIALOG_SHOWN, true)
            putBoolean(AppLaunchPreferenceKeys.STORIES_MENU_HINT_PENDING, true)
        }
        menuHintPending.value = true
    }

    fun dismissStoriesMenuHint() {
        preferences.putBoolean(AppLaunchPreferenceKeys.STORIES_MENU_HINT_PENDING, false)
        menuHintPending.value = false
    }

    fun consumeVersionUpgrade(currentVersion: Int): Boolean {
        val previousVersion = preferences.getInt(AppLaunchPreferenceKeys.LAST_VERSION, -1)
        if (currentVersion <= previousVersion) return false
        preferences.putInt(AppLaunchPreferenceKeys.LAST_VERSION, currentVersion)
        return true
    }

    fun consumeLaunchDialog(
        currentVersion: Int,
        showChangelog: Boolean,
    ): AppLaunchDialog {
        val showWelcome = shouldShowWelcomeDialog
        val upgraded = consumeVersionUpgrade(currentVersion)
        return when {
            showWelcome -> AppLaunchDialog.WELCOME
            upgraded && showChangelog -> AppLaunchDialog.CHANGELOG
            else -> AppLaunchDialog.NONE
        }
    }
}
