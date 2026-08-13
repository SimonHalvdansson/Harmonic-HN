package com.simon.harmonichackernews.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import com.simon.harmonichackernews.network.createAndroidLocalSummaryEngine
import com.simon.harmonichackernews.network.AndroidReplyNotificationPlatform
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.utils.AndroidAiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.AndroidNetworkStatus
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.summary.LocalModelService
import java.util.Calendar
import java.util.Date

class AndroidCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext
    private val aiCredentials = AndroidAiSummaryApiKeyStore(appContext)
    override fun read(id: String): String? = when (id) {
        CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.getApiKey()
        CredentialIds.HACKER_NEWS_USERNAME -> AndroidHackerNewsAccountStorage.load(appContext)?.username
        CredentialIds.HACKER_NEWS_PASSWORD -> AndroidHackerNewsAccountStorage.load(appContext)?.password
        else -> null
    }

    override fun write(id: String, value: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.setApiKey(value)
            CredentialIds.HACKER_NEWS_USERNAME ->
                AndroidHackerNewsAccountStorage.load(appContext)?.let { current ->
                AndroidHackerNewsAccountStorage.save(appContext, HackerNewsAccount(value, current.password))
            } ?: false
            CredentialIds.HACKER_NEWS_PASSWORD ->
                AndroidHackerNewsAccountStorage.load(appContext)?.let { current ->
                AndroidHackerNewsAccountStorage.save(appContext, HackerNewsAccount(current.username, value))
            } ?: false
            else -> false
        }

    override fun remove(id: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.clearApiKey()
            CredentialIds.HACKER_NEWS_USERNAME, CredentialIds.HACKER_NEWS_PASSWORD ->
                AndroidHackerNewsAccountStorage.clear(appContext)
            else -> false
        }
}

/** Android only supplies the atomic encrypted vault; shared code adds observation and locking. */
class AndroidHackerNewsAccountRepository(context: Context) : HackerNewsAccountRepository {
    private val appContext = context.applicationContext
    override fun load(): HackerNewsAccount? = AndroidHackerNewsAccountStorage.load(appContext)
    override fun save(account: HackerNewsAccount): Boolean =
        AndroidHackerNewsAccountStorage.save(appContext, account)
    override fun clear(): Boolean = AndroidHackerNewsAccountStorage.clear(appContext)
}

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

class AndroidTimeFormatter(context: Context) : PlatformTimeFormatter {
    private val appContext = context.applicationContext

    override fun time(epochMillis: Long): String =
        DateFormat.getTimeFormat(appContext).format(Date(epochMillis))

    override fun localDate(epochMillis: Long): LocalCalendarDate {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return LocalCalendarDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    override fun uses24HourClock(): Boolean = DateFormat.is24HourFormat(appContext)
}

class AndroidExternalLinkOpener(
    context: Context,
) : ExternalLinkOpener {
    private val context = context

    override fun open(request: ExternalLinkRequest): Boolean = if (request.preferInApp) {
            AndroidExternalLinkLauncher.openCustomTab(context, request)
        } else {
            AndroidExternalLinkLauncher.openExternalBrowser(context, request)
        }
}

class AndroidShareService(context: Context) : ShareService {
    private val context = context

    override fun share(text: String, title: String?) {
        val content = title?.let { "$it | $text" } ?: text
        context.startActivity(ShareUtils.getShareIntent(content))
    }
}

fun createAndroidPlatformDependencies(
    context: Context,
    localModels: LocalModelService,
): AppPlatformDependencies {
    val credentials = AndroidCredentialStore(context)
    val accounts = ObservableAccountRepositoryAdapter(
        AndroidHackerNewsAccountRepository(context),
    )
    return AppPlatformDependencies(
        credentials = credentials,
        accounts = accounts,
        history = AndroidHistoryStore(context),
        externalLinks = AndroidExternalLinkOpener(context),
        sharing = AndroidShareService(context),
        clipboard = AndroidClipboardService(context),
        connectivity = AndroidConnectivityService(context),
        timeFormatting = AndroidTimeFormatter(context),
        replyNotifications = AndroidReplyNotificationPlatform(context),
        localSummary = createAndroidLocalSummaryEngine(context, localModels),
    )
}
