package com.simon.harmonichackernews.settings

object AppLaunchPreferenceKeys {
    const val STORE_NAME = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY"
    const val WELCOME_DIALOG_SHOWN =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val LAST_VERSION = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
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
    val shouldShowWelcomeDialog: Boolean
        get() = !preferences.getBoolean(AppLaunchPreferenceKeys.WELCOME_DIALOG_SHOWN, false)

    fun markWelcomeDialogShown() {
        preferences.putBoolean(AppLaunchPreferenceKeys.WELCOME_DIALOG_SHOWN, true)
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
