package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

class AndroidAiSummaryApiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private var cachedHasApiKey: Boolean? = null
    private val encryptedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
            appContext,
            ENCRYPTED_PREFS_NAME,
            MASTER_KEY_ALIAS,
        )
    }

    fun getApiKey(): String {
        val legacyPreferences =
            PreferenceManager.getDefaultSharedPreferences(appContext)
        val legacyValue = legacyPreferences.getString(PREF_API_KEY, null)

        try {
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

    fun hasApiKey(): Boolean {
        val cachedValue = cachedHasApiKey
        return cachedValue ?: getApiKey().isNotEmpty()
    }

    fun setApiKey(apiKey: String?): Boolean {
        try {
            val saved = encryptedPreferences
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

    fun clearApiKey(): Boolean {
        val cleared = try {
            encryptedPreferences
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

    private fun removeLegacyValue(legacyPreferences: SharedPreferences) {
        if (legacyPreferences.contains(PREF_API_KEY)) {
            legacyPreferences.edit().remove(PREF_API_KEY).apply()
        }
    }

    private companion object {
        const val PREF_API_KEY = "pref_ai_summary_api_key"
        const val TAG = "AiSummaryApiKeyStore"
        const val ENCRYPTED_PREFS_NAME = "HARMONIC_AI_SUMMARY_ENCRYPTED_PREFS"
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_ai_summary_"
    }
}
