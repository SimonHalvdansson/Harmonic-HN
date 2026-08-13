package com.simon.harmonichackernews.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.local.LocalModelManager
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.AndroidNetworkStatus
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.presentation.UserMessageStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidCredentialStore(context: Context) :
    CredentialStore,
    ObservableHackerNewsAccountRepository {
    private val appContext = context.applicationContext
    override val accountState: StateFlow<HackerNewsAccount?> = processAccountState.asStateFlow()

    init {
        publishAccount()
    }

    override fun read(id: String): String? = when (id) {
        CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.getApiKey(appContext)
        CredentialIds.HACKER_NEWS_USERNAME -> AndroidHackerNewsAccountStorage.load(appContext)?.username
        CredentialIds.HACKER_NEWS_PASSWORD -> AndroidHackerNewsAccountStorage.load(appContext)?.password
        else -> null
    }

    override fun write(id: String, value: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.setApiKey(appContext, value)
            CredentialIds.HACKER_NEWS_USERNAME -> load()?.let { current ->
                AndroidHackerNewsAccountStorage.save(appContext, HackerNewsAccount(value, current.password))
            } ?: false
            CredentialIds.HACKER_NEWS_PASSWORD -> load()?.let { current ->
                AndroidHackerNewsAccountStorage.save(appContext, HackerNewsAccount(current.username, value))
            } ?: false
            else -> false
        }.also { if (it) publishAccount() }

    override fun remove(id: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.clearApiKey(appContext)
            CredentialIds.HACKER_NEWS_USERNAME, CredentialIds.HACKER_NEWS_PASSWORD ->
                AndroidHackerNewsAccountStorage.clear(appContext)
            else -> false
        }.also { if (it) publishAccount() }

    override fun load(): HackerNewsAccount? = AndroidHackerNewsAccountStorage.load(appContext)

    override fun save(account: HackerNewsAccount): Boolean =
        AndroidHackerNewsAccountStorage.save(appContext, account).also { publishAccount() }

    override fun clear(): Boolean = AndroidHackerNewsAccountStorage.clear(appContext).also {
        publishAccount()
    }

    override suspend fun saveAccount(account: HackerNewsAccount): Boolean =
        accountMutationMutex.withLock { withContext(Dispatchers.IO) { save(account) } }

    override suspend fun clearAccount(): Boolean =
        accountMutationMutex.withLock { withContext(Dispatchers.IO) { clear() } }

    private fun publishAccount() {
        processAccountState.value = AndroidHackerNewsAccountStorage.load(appContext)
    }

    private companion object {
        val processAccountState = MutableStateFlow<HackerNewsAccount?>(null)
        val accountMutationMutex = Mutex()
    }
}

class AndroidBookmarkStore(context: Context) : ObservableBookmarkStore by StoredBookmarkStore(
    AndroidKeyValueStore.global(context.applicationContext),
)

class AndroidHistoryStore(context: Context) : ObservableHistoryStore by StoredHistoryStore(
    AndroidKeyValueStore.global(context.applicationContext),
)

class AndroidClipboardService(context: Context) : ClipboardService {
    private val clipboard = context.applicationContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(label: String, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

class AndroidConnectivityService(context: Context) : ConnectivityService {
    private val appContext = context.applicationContext

    override fun isOnline(): Boolean = AndroidNetworkStatus.isOnline(appContext)

    override fun isUnmetered(): Boolean = AndroidNetworkStatus.isUnmetered(appContext)
}

class AndroidExternalLinkOpener(
    context: Context,
    private val userMessages: UserMessageStore,
) : ExternalLinkOpener {
    private val context = context

    override fun open(request: ExternalLinkRequest) {
        val opened = if (request.preferInApp) {
            AndroidExternalLinkLauncher.openCustomTab(context, request.url, request.shareable)
        } else {
            AndroidExternalLinkLauncher.openExternalBrowser(context, request.url)
        }
        if (!opened) userMessages.show("Couldn't open link to: ${request.url}")
    }
}

class AndroidShareService(context: Context) : ShareService {
    private val context = context

    override fun share(text: String, title: String?) {
        val content = title?.let { "$it | $text" } ?: text
        context.startActivity(ShareUtils.getShareIntent(content))
    }
}

class AndroidCacheStore(context: Context) : CacheStore {
    private val root = File(context.applicationContext.cacheDir, "shared_cache")

    override suspend fun read(namespace: String, key: String): ByteArray? = withContext(
        Dispatchers.IO,
    ) {
        resolve(namespace, key).takeIf(File::isFile)?.readBytes()
    }

    override suspend fun write(namespace: String, key: String, value: ByteArray) = withContext(
        Dispatchers.IO,
    ) {
        resolve(namespace, key).also { file ->
            file.parentFile?.mkdirs()
            file.writeBytes(value)
        }
        Unit
    }

    override suspend fun remove(namespace: String, key: String) = withContext(Dispatchers.IO) {
        resolve(namespace, key).delete()
        Unit
    }

    override suspend fun clear(namespace: String) = withContext(Dispatchers.IO) {
        File(root, safeName(namespace)).deleteRecursively()
        Unit
    }

    private fun resolve(namespace: String, key: String): File =
        File(File(root, safeName(namespace)), safeName(key))
}

class AndroidFileStore(context: Context) : FileStore {
    private val root = File(context.applicationContext.filesDir, "shared_files")

    override suspend fun read(reference: String): ByteArray? = withContext(Dispatchers.IO) {
        resolve(reference).takeIf(File::isFile)?.readBytes()
    }

    override suspend fun write(reference: String, value: ByteArray) = withContext(Dispatchers.IO) {
        resolve(reference).also { file ->
            file.parentFile?.mkdirs()
            file.writeBytes(value)
        }
        Unit
    }

    override suspend fun remove(reference: String): Boolean = withContext(Dispatchers.IO) {
        resolve(reference).delete()
    }

    private fun resolve(reference: String): File = File(root, safeName(reference))
}

class AndroidArticleViewer(
    context: Context,
    private val userMessages: UserMessageStore,
) : ArticleViewer {
    private val context = context

    override fun open(request: ArticleRequest) {
        if (!AndroidExternalLinkLauncher.openCustomTab(context, request.url)) {
            userMessages.show("Couldn't open link to: ${request.url}")
        }
    }
}

class AndroidNotificationScheduler(context: Context) : NotificationScheduler {
    private val appContext = context.applicationContext

    override suspend fun schedule(request: NotificationRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Harmonic updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val contentIntent = request.deepLink?.let { deepLink ->
            PendingIntent.getActivity(
                appContext,
                request.id.hashCode(),
                Intent(Intent.ACTION_VIEW, deepLink.toUri()).setPackage(appContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_comment)
            .setContentTitle(request.title)
            .setContentText(request.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(appContext).notify(request.id.hashCode(), notification)
    }

    override suspend fun cancel(id: String) {
        NotificationManagerCompat.from(appContext).cancel(id.hashCode())
    }

    private companion object {
        const val CHANNEL_ID = "harmonic_shared_updates"
    }
}

class AndroidLocalSummaryEngine(context: Context) : LocalSummaryEngine {
    private val appContext = context.applicationContext

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val model = LocalModelManager.getSelectedModel(appContext)
        LocalModelManager.isSelectedModelDownloaded(appContext) &&
            LocalAiRuntimeManager.isRuntimeInstalled(appContext, model.runtime)
    }

    override suspend fun summarize(request: SummaryRequest): SummaryResult = withContext(
        Dispatchers.IO,
    ) {
        var loadMillis = 0L
        val text = LocalModelInference.summarize(
            appContext,
            request.text,
            LocalModelInference.ProgressCallback {},
            LocalModelInference.LoadCallback { loadMillis = it },
        )
        SummaryResult(text = text, debugInfo = "modelLoadMillis=$loadMillis")
    }
}

object AndroidPlatformDependencies {
    fun create(
        context: Context,
        bookmarkStore: ObservableBookmarkStore = AndroidBookmarkStore(context),
        userMessages: UserMessageStore = UserMessageStore(),
    ): AppPlatformDependencies {
        val accounts = AndroidCredentialStore(context)
        return AppPlatformDependencies(
            credentials = accounts,
            accounts = accounts,
            capabilities = OptionalPlatformCapabilities(
                bookmarks = PlatformCapability.Available(bookmarkStore),
                history = PlatformCapability.Available(AndroidHistoryStore(context)),
                cache = PlatformCapability.Available(AndroidCacheStore(context)),
                files = PlatformCapability.Available(AndroidFileStore(context)),
                externalLinks = PlatformCapability.Available(
                    AndroidExternalLinkOpener(context, userMessages),
                ),
                sharing = PlatformCapability.Available(AndroidShareService(context)),
                clipboard = PlatformCapability.Available(AndroidClipboardService(context)),
                connectivity = PlatformCapability.Available(AndroidConnectivityService(context)),
                notifications = PlatformCapability.Available(AndroidNotificationScheduler(context)),
                articles = PlatformCapability.Available(AndroidArticleViewer(context, userMessages)),
                localSummary = PlatformCapability.Available(AndroidLocalSummaryEngine(context)),
            ),
        )
    }
}

private fun safeName(value: String): String {
    require(value.isNotBlank()) { "A non-blank storage key is required" }
    return MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
