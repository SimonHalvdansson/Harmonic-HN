package com.simon.harmonichackernews.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FeaturePlatformDependenciesTest {
    @Test
    fun appCompositionCanDeclareOnlyItsSupportedSubset() {
        val accounts = MemoryAccountRepository()
        val links = RecordingLinkOpener()
        val dependencies = AppPlatformDependencies(
            accounts = accounts,
            capabilities = OptionalPlatformCapabilities(
                externalLinks = PlatformCapability.Available(links),
                localSummary = PlatformCapability.Unavailable(
                    "Local summaries",
                    "No native runtime is installed",
                ),
            ),
        )

        assertSame(accounts, dependencies.accounts)
        assertSame(links, dependencies.capabilities.externalLinks.requireService())
        assertNull(dependencies.capabilities.cache.getOrNull())

        val error = assertFailsWith<PlatformCapabilityUnavailableException> {
            dependencies.capabilities.localSummary.requireService()
        }
        assertEquals("Local summaries", error.capability)
    }

    @Test
    fun featureDependenciesContainOnlyFacilitiesTheFeatureUses() {
        val links = RecordingLinkOpener()
        val dependencies = SubmissionsPlatformDependencies(links)

        dependencies.externalLinks.open(ExternalLinkRequest("https://example.com"))

        assertEquals("https://example.com", links.lastUrl)
    }

    private class MemoryAccountRepository : ObservableHackerNewsAccountRepository {
        private var account: HackerNewsAccount? = null
        private val mutableAccountState = MutableStateFlow<HackerNewsAccount?>(null)
        override val accountState: StateFlow<HackerNewsAccount?> = mutableAccountState

        override fun load(): HackerNewsAccount? = account
        override fun save(account: HackerNewsAccount): Boolean = true.also {
            this.account = account
            mutableAccountState.value = account
        }
        override fun clear(): Boolean = true.also {
            account = null
            mutableAccountState.value = null
        }
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean = save(account)
        override suspend fun clearAccount(): Boolean = clear()
    }

    private class RecordingLinkOpener : ExternalLinkOpener {
        var lastUrl: String? = null
        override fun open(request: ExternalLinkRequest) {
            lastUrl = request.url
        }
    }
}
