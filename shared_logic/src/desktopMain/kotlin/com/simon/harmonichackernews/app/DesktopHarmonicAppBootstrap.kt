package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.NetworkGraph
import com.simon.harmonichackernews.network.NetworkGraphEnvironment
import com.simon.harmonichackernews.network.NetworkGraphFactory
import com.simon.harmonichackernews.platform.AppPlatformDependencies
import com.simon.harmonichackernews.platform.CredentialBackedHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.CredentialStore
import com.simon.harmonichackernews.platform.ClipboardService
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.platform.ExternalLinkOpener
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.LocalCalendarDate
import com.simon.harmonichackernews.platform.PlatformTimeFormatter
import com.simon.harmonichackernews.platform.ShareService
import com.simon.harmonichackernews.platform.StoredHistoryStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalTime

/**
 * Desktop lifecycle owner for the real shared application and CIO networking graphs.
 *
 * Constructing the graph does not make a request. A production desktop host can provide durable
 * [KeyValueStore] implementations and supported [AppPlatformDependencies]; [inMemory] is intended
 * for previews and smoke hosts that must not write credentials, settings, or application data.
 */
class DesktopHarmonicAppBootstrap(
    userAgent: String,
    platform: AppPlatformDependencies,
    host: HarmonicHostConfiguration,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closed = false

    val network: NetworkGraph = NetworkGraphFactory.create(NetworkGraphEnvironment(
        scope = scope,
        userAgent = userAgent,
        engine = { CIO.create() },
    ))
    val app = HarmonicAppComposition(
        network = network,
        platform = platform,
        host = host,
    )
    val scene = app.createScene()

    fun close() {
        if (closed) return
        closed = true
        scene.close()
        network.close()
        scope.cancel()
    }

    companion object {
        /** Creates an operational but side-effect-free host with unsupported native facilities. */
        fun inMemory(userAgent: String): DesktopHarmonicAppBootstrap {
            val settings = InMemoryKeyValueStore()
            val appData = InMemoryKeyValueStore()
            val credentials = InMemoryCredentialStore()
            val host = HarmonicHostConfiguration.inMemory(
                metadata = AppMetadata(
                    name = "Harmonic Desktop Smoke",
                    buildType = "smoke",
                    debug = true,
                    debugSettingsEnabled = true,
                ),
                settingsStore = settings,
                appDataStore = appData,
                settingsChanges = settings.changes,
                currentMinutesFromMidnight = {
                    LocalTime.now().let { it.hour * 60 + it.minute }
                },
                systemDark = { false },
            )
            return DesktopHarmonicAppBootstrap(
                userAgent = userAgent,
                platform = AppPlatformDependencies(
                    credentials = credentials,
                    accounts = CredentialBackedHackerNewsAccountRepository(
                        credentials,
                    ),
                    history = StoredHistoryStore(appData),
                    externalLinks = SideEffectFreeExternalLinkOpener,
                    sharing = SideEffectFreeShareService,
                    clipboard = SideEffectFreeClipboardService,
                    connectivity = DesktopSmokeConnectivity,
                    timeFormatting = DesktopTimeFormatter(),
                ),
                host = host,
            )
        }
    }
}

private data object SideEffectFreeExternalLinkOpener : ExternalLinkOpener {
    override fun open(request: ExternalLinkRequest): Boolean = true
}

private data object SideEffectFreeShareService : ShareService {
    override fun share(text: String, title: String?) = Unit
}

private data object SideEffectFreeClipboardService : ClipboardService {
    override fun copy(label: String, text: String) = Unit
}

private data object DesktopSmokeConnectivity : ConnectivityService {
    override fun isOnline(): Boolean = true
    override fun isUnmetered(): Boolean = true
}

private class DesktopTimeFormatter : PlatformTimeFormatter {
    private val zone = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    override fun localDate(epochMillis: Long): LocalCalendarDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().let { date ->
            LocalCalendarDate(date.year, date.monthValue, date.dayOfMonth)
        }

    override fun uses24HourClock(): Boolean = true
}

private class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<String, String>()

    override fun read(id: String): String? = values[id]

    override fun write(id: String, value: String): Boolean {
        values[id] = value
        return true
    }

    override fun remove(id: String): Boolean {
        values.remove(id)
        return true
    }
}
