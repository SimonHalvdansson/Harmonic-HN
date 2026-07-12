package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.security.KeyStore;

public class EncryptedSharedPreferencesHelper {

    private final static String HARMONIC_ENCRYPTED_PREFS = "HARMONIC_ENCRYPTED_PREFS";
    private static final String MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_";

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";


    public static SharedPreferences getEncryptedSharedPreferences(Context ctx) throws Exception {
        return getEncryptedSharedPreferences(ctx, HARMONIC_ENCRYPTED_PREFS, MASTER_KEY_ALIAS);
    }

    public static SharedPreferences getEncryptedSharedPreferences(
            Context ctx,
            String sharedPreferencesName,
            String masterKeyAlias) throws Exception {
        try {
            return createSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                deleteSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias);
                return createSharedPreferences(ctx, sharedPreferencesName, masterKeyAlias);
            } catch (Exception otherException) {
                otherException.printStackTrace();
                throw new Exception();
            }
        }
    }

    private static SharedPreferences createSharedPreferences(
            Context ctx,
            String sharedPreferencesName,
            String masterKeyAlias) throws Exception {
        MasterKey mainKey = getMasterKey(ctx, masterKeyAlias);

        if (mainKey == null) {
            throw new Exception();
        }

        return EncryptedSharedPreferences.create(
                ctx,
                sharedPreferencesName,
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    // Workaround [https://github.com/google/tink/issues/535#issuecomment-912170221]
    // Issue Tracker - https://issuetracker.google.com/issues/176215143?pli=1
    public static boolean deleteSharedPreferences(Context ctx) {
        return deleteSharedPreferences(ctx, HARMONIC_ENCRYPTED_PREFS, MASTER_KEY_ALIAS);
    }

    private static boolean deleteSharedPreferences(
            Context ctx,
            String sharedPreferencesName,
            String masterKeyAlias) {
        try {
            File sharedPrefsFile = new File(ctx.getFilesDir().getParent()
                    + "/shared_prefs/" + sharedPreferencesName + ".xml");

            // Clear the encrypted prefs
            clearSharedPreference(ctx, sharedPreferencesName);

            // Delete the encrypted prefs file
            if (sharedPrefsFile.exists()) {
                boolean deleted = sharedPrefsFile.delete();
                Utils.log("EncryptedSharedPref: Shared pref file deleted=" + deleted + "; path=" + sharedPrefsFile.getAbsolutePath());
            } else {
                Utils.log("EncryptedSharedPref: Shared pref file non-existent; path=" + sharedPrefsFile.getAbsolutePath());
            }

            // Delete the master key
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            keyStore.deleteEntry(masterKeyAlias);
            return true;
        } catch (Exception e) {
            Utils.log("EncryptedSharedPref: Error occurred while trying to reset shared pref=" + e);
            return false;
        }
    }

    private static void clearSharedPreference(Context ctx, String sharedPreferencesName) {
        ctx.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static MasterKey getMasterKey(Context ctx, String masterKeyAlias) {
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    masterKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();

            return new MasterKey.Builder(ctx, masterKeyAlias)
                    .setKeyGenParameterSpec(spec)
                    .build();
        } catch (Exception e) {
            Log.e(ctx.getClass().getSimpleName(), "Error on getting master key", e);
        }
        return null;
    }

}
