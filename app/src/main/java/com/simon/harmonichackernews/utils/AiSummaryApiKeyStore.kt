package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class AndroidAiSummaryApiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    @Volatile
    private var cachedApiKey: String? = null
    private val secretStore = AndroidKeystoreSecretStore(
        context = appContext,
        preferencesName = KEYSTORE_VAULT_NAME,
        preferenceKey = KEYSTORE_VAULT_API_KEY,
        keyAlias = KEYSTORE_VAULT_KEY_ALIAS,
    )
    private val legacyEncryptedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
            appContext,
            LEGACY_ENCRYPTED_PREFS_NAME,
            LEGACY_MASTER_KEY_ALIAS,
        )
    }

    @Synchronized
    fun getApiKey(): String {
        cachedApiKey?.let { return it }
        val plainLegacyPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val plainLegacyValue = plainLegacyPreferences.getString(PREF_API_KEY, null)

        val result = readWithLegacyMigration(
            destination = secretStore,
            onMigrationFailure = {
                Log.e(TAG, "Unable to migrate the legacy AI summary API key", it)
            },
        ) {
            val encryptedLegacyValue = if (legacyEncryptedPreferences.contains(PREF_API_KEY)) {
                legacyEncryptedPreferences.getString(PREF_API_KEY, null)
            } else {
                null
            }
            (encryptedLegacyValue ?: plainLegacyValue)?.encodeToByteArray()
        }
        if (result is MigratingSecretReadResult.Failure) {
            // Do not delete or cache a fallback after a transient vault failure. A later read can
            // retry the Keystore while a pre-encryption value remains usable in the meantime.
            Log.e(TAG, "Unable to read the AI summary API key", result.error)
            return plainLegacyValue.orEmpty()
        }

        result as MigratingSecretReadResult.Success
        val resolved = result.value?.decodeToString().orEmpty()
        if (result.canDeleteLegacy) removeLegacyValue(plainLegacyPreferences)
        if (!result.shouldRetryMigration) cachedApiKey = resolved
        return resolved
    }

    fun hasApiKey(): Boolean = getApiKey().isNotEmpty()

    @Synchronized
    fun setApiKey(apiKey: String?): Boolean = try {
        val normalized = apiKey.orEmpty()
        secretStore.write(normalized.encodeToByteArray()).also { saved ->
            if (saved) {
                removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
                cachedApiKey = normalized
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Unable to save the AI summary API key", e)
        false
    }

    @Synchronized
    fun clearApiKey(): Boolean {
        val cleared = try {
            secretStore.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to clear the AI summary API key", e)
            false
        }
        if (!cleared) return false

        removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext))
        runCatching {
            legacyEncryptedPreferences.edit(commit = true) { remove(PREF_API_KEY) }
        }.onFailure { Log.w(TAG, "Unable to remove the legacy AI summary API key", it) }
        cachedApiKey = ""
        return cleared
    }

    private fun removeLegacyValue(legacyPreferences: SharedPreferences) {
        if (legacyPreferences.contains(PREF_API_KEY)) {
            legacyPreferences.edit { remove(PREF_API_KEY) }
        }
    }

    private companion object {
        const val PREF_API_KEY = "pref_ai_summary_api_key"
        const val TAG = "AiSummaryApiKeyStore"
        const val KEYSTORE_VAULT_NAME = "HARMONIC_AI_SUMMARY_KEYSTORE_VAULT"
        const val KEYSTORE_VAULT_API_KEY = "api_key"
        const val KEYSTORE_VAULT_KEY_ALIAS = "_harmonic_ai_summary_aes_gcm_v2_"
        const val LEGACY_ENCRYPTED_PREFS_NAME = "HARMONIC_AI_SUMMARY_ENCRYPTED_PREFS"
        const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_ai_summary_"
    }
}
