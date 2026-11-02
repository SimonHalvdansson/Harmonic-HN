package com.simon.harmonichackernews.platform

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidCredentialStoreTest {
    @Test
    fun accountAccessLeavesAiVaultUnopenedUntilAnAiCredentialIsRequested() {
        val application = RuntimeEnvironment.getApplication()
        // An empty, previously migrated vault can be read without exercising Android Keystore.
        application.getSharedPreferences(AI_VAULT, Context.MODE_PRIVATE).edit()
            .putBoolean("legacy_migration_complete", true)
            .commit()
        val context = object : ContextWrapper(application) {
            var aiVaultOpens = 0
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                if (name == AI_VAULT) aiVaultOpens++
                return super.getSharedPreferences(name, mode)
            }
        }
        val account = HackerNewsAccount("reader", "password")
        val credentials = AndroidCredentialStore(context) { account }

        assertEquals("reader", credentials.read(CredentialIds.HACKER_NEWS_USERNAME))
        assertEquals("password", credentials.read(CredentialIds.HACKER_NEWS_PASSWORD))
        assertFalse(credentials.write(CredentialIds.HACKER_NEWS_USERNAME, "other"))
        assertFalse(credentials.remove(CredentialIds.HACKER_NEWS_PASSWORD))
        assertNull(credentials.read("unknown"))
        assertEquals(0, context.aiVaultOpens)

        assertEquals("", credentials.read(CredentialIds.AI_SUMMARY_API_KEY))
        assertEquals(1, context.aiVaultOpens)
        assertEquals("", credentials.read(CredentialIds.AI_SUMMARY_API_KEY))
        assertEquals(1, context.aiVaultOpens)
    }

    private companion object {
        const val AI_VAULT = "HARMONIC_AI_SUMMARY_KEYSTORE_VAULT"
    }
}
