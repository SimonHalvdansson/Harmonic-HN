package com.simon.harmonichackernews.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.utils.AndroidKeystoreSecretStore
import com.simon.harmonichackernews.utils.EncryptedSharedPreferencesHelper
import com.simon.harmonichackernews.utils.MigratingSecretReadResult
import com.simon.harmonichackernews.utils.readWithLegacyMigration
import java.nio.ByteBuffer

/** Android encrypted persistence adapter behind the shared account repository contract. */
internal object AndroidHackerNewsAccountStorage {
    private const val USERNAME_KEY =
        "com.simon.harmonichackernews.KEY_UNENCRYPTED_SHARED_PREFERENCES_USERNAME"
    private const val PASSWORD_KEY =
        "com.simon.harmonichackernews.KEY_ENCRYPTED_SHARED_PREFERENCES_PASSWORD"
    private const val KEYSTORE_VAULT_NAME = "HARMONIC_ACCOUNT_KEYSTORE_VAULT"
    private const val KEYSTORE_VAULT_ACCOUNT_KEY = "account"
    private const val KEYSTORE_VAULT_KEY_ALIAS = "_harmonic_hn_account_aes_gcm_v2_"
    private const val TAG = "HnAccountStorage"
    private var cachedSecretStore: AndroidKeystoreSecretStore? = null
    private var cachedLegacyEncryptedPreferences: SharedPreferences? = null

    fun load(context: Context): HackerNewsAccount? = synchronized(this) {
        runCatching {
            when (
                val result = readWithLegacyMigration(
                    destination = secretStore(context),
                    onMigrationFailure = {
                        Log.e(TAG, "Unable to migrate the legacy Hacker News account", it)
                    },
                ) { loadLegacyAccount(context)?.let(HackerNewsAccountPayloadCodec::encode) }
            ) {
                is MigratingSecretReadResult.Failure -> throw result.error
                is MigratingSecretReadResult.Success ->
                    result.value?.let(HackerNewsAccountPayloadCodec::decode)
            }
        }.onFailure { Log.e(TAG, "Unable to read the Hacker News account", it) }
            .getOrThrow()
    }

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, account: HackerNewsAccount): Boolean = synchronized(this) {
        runCatching {
            secretStore(context).write(
                HackerNewsAccountPayloadCodec.encode(
                    account.copy(username = account.username.trim()),
                ),
            )
        }.onFailure { Log.e(TAG, "Unable to save the Hacker News account", it) }
            .getOrDefault(false)
    }

    // The KTX helper does not expose commit()'s result, which this migration cleanup must check.
    @SuppressLint("ApplySharedPref", "UseKtx")
    fun clear(context: Context): Boolean = synchronized(this) {
        val cleared = runCatching { secretStore(context).reset() }
            .onFailure { Log.e(TAG, "Unable to clear the Hacker News account", it) }
            .getOrDefault(false)
        if (!cleared) return false

        // The completed-migration marker above is authoritative. These legacy removals are
        // best-effort cleanup and can never make stale credentials visible again.
        if (!globalPreferences(context).edit().remove(USERNAME_KEY).commit()) {
            Log.w(TAG, "Unable to remove the legacy Hacker News username")
        }
        runCatching {
            legacyEncryptedPreferences(context).edit().remove(PASSWORD_KEY).commit()
        }.onFailure { Log.w(TAG, "Unable to remove the legacy Hacker News password", it) }
        true
    }

    private fun loadLegacyAccount(context: Context): HackerNewsAccount? {
        val username = globalPreferences(context).getString(USERNAME_KEY, null)?.trim()
        if (username.isNullOrBlank()) return null
        val password = legacyEncryptedPreferences(context).getString(PASSWORD_KEY, null)
        if (password.isNullOrEmpty()) return null
        return HackerNewsAccount(username, password)
    }

    private fun secretStore(context: Context): AndroidKeystoreSecretStore {
        cachedSecretStore?.let { return it }
        return AndroidKeystoreSecretStore(
            context = context.applicationContext,
            preferencesName = KEYSTORE_VAULT_NAME,
            preferenceKey = KEYSTORE_VAULT_ACCOUNT_KEY,
            keyAlias = KEYSTORE_VAULT_KEY_ALIAS,
        ).also { cachedSecretStore = it }
    }

    private fun legacyEncryptedPreferences(context: Context): SharedPreferences {
        cachedLegacyEncryptedPreferences?.let { return it }
        return EncryptedSharedPreferencesHelper.getEncryptedSharedPreferences(
            context.applicationContext,
        ).also { cachedLegacyEncryptedPreferences = it }
    }

    private fun globalPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            AppLaunchPreferenceKeys.STORE_NAME,
            Context.MODE_PRIVATE,
        )
}

internal object HackerNewsAccountPayloadCodec {
    private const val VERSION = 1

    fun encode(account: HackerNewsAccount): ByteArray {
        val username = account.username.encodeToByteArray()
        val password = account.password.encodeToByteArray()
        return ByteBuffer.allocate(1 + Int.SIZE_BYTES + username.size + password.size)
            .put(VERSION.toByte())
            .putInt(username.size)
            .put(username)
            .put(password)
            .array()
    }

    fun decode(payload: ByteArray): HackerNewsAccount {
        require(payload.size >= 1 + Int.SIZE_BYTES + 2) { "Account payload is too short" }
        val buffer = ByteBuffer.wrap(payload)
        require(buffer.get().toInt() and 0xff == VERSION) { "Unsupported account payload version" }
        val usernameSize = buffer.int
        require(usernameSize > 0 && buffer.remaining() > usernameSize) {
            "Invalid account username length"
        }
        val username = ByteArray(usernameSize).also(buffer::get).decodeToString()
        val password = ByteArray(buffer.remaining()).also(buffer::get).decodeToString()
        return HackerNewsAccount(username, password)
    }
}
