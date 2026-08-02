package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.simon.harmonichackernews.MainActivity
import kotlin.Triple

object AccountUtils {
    private const val KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME =
        "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME"
    private const val KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD =
        "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD"
    val FAILURE_MODE_NONE: Int = -1
    const val FAILURE_MODE_MAINKEY: Int = 0
    const val FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION: Int = 1
    const val FAILURE_MODE_NO_USERNAME: Int = 3
    const val FAILURE_MODE_NO_PASSWORD: Int = 4

    @kotlin.concurrent.Volatile
    private var cachedHasAccountDetails: kotlin.Boolean? = null


    fun getAccountUsername(ctx: android.content.Context): kotlin.String? {
        return SettingsUtils.readStringFromSharedPreferences(
            ctx,
            AccountUtils.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME
        )
    }

    fun setAccountUsername(ctx: android.content.Context, username: kotlin.String?) {
        AccountUtils.cachedHasAccountDetails = null
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            AccountUtils.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME,
            username
        )
    }

    fun hasAccountDetails(ctx: android.content.Context): kotlin.Boolean {
        var cached = AccountUtils.cachedHasAccountDetails
        if (cached != null) {
            return cached
        }
        kotlin.synchronized(AccountUtils::class.java) {
            cached = AccountUtils.cachedHasAccountDetails
            if (cached == null) {
                val account = AccountUtils.getAccountDetails(ctx)
                cached =
                    account.third == AccountUtils.FAILURE_MODE_NONE && !TextUtils.isEmpty(account.first) && !TextUtils.isEmpty(
                        account.second
                    )
                AccountUtils.cachedHasAccountDetails = cached
            }
            return cached
        }
    }

    fun getAccountDetails(ctx: android.content.Context): kotlin.Triple<kotlin.String?, kotlin.String?, Int?> {
        val sharedPreferences: SharedPreferences?
        try {
            sharedPreferences = EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return kotlin.Triple<kotlin.String?, kotlin.String?, Int?>(
                null,
                null,
                AccountUtils.FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION
            )
        }

        val username = AccountUtils.getAccountUsername(ctx)
        val password = sharedPreferences.getString(
            AccountUtils.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD,
            null
        )

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            // note we're not logging the password
            com.simon.harmonichackernews.utils.Utils.log(
                "Empty check: username " + TextUtils.isEmpty(
                    username
                ) + ", pass:" + TextUtils.isEmpty(password)
            )
        }

        if (TextUtils.isEmpty(username)) {
            return kotlin.Triple<kotlin.String?, kotlin.String?, Int?>(
                username,
                password,
                AccountUtils.FAILURE_MODE_NO_USERNAME
            )
        }

        if (TextUtils.isEmpty(password)) {
            return kotlin.Triple<kotlin.String?, kotlin.String?, Int?>(
                username,
                password,
                AccountUtils.FAILURE_MODE_NO_PASSWORD
            )
        }

        // last resort, all is well, still send migration status
        return kotlin.Triple<kotlin.String?, kotlin.String?, Int?>(
            username,
            password,
            AccountUtils.FAILURE_MODE_NONE
        )
    }

    fun deleteAccountDetails(ctx: android.content.Context) {
        AccountUtils.cachedHasAccountDetails = null
        AccountUtils.setAccountDetails(ctx, null, null)
        val deleted = EncryptedSharedPreferencesHelper.deleteSharedPreferences(ctx)
        if (!deleted) {
            com.simon.harmonichackernews.utils.Utils.toast(
                "Failed to delete EncryptedSharedPreferences",
                ctx
            )
        }
    }

    fun setAccountDetails(
        ctx: android.content.Context,
        username: kotlin.String?,
        password: kotlin.String?
    ) {
        AccountUtils.cachedHasAccountDetails = null
        val sharedPreferences: SharedPreferences?
        try {
            sharedPreferences = EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return
        }

        val sharedPrefsEditor: SharedPreferences.Editor = sharedPreferences.edit()
        sharedPrefsEditor.putString(
            AccountUtils.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD,
            password
        )
        sharedPrefsEditor.apply()

        AccountUtils.setAccountUsername(ctx, username)
    }

    fun showLoginPrompt(ctx: android.content.Context?): kotlin.Boolean {
        return MainActivity.showLoginPrompt()
    }

    fun handlePossibleError(
        account: kotlin.Triple<kotlin.String?, kotlin.String?, Int?>,
        showLoginPrompt: kotlin.Boolean,
        ctx: android.content.Context?
    ): kotlin.Boolean {
        if (account.third == AccountUtils.FAILURE_MODE_NONE) {
            return false
        }

        val loginPromptShown = showLoginPrompt && AccountUtils.showLoginPrompt(ctx)

        when (account.third) {
            AccountUtils.FAILURE_MODE_MAINKEY -> com.simon.harmonichackernews.utils.Utils.toast(
                "Login failed, cause: Couldn't get AndroidX MasterKey",
                ctx
            )

            AccountUtils.FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION -> com.simon.harmonichackernews.utils.Utils.toast(
                "Login failed, cause: EncryptedSharedPreferences threw exception",
                ctx
            )

            AccountUtils.FAILURE_MODE_NO_USERNAME -> {
                if (!loginPromptShown) {
                    com.simon.harmonichackernews.utils.Utils.toast(
                        "Login failed, cause: No saved username",
                        ctx
                    )
                }
            }

            AccountUtils.FAILURE_MODE_NO_PASSWORD -> {
                if (!loginPromptShown) {
                    com.simon.harmonichackernews.utils.Utils.toast(
                        "Login failed, cause: No saved password",
                        ctx
                    )
                }
            }
        }
        return true
    }
}
