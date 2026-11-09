package com.simon.harmonichackernews.platform

/** Native permission and settings hooks; callbacks return on the UI thread without blocking it. */
interface NotificationSettingsHost {
    /** Refresh authorization after returning from the system settings app. */
    fun refresh(onResult: (deliveryAllowed: Boolean) -> Unit)
    fun requestPermission(onResult: (granted: Boolean) -> Unit)
    fun openSystemSettings()
}
