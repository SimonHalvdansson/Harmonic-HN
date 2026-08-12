package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.settings.AndroidKeyValueStore

object AccountUtils {
    private const val KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME =
        "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME"
    private const val KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD =
        "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD"
    const val FAILURE_MODE_NONE = -1
    const val FAILURE_MODE_MAINKEY = 0
    const val FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION = 1
    const val FAILURE_MODE_NO_USERNAME = 3
    const val FAILURE_MODE_NO_PASSWORD = 4

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
        cachedHasAccountDetails?.let { return it }
        return synchronized(this) {
            cachedHasAccountDetails ?: getAccountDetails(ctx).let { account ->
                (account.third == FAILURE_MODE_NONE &&
                    !account.first.isNullOrEmpty() &&
                    !account.second.isNullOrEmpty()).also {
                    cachedHasAccountDetails = it
                }
            }
        }
    }

    fun getAccountDetails(ctx: Context): Triple<String?, String?, Int> {
        val sharedPreferences = try {
            EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
            return Triple(
                null,
                null,
                FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION,
            )
        }

        val username = getAccountUsername(ctx)
        val password = sharedPreferences.getString(
            KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD,
            null,
        )

        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            // note we're not logging the password
            Utils.log(
                "Empty check: username ${username.isNullOrEmpty()}, " +
                    "pass:${password.isNullOrEmpty()}",
            )
        }

        if (username.isNullOrEmpty()) {
            return Triple(
                username,
                password,
                FAILURE_MODE_NO_USERNAME,
            )
        }

        if (password.isNullOrEmpty()) {
            return Triple(
                username,
                password,
                FAILURE_MODE_NO_PASSWORD,
            )
        }

        // last resort, all is well, still send migration status
        return Triple(
            username,
            password,
            FAILURE_MODE_NONE,
        )
    }

    fun getHackerNewsAccount(ctx: Context): HackerNewsAccount? {
        val (username, password, failureMode) = getAccountDetails(ctx)
        if (
            failureMode != FAILURE_MODE_NONE ||
            username.isNullOrBlank() ||
            password.isNullOrEmpty()
        ) {
            return null
        }
        return HackerNewsAccount(username, password)
    }

    fun setHackerNewsAccount(ctx: Context, account: HackerNewsAccount): Boolean {
        setAccountDetails(ctx, account.username, account.password)
        return getHackerNewsAccount(ctx) == account
    }

    fun clearHackerNewsAccount(ctx: Context): Boolean {
        cachedHasAccountDetails = null
        setAccountDetails(ctx, null, null)
        val encryptedPreferencesDeleted =
            EncryptedSharedPreferencesHelper.deleteSharedPreferences(ctx)
        return encryptedPreferencesDeleted && getAccountUsername(ctx).isNullOrEmpty()
    }

    fun deleteAccountDetails(ctx: Context) {
        val deleted = clearHackerNewsAccount(ctx)
        if (!deleted) {
            Utils.toast(
                "Failed to delete EncryptedSharedPreferences",
                ctx,
            )
        }
    }

    fun setAccountDetails(
        ctx: Context,
        username: String?,
        password: String?,
    ) {
        cachedHasAccountDetails = null
        val sharedPreferences = try {
            EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        sharedPreferences.edit()
            .putString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, password)
            .apply()

        setAccountUsername(ctx, username)
    }

    fun showLoginPrompt(): Boolean = MainActivity.showLoginPrompt()

    @Suppress("UNUSED_PARAMETER")
    fun showLoginPrompt(ctx: Context?): Boolean = showLoginPrompt()

    fun handlePossibleError(
        account: Triple<String?, String?, Int>,
        showLoginPrompt: Boolean,
        ctx: Context?,
    ): Boolean {
        if (account.third == FAILURE_MODE_NONE) {
            return false
        }

        val loginPromptShown = showLoginPrompt && this.showLoginPrompt()

        when (account.third) {
            FAILURE_MODE_MAINKEY -> Utils.toast(
                "Login failed, cause: Couldn't get AndroidX MasterKey",
                ctx,
            )

            FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION -> Utils.toast(
                "Login failed, cause: EncryptedSharedPreferences threw exception",
                ctx,
            )

            FAILURE_MODE_NO_USERNAME -> {
                if (!loginPromptShown) {
                    Utils.toast(
                        "Login failed, cause: No saved username",
                        ctx,
                    )
                }
            }

            FAILURE_MODE_NO_PASSWORD -> {
                if (!loginPromptShown) {
                    Utils.toast(
                        "Login failed, cause: No saved password",
                        ctx,
                    )
                }
            }
        }
        return true
    }
}
