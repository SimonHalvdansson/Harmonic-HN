package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Read-only bridge for secrets written by older releases.
 *
 * New secrets use [AndroidKeystoreSecretStore]. This helper intentionally performs no recovery:
 * deleting a preference file or Keystore key after a transient read failure would permanently
 * discard credentials that may still be readable on the next attempt.
 */
@Suppress("DEPRECATION")
object EncryptedSharedPreferencesHelper {
    private const val HARMONIC_ENCRYPTED_PREFS = "HARMONIC_ENCRYPTED_PREFS"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_"

    @Throws(Exception::class)
    fun getEncryptedSharedPreferences(ctx: Context): SharedPreferences {
        return getEncryptedSharedPreferences(ctx, HARMONIC_ENCRYPTED_PREFS, MASTER_KEY_ALIAS)
    }

    @Throws(Exception::class)
    fun getEncryptedSharedPreferences(
        ctx: Context,
        sharedPreferencesName: String,
        masterKeyAlias: String
    ): SharedPreferences = createSharedPreferences(
        ctx.applicationContext,
        sharedPreferencesName,
        masterKeyAlias,
    )

    @Throws(Exception::class)
    private fun createSharedPreferences(
        ctx: Context,
        sharedPreferencesName: String,
        masterKeyAlias: String
    ): SharedPreferences {
        val mainKey = getMasterKey(ctx, masterKeyAlias)

        return EncryptedSharedPreferences.create(
            ctx,
            sharedPreferencesName,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getMasterKey(ctx: Context, masterKeyAlias: String): MasterKey {
        val spec = KeyGenParameterSpec.Builder(
            masterKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return MasterKey.Builder(ctx, masterKeyAlias)
            .setKeyGenParameterSpec(spec)
            .build()
    }
}
