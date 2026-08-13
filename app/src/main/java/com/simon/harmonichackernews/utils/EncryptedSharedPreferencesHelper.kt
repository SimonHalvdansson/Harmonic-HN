package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

object EncryptedSharedPreferencesHelper {
    private const val HARMONIC_ENCRYPTED_PREFS = "HARMONIC_ENCRYPTED_PREFS"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"


    @Throws(Exception::class)
    fun getEncryptedSharedPreferences(ctx: Context): SharedPreferences {
        return getEncryptedSharedPreferences(ctx, HARMONIC_ENCRYPTED_PREFS, MASTER_KEY_ALIAS)
    }

    @Throws(Exception::class)
    fun getEncryptedSharedPreferences(
        ctx: Context,
        sharedPreferencesName: String,
        masterKeyAlias: String
    ): SharedPreferences {
        try {
            return createSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                deleteSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias)
                return createSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias)
            } catch (otherException: Exception) {
                otherException.printStackTrace()
                throw Exception()
            }
        }
    }

    @Throws(Exception::class)
    private fun createSharedPreferences(
        ctx: Context,
        sharedPreferencesName: String,
        masterKeyAlias: String
    ): SharedPreferences {
        val mainKey = getMasterKey(ctx, masterKeyAlias)

        if (mainKey == null) {
            throw Exception()
        }

        return EncryptedSharedPreferences.create(
            ctx,
            sharedPreferencesName,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Workaround [https://github.com/google/tink/issues/535#issuecomment-912170221]
    // Issue Tracker - https://issuetracker.google.com/issues/176215143?pli=1
    fun deleteSharedPreferences(ctx: Context): Boolean {
        return deleteSharedPreferences(ctx, HARMONIC_ENCRYPTED_PREFS, MASTER_KEY_ALIAS)
    }

    private fun deleteSharedPreferences(
        ctx: Context,
        sharedPreferencesName: String?,
        masterKeyAlias: String?
    ): Boolean {
        try {
            val sharedPrefsFile = File(
                (ctx.getFilesDir().getParent()
                        + "/shared_prefs/" + sharedPreferencesName + ".xml")
            )

            // Clear the encrypted prefs
            clearSharedPreference(ctx, sharedPreferencesName)

            // Delete the encrypted prefs file
            if (sharedPrefsFile.exists()) {
                val deleted = sharedPrefsFile.delete()
                HarmonicLog.debug("EncryptedSharedPref: Shared pref file deleted=" + deleted + "; path=" + sharedPrefsFile.getAbsolutePath())
            } else {
                HarmonicLog.debug("EncryptedSharedPref: Shared pref file non-existent; path=" + sharedPrefsFile.getAbsolutePath())
            }

            // Delete the master key
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.deleteEntry(masterKeyAlias)
            return true
        } catch (e: Exception) {
            HarmonicLog.debug("EncryptedSharedPref: Error occurred while trying to reset shared pref=" + e)
            return false
        }
    }

    private fun clearSharedPreference(ctx: Context, sharedPreferencesName: String?) {
        ctx.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getMasterKey(ctx: Context, masterKeyAlias: String): MasterKey? {
        try {
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
        } catch (e: Exception) {
            Log.e(ctx.javaClass.getSimpleName(), "Error on getting master key", e)
        }
        return null
    }
}
