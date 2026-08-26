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
import com.simon.harmonichackernews.platform.ExternalLinkPolicy
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.LocalCalendarDate
import com.simon.harmonichackernews.platform.PlatformTimeFormatter
import com.simon.harmonichackernews.platform.ShareService
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.platform.StoredHistoryStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.DesktopFileKeyValueStore
import com.simon.harmonichackernews.summary.DesktopLocalAiEnvironment
import io.ktor.client.engine.cio.CIO
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.text.DateFormat
import java.text.SimpleDateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.io.files.Path as KotlinPath

/**
 * Desktop lifecycle owner for the real shared application and CIO networking graphs.
 *
 * Constructing the graph does not make a request. A production desktop host can provide durable
 * [KeyValueStore] implementations and supported [AppPlatformDependencies]; [inMemory] is intended
 * for previews and tests that must not write credentials, settings, or application data.
 */
class DesktopHarmonicAppBootstrap(
    userAgent: String,
    platform: AppPlatformDependencies,
    host: HarmonicHostConfiguration,
    private val closePlatformServices: () -> Unit = {},
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
        app.platform.accounts.close()
        closePlatformServices()
        network.close()
        scope.cancel()
    }

    companion object {
        /** Creates the durable desktop application graph used by the installed app. */
        fun production(
            userAgent: String,
            metadata: AppMetadata,
            paths: DesktopAppPaths = DesktopAppPaths.default(),
        ): DesktopHarmonicAppBootstrap {
            Files.createDirectories(paths.configDirectory)
            Files.createDirectories(paths.filesDirectory)
            Files.createDirectories(paths.cacheDirectory)
            val settings = DesktopFileKeyValueStore(paths.configDirectory.resolve("settings.properties"))
            val appData = DesktopFileKeyValueStore(paths.configDirectory.resolve("app-data.properties"))
            val previewCache = DesktopFileKeyValueStore(
                paths.cacheDirectory.resolve("preview-cache.properties"),
            )
            val fileAccess = DesktopFileKeyValueStore(
                paths.cacheDirectory.resolve("file-access.properties"),
            )
            val credentials = desktopCredentialStore()
            val localAi = DesktopLocalAiEnvironment.create(
                preferences = settings,
                modelsRoot = paths.filesDirectory.resolve(
                    StorageKeyPolicy.LOCAL_MODELS_DIRECTORY,
                ),
                cacheRoot = paths.cacheDirectory,
                userAgent = userAgent,
            )
            val persistentStorage = HarmonicPersistentStorageFactory.create(
                roots = HarmonicStorageRoots(
                    files = KotlinPath(paths.filesDirectory.toString()),
                    pdfCache = KotlinPath(paths.cacheDirectory.resolve("pdf").toString()),
                ),
                appDataStore = appData,
                fileAccessStore = fileAccess,
            )
            val history = StoredHistoryStore(appData).also { it.initialize() }
            val host = HarmonicHostConfiguration(
                metadata = metadata,
                settingsStore = settings,
                appDataStore = appData,
                previewCacheStore = previewCache,
                settingsChanges = settings.changes,
                appearanceChanges = DesktopSystemAppearance.changes,
                currentMinutesFromMidnight = {
                    LocalTime.now().let { it.hour * 60 + it.minute }
                },
                systemDark = DesktopSystemAppearance::isDark,
                showCommentsUpButtonByDefault = true,
                preloadCommentsFromStoriesByDefault = true,
                localModels = localAi.models,
                storyCacheRepository = persistentStorage.storyCacheRepository,
                articleSnapshotStore = persistentStorage.articleSnapshotStore,
                pdfDownloadStore = persistentStorage.pdfDownloadStore,
            )
            return try {
                DesktopHarmonicAppBootstrap(
                    userAgent = userAgent,
                    platform = AppPlatformDependencies(
                        credentials = credentials,
                        accounts = CredentialBackedHackerNewsAccountRepository(
                            credentials,
                            storageDispatcher = Dispatchers.IO,
                        ),
                        history = history,
                        externalLinks = DesktopExternalLinkOpener,
                        sharing = DesktopShareService,
                        clipboard = DesktopClipboardService,
                        connectivity = DesktopConnectivity,
                        timeFormatting = DesktopTimeFormatter(),
                        localSummary = localAi.summary,
                    ),
                    host = host,
                    closePlatformServices = localAi::close,
                )
            } catch (error: Throwable) {
                localAi.close()
                throw error
            }
        }

        /** Creates an operational but side-effect-free host with unsupported native facilities. */
        fun inMemory(userAgent: String): DesktopHarmonicAppBootstrap {
            val settings = InMemoryKeyValueStore()
            val appData = InMemoryKeyValueStore()
            val credentials = InMemoryCredentialStore()
            val host = HarmonicHostConfiguration.inMemory(
                metadata = AppMetadata(
                    name = "Harmonic Desktop Preview",
                    buildType = "preview",
                    debug = true,
                    debugSettingsEnabled = true,
                ),
                settingsStore = settings,
                appDataStore = appData,
                settingsChanges = settings.changes,
                showCommentsUpButtonByDefault = true,
                preloadCommentsFromStoriesByDefault = true,
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
                        storageDispatcher = Dispatchers.IO,
                    ),
                    history = StoredHistoryStore(appData),
                    externalLinks = SideEffectFreeExternalLinkOpener,
                    sharing = SideEffectFreeShareService,
                    clipboard = SideEffectFreeClipboardService,
                    connectivity = DesktopPreviewConnectivity,
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

private data object DesktopPreviewConnectivity : ConnectivityService {
    override fun isOnline(): Boolean = true
    override fun isUnmetered(): Boolean = true
}

private class DesktopTimeFormatter : PlatformTimeFormatter {
    private val zone = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

    override fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    override fun localDate(epochMillis: Long): LocalCalendarDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().let { date ->
            LocalCalendarDate(date.year, date.monthValue, date.dayOfMonth)
        }

    override fun uses24HourClock(): Boolean =
        (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)
            ?.toPattern()
            ?.any { it == 'H' || it == 'k' }
            ?: true
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

data class DesktopAppPaths(
    val configDirectory: Path,
    val filesDirectory: Path,
    val cacheDirectory: Path,
) {
    companion object {
        fun default(): DesktopAppPaths {
            (System.getProperty("harmonic.desktop.dataDir")
                ?: System.getenv("HARMONIC_DESKTOP_DATA_DIR"))
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.let { root ->
                    return DesktopAppPaths(
                        configDirectory = root.resolve("config"),
                        filesDirectory = root.resolve("files"),
                        cacheDirectory = root.resolve("cache"),
                    )
                }
            val home = Path.of(System.getProperty("user.home"))
            val os = System.getProperty("os.name").lowercase()
            val config = when {
                os.contains("mac") -> home.resolve("Library/Application Support/Harmonic")
                os.contains("win") -> Path.of(
                    System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString(),
                ).resolve("Harmonic")
                else -> Path.of(
                    System.getenv("XDG_CONFIG_HOME") ?: home.resolve(".config").toString(),
                ).resolve("harmonic")
            }
            val cache = when {
                os.contains("mac") -> home.resolve("Library/Caches/Harmonic")
                os.contains("win") -> Path.of(
                    System.getenv("LOCALAPPDATA") ?: home.resolve("AppData/Local").toString(),
                ).resolve("Harmonic/Cache")
                else -> Path.of(
                    System.getenv("XDG_CACHE_HOME") ?: home.resolve(".cache").toString(),
                ).resolve("harmonic")
            }
            return DesktopAppPaths(
                configDirectory = config,
                filesDirectory = config.resolve("files"),
                cacheDirectory = cache,
            )
        }
    }
}

private data object DesktopExternalLinkOpener : ExternalLinkOpener {
    override fun open(request: ExternalLinkRequest): Boolean =
        ExternalLinkPolicy.openCandidates(request.url).any { candidate ->
            runCatching {
                if (!Desktop.isDesktopSupported()) return@runCatching false
                Desktop.getDesktop().browse(URI(candidate))
                true
            }.getOrDefault(false)
        }
}

private data object DesktopClipboardService : ClipboardService {
    override fun copy(label: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

private data object DesktopShareService : ShareService {
    override fun share(text: String, title: String?) {
        DesktopClipboardService.copy(title ?: "Shared from Harmonic", text)
    }
}
