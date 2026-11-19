package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.app.AppMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLaunchPreferenceKeys {
    const val STORE_NAME = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY"
    const val WELCOME_DIALOG_SHOWN =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val LAST_VERSION = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
    const val LAST_RELEASE_SERIES = "last_release_series"
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

    private fun consumeReleaseUpgrade(metadata: AppMetadata): Boolean {
        val previousCode = preferences.getInt(AppLaunchPreferenceKeys.LAST_VERSION, -1)
        if (metadata.versionCode <= previousCode) return false

        val series = metadata.versionName.split('.').take(2).joinToString(".")
        // Releases through 3.2.7 stored only the build number. 3.2 shipped as build 81.
        val previousSeries = preferences.getString(AppLaunchPreferenceKeys.LAST_RELEASE_SERIES)
            ?: if (previousCode in 81..89) "3.2" else null
        preferences.update {
            putInt(AppLaunchPreferenceKeys.LAST_VERSION, metadata.versionCode)
            putString(AppLaunchPreferenceKeys.LAST_RELEASE_SERIES, series)
        }
        return previousCode >= 0 && series != previousSeries
    }

    fun consumeLaunchDialog(
        metadata: AppMetadata,
        showChangelog: Boolean,
    ): AppLaunchDialog {
        val showWelcome = shouldShowWelcomeDialog
        val upgraded = consumeReleaseUpgrade(metadata)
        return when {
            showWelcome -> AppLaunchDialog.WELCOME
            upgraded && showChangelog -> AppLaunchDialog.CHANGELOG
            else -> AppLaunchDialog.NONE
        }
    }
}
