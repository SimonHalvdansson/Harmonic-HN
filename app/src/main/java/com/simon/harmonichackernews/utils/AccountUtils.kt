package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.settings.AndroidKeyValueStore

object AccountUtils {
    private const val KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME =
        "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME"
    private const val KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD =
        "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD"

    @Volatile
    private var cachedHasAccountDetails: Boolean? = null

    fun getAccountUsername(ctx: Context): String? =
        AndroidKeyValueStore.global(ctx).getString(KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME)

    fun setAccountUsername(ctx: Context, username: String?) {
        cachedHasAccountDetails = null
        AndroidKeyValueStore.global(ctx).putString(
            KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME,
            username,
        )
    }

    fun hasAccountDetails(ctx: Context): Boolean {
        return synchronized(this) {
            cachedHasAccountDetails ?: getAccountDetails(ctx)?.let { account ->
                (!account.first.isNullOrEmpty() &&
                    !account.second.isNullOrEmpty()).also {
                    cachedHasAccountDetails = it
                }
            } ?: false
        }
    }

    fun getAccountDetails(ctx: Context): Pair<String?, String?>? {
        val sharedPreferences = try {
            EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val username = getAccountUsername(ctx)
        val password = sharedPreferences.getString(
            KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD,
            null,
        )

        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            return null
        }

        return Pair(username, password)
    }

    fun getHackerNewsAccount(ctx: Context): HackerNewsAccount? {
        val account = getAccountDetails(ctx) ?: return null
        val username = account.first ?: return null
        val password = account.second ?: return null
        return HackerNewsAccount(username, password)
    }

    fun setHackerNewsAccount(ctx: Context, account: HackerNewsAccount): Boolean {
        setAccountDetails(ctx, account.username, account.password)
        return true
    }

    fun clearHackerNewsAccount(ctx: Context): Boolean {
        deleteAccountDetails(ctx)
        return true
    }

    fun deleteAccountDetails(ctx: Context) {
        synchronized(this) {
            cachedHasAccountDetails = null
            setAccountUsername(ctx, null)
            EncryptedSharedPreferencesHelper.deleteSharedPreferences(ctx)
        }
    }

    fun setAccountDetails(ctx: Context, username: String?, password: String?) {
        synchronized(this) {
            cachedHasAccountDetails = null
            setAccountUsername(ctx, username)

            val sharedPreferences = try {
                EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }

            sharedPreferences.edit()
                .putString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, password)
                .apply()
        }
    }
}
