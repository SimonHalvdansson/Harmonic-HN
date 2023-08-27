package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.simon.harmonichackernews.LoginDialogFragment;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class AccountUtils {

    private final static String KEY_ENCRYPTED_SHARED_PREFERENCES_USERNAME = "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_USERNAME";
    private final static String KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME = "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME";
    private final static String KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD = "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD";
    private final static String HARMONIC_ENCRYPTED_PREFS = "HARMONIC_ENCRYPTED_PREFS";
    private static final String MASTER_KEY_ALIAS = "_androidx_security_master_key_harmonic_";

    public static String getAccountUsername(Context ctx) {
        return SettingsUtils.readStringFromSharedPreferences(ctx, KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME);
    }

    public static void setAccountUsername(Context ctx, String username) {
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME, username);
    }

    public static Pair<String, String> getAccountDetails(Context ctx) {
        MasterKey mainKey = getMasterKey(ctx);

        if (mainKey == null) {
            return null;
        }

        SharedPreferences sharedPreferences;
        try {
            sharedPreferences = EncryptedSharedPreferences.create(
                    ctx,
                    HARMONIC_ENCRYPTED_PREFS,
                    mainKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }

        //try to get unencrypted version first
        String username = getAccountUsername(ctx);
        //if it fails, see if we have encrypted saved
        if (TextUtils.isEmpty(username)) {
            username = sharedPreferences.getString(KEY_ENCRYPTED_SHARED_PREFERENCES_USERNAME, null);
            //migration
            setAccountUsername(ctx, username);
        }
        String password = sharedPreferences.getString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, null);

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            return null;
        }
        return Pair.create(username, password);
    }

    public static void deleteAccountDetails(Context ctx) {
        setAccountDetails(ctx, null, null);
    }

    public static void setAccountDetails(Context ctx, String username, String password) {
        MasterKey mainKey = getMasterKey(ctx);

        if (mainKey == null) {
            return;
        }

        SharedPreferences sharedPreferences;
        try {
            sharedPreferences = EncryptedSharedPreferences.create(
                    ctx,
                    HARMONIC_ENCRYPTED_PREFS,
                    mainKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return;
        }

        SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
        sharedPrefsEditor.putString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, password);
        sharedPrefsEditor.apply();

        setAccountUsername(ctx, username);
    }

    private static MasterKey getMasterKey(Context ctx) {
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();

            return new MasterKey.Builder(ctx, MASTER_KEY_ALIAS)
                    .setKeyGenParameterSpec(spec)
                    .build();
        } catch (Exception e) {
            Log.e(ctx.getClass().getSimpleName(), "Error on getting master key", e);
        }
        return null;
    }

    public static void showLoginPrompt(FragmentManager fm) {
        new LoginDialogFragment().show(fm, LoginDialogFragment.TAG);
    }

}
