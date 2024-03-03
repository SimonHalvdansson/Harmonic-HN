package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.fragment.app.FragmentManager;

import com.simon.harmonichackernews.LoginDialogFragment;

import kotlin.Triple;

public class AccountUtils {

    private final static String KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME = "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME";
    private final static String KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD = "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD";
    public final static int FAILURE_MODE_NONE = -1;
    public final static int FAILURE_MODE_MAINKEY = 0;
    public final static int FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION = 1;
    public final static int FAILURE_MODE_NO_USERNAME = 3;
    public final static int FAILURE_MODE_NO_PASSWORD = 4;


    public static String getAccountUsername(Context ctx) {
        return SettingsUtils.readStringFromSharedPreferences(ctx, KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME);
    }

    public static void setAccountUsername(Context ctx, String username) {
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME, username);
    }

    public static boolean hasAccountDetails(Context ctx) {
        Triple<String, String, Integer> account = getAccountDetails(ctx);
        return (account.getThird() == FAILURE_MODE_NONE && !TextUtils.isEmpty(account.getFirst()) && !TextUtils.isEmpty(account.getSecond()));
    }

    public static Triple<String, String, Integer> getAccountDetails(Context ctx) {
        SharedPreferences sharedPreferences;
        try {
            sharedPreferences = EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx);
        } catch (Exception e) {
            e.printStackTrace();
            return new Triple<>(null, null, FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION);
        }

        String username = getAccountUsername(ctx);
        String password = sharedPreferences.getString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, null);

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            // note we're not logging the password
            Utils.log("Empty check: username " + TextUtils.isEmpty(username) + ", pass:" + TextUtils.isEmpty(password));
        }

        if (TextUtils.isEmpty(username)) {
            return new Triple<>(username, password, FAILURE_MODE_NO_USERNAME);
        }

        if (TextUtils.isEmpty(password)) {
            return new Triple<>(username, password, FAILURE_MODE_NO_PASSWORD);
        }

        // last resort, all is well, still send migration status
        return new Triple<>(username, password, FAILURE_MODE_NONE);
    }

    public static void deleteAccountDetails(Context ctx) {
        setAccountDetails(ctx, null, null);
        boolean deleted = EncryptedSharedPreferencesHelper.deleteSharedPreferences(ctx);
        if (!deleted) {
            Utils.toast("Failed to delete EncryptedSharedPreferences", ctx);
        }
    }

    public static void setAccountDetails(Context ctx, String username, String password) {
        SharedPreferences sharedPreferences;
        try {
            sharedPreferences = EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(ctx);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
        sharedPrefsEditor.putString(KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD, password);
        sharedPrefsEditor.apply();

        setAccountUsername(ctx, username);
    }

    public static void showLoginPrompt(FragmentManager fm) {
        new LoginDialogFragment().show(fm, LoginDialogFragment.TAG);
    }

    public static boolean handlePossibleError(Triple<String, String, Integer> account, FragmentManager fm, Context ctx) {
        if (account.getThird() == AccountUtils.FAILURE_MODE_NONE) {
            return false;
        }

        if (fm != null) {
            AccountUtils.showLoginPrompt(fm);
        }

        switch (account.getThird()) {
            case AccountUtils.FAILURE_MODE_MAINKEY:
                Utils.toast("Login failed, cause: Couldn't get AndroidX MasterKey", ctx);
                break;
            case AccountUtils.FAILURE_MODE_ENCRYPTED_PREFERENCES_EXCEPTION:
                Utils.toast("Login failed, cause: EncryptedSharedPreferences threw exception", ctx);
                break;
            case AccountUtils.FAILURE_MODE_NO_USERNAME:
                Utils.toast("Login failed, cause: No saved username", ctx);
                break;
            case AccountUtils.FAILURE_MODE_NO_PASSWORD:
                Utils.toast("Login failed, cause: No saved password", ctx);
                break;
        }
        return true;
    }
}
