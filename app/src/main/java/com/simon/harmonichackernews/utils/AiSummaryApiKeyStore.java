package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

public final class AiSummaryApiKeyStore {
    public static final String PREF_API_KEY = "pref_ai_summary_api_key";

    private static final String TAG = "AiSummaryApiKeyStore";
    private static final String ENCRYPTED_PREFS_NAME = "HARMONIC_AI_SUMMARY_ENCRYPTED_PREFS";
    private static final String MASTER_KEY_ALIAS =
            "_androidx_security_master_key_harmonic_ai_summary_";

    private AiSummaryApiKeyStore() {
    }

    public static String getApiKey(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences legacyPreferences =
                PreferenceManager.getDefaultSharedPreferences(appContext);
        String legacyValue = legacyPreferences.getString(PREF_API_KEY, null);

        try {
            SharedPreferences encryptedPreferences = getEncryptedPreferences(appContext);
            if (encryptedPreferences.contains(PREF_API_KEY)) {
                String encryptedValue = encryptedPreferences.getString(PREF_API_KEY, "");
                removeLegacyValue(legacyPreferences);
                return encryptedValue == null ? "" : encryptedValue;
            }

            if (legacyValue != null
                    && encryptedPreferences.edit()
                    .putString(PREF_API_KEY, legacyValue)
                    .commit()) {
                removeLegacyValue(legacyPreferences);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read the encrypted AI summary API key", e);
        }

        return legacyValue == null ? "" : legacyValue;
    }

    public static boolean setApiKey(Context context, String apiKey) {
        Context appContext = context.getApplicationContext();
        try {
            boolean saved = getEncryptedPreferences(appContext)
                    .edit()
                    .putString(PREF_API_KEY, apiKey == null ? "" : apiKey)
                    .commit();
            if (saved) {
                removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext));
            }
            return saved;
        } catch (Exception e) {
            Log.e(TAG, "Unable to save the encrypted AI summary API key", e);
            return false;
        }
    }

    public static boolean clearApiKey(Context context) {
        Context appContext = context.getApplicationContext();
        boolean cleared = false;
        try {
            cleared = getEncryptedPreferences(appContext)
                    .edit()
                    .remove(PREF_API_KEY)
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "Unable to clear the encrypted AI summary API key", e);
        }
        removeLegacyValue(PreferenceManager.getDefaultSharedPreferences(appContext));
        return cleared;
    }

    private static SharedPreferences getEncryptedPreferences(Context context) throws Exception {
        return EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
                context,
                ENCRYPTED_PREFS_NAME,
                MASTER_KEY_ALIAS);
    }

    private static void removeLegacyValue(SharedPreferences legacyPreferences) {
        if (legacyPreferences.contains(PREF_API_KEY)) {
            legacyPreferences.edit().remove(PREF_API_KEY).apply();
        }
    }
}
