package com.simon.harmonichackernews.platform

import android.annotation.SuppressLint
import android.content.Context
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.utils.EncryptedSharedPreferencesHelper

/** Android encrypted persistence adapter behind the shared account repository contract. */
internal object AndroidHackerNewsAccountStorage {
    private const val USERNAME_KEY =
        "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME"
    private const val PASSWORD_KEY =
        "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD"

    fun load(context: Context): HackerNewsAccount? = synchronized(this) {
        val encrypted = encryptedPreferences(context) ?: return null
        val username = globalPreferences(context).getString(USERNAME_KEY, null)?.trim()
        val password = encrypted.getString(PASSWORD_KEY, null)
        if (username.isNullOrBlank() || password.isNullOrEmpty()) return null
        HackerNewsAccount(username, password)
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun save(context: Context, account: HackerNewsAccount): Boolean = synchronized(this) {
        val encrypted = encryptedPreferences(context) ?: return false
        val global = globalPreferences(context)
        val previousPassword = encrypted.getString(PASSWORD_KEY, null)
        if (!encrypted.edit().putString(PASSWORD_KEY, account.password).commit()) return false
        if (global.edit().putString(USERNAME_KEY, account.username.trim()).commit()) return true

        // Never leave a username/password half-write visible to the shared repository.
        encrypted.edit().putString(PASSWORD_KEY, previousPassword).commit()
        false
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun clear(context: Context): Boolean = synchronized(this) {
        val encrypted = encryptedPreferences(context) ?: return false
        val previousPassword = encrypted.getString(PASSWORD_KEY, null)
        if (!encrypted.edit().remove(PASSWORD_KEY).commit()) return false
        if (globalPreferences(context).edit().remove(USERNAME_KEY).commit()) return true

        encrypted.edit().putString(PASSWORD_KEY, previousPassword).commit()
        false
    }

    private fun encryptedPreferences(context: Context) = runCatching {
        EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(context)
    }.getOrNull()

    private fun globalPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            AppLaunchPreferenceKeys.STORE_NAME,
            Context.MODE_PRIVATE,
        )
}
