package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlin.concurrent.Volatile

object AiSummaryApiKeyStore {
    const val PREF_API_KEY = "pref_ai_summary_api_key"

    private const val TAG = "AiSummaryApiKeyStore"
    private const val ENCRYPTED_PREFS_NAME = "HARMONIC_AI_SUMMARY_ENCRYPTED_PREFS"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_ai_summary_"

    @Volatile
    private var encryptedPreferences: SharedPreferences? = null

    @Volatile
    private var cachedHasApiKey: Boolean? = null

    fun getApiKey(context: Context): String {
        val appContext = context.applicationContext
        val legacyPreferences =
            PreferenceManager.getDefaultSharedPreferences(appContext)
        val legacyValue = legacyPreferences.getString(PREF_API_KEY, null)

        try {
            val encryptedPreferences = getEncryptedPreferences(appContext)
            if (encryptedPreferences.contains(PREF_API_KEY)) {
                val encryptedValue = encryptedPreferences.getString(PREF_API_KEY, "").orEmpty()
                removeLegacyValue(legacyPreferences)
                cachedHasApiKey = encryptedValue.isNotEmpty()
                return encryptedValue
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

        val resolvedValue = legacyValue.orEmpty()
        cachedHasApiKey = resolvedValue.isNotEmpty()
        return resolvedValue
    }

    fun hasApiKey(context: Context): Boolean {
        val cachedValue = cachedHasApiKey
        return cachedValue ?: getApiKey(context).isNotEmpty()
    }

    fun setApiKey(context: Context, apiKey: String?): Boolean {
        val appContext = context.applicationContext
        try {
            val saved = getEncryptedPreferences(appContext)
                .edit()
                .putString(PREF_API_KEY, apiKey.orEmpty())
                .commit()
            if (saved) {
                removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
                cachedHasApiKey = !apiKey.isNullOrEmpty()
            }
            return saved
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save the encrypted AI summary API key", e)
            return false
        }
    }

    fun clearApiKey(context: Context): Boolean {
        val appContext = context.applicationContext
        val cleared = try {
            getEncryptedPreferences(appContext)
                .edit()
                .remove(PREF_API_KEY)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to clear the encrypted AI summary API key", e)
            false
        }
        removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
        cachedHasApiKey = if (cleared) false else null
        return cleared
    }

    @Throws(Exception::class)
    private fun getEncryptedPreferences(context: Context): SharedPreferences {
        encryptedPreferences?.let { return it }
        return synchronized(this) {
            encryptedPreferences ?: EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
                context.applicationContext,
                ENCRYPTED_PREFS_NAME,
                MASTER_KEY_ALIAS,
            ).also { encryptedPreferences = it }
        }
    }

    private fun removeLegacyValue(legacyPreferences: SharedPreferences) {
        if (legacyPreferences.contains(PREF_API_KEY)) {
            legacyPreferences.edit().remove(PREF_API_KEY).apply()
        }
    }
}
