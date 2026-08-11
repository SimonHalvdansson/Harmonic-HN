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
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.local.LocalModelManager
import com.simon.harmonichackernews.utils.HistoriesUtils
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.utils.Utils
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext

    override fun read(id: String): String? = when (id) {
        CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.getApiKey(appContext)
        CredentialIds.HACKER_NEWS_USERNAME -> AccountUtils.getAccountUsername(appContext)
        CredentialIds.HACKER_NEWS_PASSWORD -> AccountUtils.getAccountDetails(appContext).second
        else -> null
    }

    override fun write(id: String, value: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.setApiKey(appContext, value)
            CredentialIds.HACKER_NEWS_USERNAME -> {
                AccountUtils.setAccountUsername(appContext, value)
                true
            }
            CredentialIds.HACKER_NEWS_PASSWORD -> {
                val username = AccountUtils.getAccountUsername(appContext)
                AccountUtils.setAccountDetails(appContext, username, value)
                true
            }
            else -> false
        }

    override fun remove(id: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.clearApiKey(appContext)
            CredentialIds.HACKER_NEWS_USERNAME, CredentialIds.HACKER_NEWS_PASSWORD -> {
                AccountUtils.deleteAccountDetails(appContext)
                true
            }
            else -> false
        }
}

class AndroidBookmarkStore(context: Context) : BookmarkStore {
    private val appContext = context.applicationContext
    private val savedItems = SavedItemsRepository(AndroidKeyValueStore.global(appContext))

    override fun load(): List<Bookmark> = SavedItemCodec.toBookmarks(
        savedItems.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true),
    )

    override fun add(id: Int) {
        savedItems.setMembership(
            SavedItemSource.BOOKMARKS,
            id,
            present = true,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    override fun remove(id: Int) {
        savedItems.setMembership(
            SavedItemSource.BOOKMARKS,
            id,
            present = false,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    override fun clear() = savedItems.saveItems(SavedItemSource.BOOKMARKS, emptyList())
}

class AndroidHistoryStore(context: Context) : HistoryStore {
    private val appContext = context.applicationContext

    override fun initialize() = HistoriesUtils.init(appContext)

    override fun load(): List<History> = HistoriesUtils.loadHistories(appContext, true)

    override fun record(id: Int, createdAtMillis: Long) {
        HistoriesUtils.addHistory(appContext, id, createdAtMillis)
    }

    override fun remove(id: Int) = HistoriesUtils.removeHistoryById(appContext, id)

    override fun clear() = HistoriesUtils.clearHistories(appContext)

    override fun contains(id: Int): Boolean = HistoriesUtils.isHistoryExist(id)

    override val size: Int
        get() = HistoriesUtils.size()

    override val changeVersion: Long
        get() = HistoriesUtils.getChangeVersion()
}

class AndroidClipboardService(context: Context) : ClipboardService {
    private val clipboard = context.applicationContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(label: String, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

class AndroidConnectivityService(context: Context) : ConnectivityService {
    private val appContext = context.applicationContext

    override fun isOnline(): Boolean = Utils.isNetworkAvailable(appContext)

    override fun isUnmetered(): Boolean = Utils.isOnWiFi(appContext)
}

class AndroidExternalLinkOpener(context: Context) : ExternalLinkOpener {
    private val context = context

    override fun open(request: ExternalLinkRequest) {
        if (request.preferInApp) {
            Utils.launchCustomTab(context, request.url, request.shareable)
        } else {
            Utils.launchInExternalBrowser(context, request.url)
        }
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

class AndroidArticleViewer(context: Context) : ArticleViewer {
    private val context = context

    override fun open(request: ArticleRequest) {
        Utils.launchCustomTab(context, request.url)
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

object AndroidPlatformServices {
    fun create(context: Context): PlatformServices = PlatformServices(
        credentials = AndroidCredentialStore(context),
        bookmarks = AndroidBookmarkStore(context),
        history = AndroidHistoryStore(context),
        cache = AndroidCacheStore(context),
        files = AndroidFileStore(context),
        externalLinks = AndroidExternalLinkOpener(context),
        sharing = AndroidShareService(context),
        clipboard = AndroidClipboardService(context),
        connectivity = AndroidConnectivityService(context),
        notifications = AndroidNotificationScheduler(context),
        articles = AndroidArticleViewer(context),
        localSummary = AndroidLocalSummaryEngine(context),
    )
}

private fun safeName(value: String): String {
    require(value.isNotBlank()) { "A non-blank storage key is required" }
    return MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
