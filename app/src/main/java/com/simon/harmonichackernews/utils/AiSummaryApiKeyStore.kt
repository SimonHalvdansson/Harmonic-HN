package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlin.Exception
import kotlin.String
import kotlin.Throws
import kotlin.concurrent.Volatile
import kotlin.synchronized

object AiSummaryApiKeyStore {
    const val PREF_API_KEY: String = "pref_ai_summary_api_key"

    private const val TAG = "AiSummaryApiKeyStore"
    private const val ENCRYPTED_PREFS_NAME = "HARMONIC_AI_SUMMARY_ENCRYPTED_PREFS"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_ai_summary_"

    @Volatile
    private var encryptedPreferences: SharedPreferences? = null

    @Volatile
    private var cachedHasApiKey: Boolean? = null

    fun getApiKey(context: Context): String {
        val appContext = context.getApplicationContext()
        val legacyPreferences =
            PreferenceManager.getDefaultSharedPreferences(appContext)
        val legacyValue = legacyPreferences.getString(PREF_API_KEY, null)

        try {
            val encryptedPreferences = getEncryptedPreferences(appContext)
            if (encryptedPreferences.contains(PREF_API_KEY)) {
                val encryptedValue: String = encryptedPreferences.getString(PREF_API_KEY, "")!!
                removeLegacyValue(legacyPreferences)
                val resolvedValue = if (encryptedValue == null) "" else encryptedValue
                cachedHasApiKey = !resolvedValue.isEmpty()
                return resolvedValue
            }

            if (legacyValue != null
                && encryptedPreferences.edit()
                    .putString(PREF_API_KEY, legacyValue)
                    .commit()
            ) {
                removeLegacyValue(legacyPreferences)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read the encrypted AI summary API key", e)
        }

        val resolvedValue = if (legacyValue == null) "" else legacyValue
        cachedHasApiKey = !resolvedValue.isEmpty()
        return resolvedValue
    }

    fun hasApiKey(context: Context): Boolean {
        val cachedValue = cachedHasApiKey
        if (cachedValue != null) {
            return cachedValue
        }
        return !getApiKey(context).isEmpty()
    }

    fun setApiKey(context: Context, apiKey: String?): Boolean {
        val appContext = context.getApplicationContext()
        try {
            val saved = getEncryptedPreferences(appContext)
                .edit()
                .putString(PREF_API_KEY, if (apiKey == null) "" else apiKey)
                .commit()
            if (saved) {
                removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
                cachedHasApiKey = apiKey != null && !apiKey.isEmpty()
            }
            return saved
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save the encrypted AI summary API key", e)
            return false
        }
    }

    fun clearApiKey(context: Context): Boolean {
        val appContext = context.getApplicationContext()
        var cleared = false
        try {
            cleared = getEncryptedPreferences(appContext)
                .edit()
                .remove(PREF_API_KEY)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to clear the encrypted AI summary API key", e)
        }
        removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
        cachedHasApiKey = if (cleared) false else null
        return cleared
    }

    @Throws(Exception::class)
    private fun getEncryptedPreferences(context: Context): SharedPreferences {
        val cachedPreferences = encryptedPreferences
        if (cachedPreferences != null) {
            return cachedPreferences
        }
        synchronized(AiSummaryApiKeyStore::class.java) {
            if (encryptedPreferences == null) {
                encryptedPreferences =
                    EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
                        context.getApplicationContext(),
                        ENCRYPTED_PREFS_NAME,
                        MASTER_KEY_ALIAS
                    )
            }
            return encryptedPreferences!!
        }
    }

    private fun removeLegacyValue(legacyPreferences: SharedPreferences) {
        if (legacyPreferences.contains(PREF_API_KEY)) {
            legacyPreferences.edit().remove(PREF_API_KEY).apply()
        }
    }
}
