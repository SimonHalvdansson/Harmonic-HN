package com.simon.harmonichackernews.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.text.format.DateFormat
import com.simon.harmonichackernews.network.createAndroidLocalSummaryEngine
import com.simon.harmonichackernews.network.AndroidReplyNotificationPlatform
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.utils.AndroidAiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.AndroidConnectivityCapabilities
import com.simon.harmonichackernews.utils.AndroidConnectivityStatus
import com.simon.harmonichackernews.utils.AndroidNetworkStatus
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.summary.LocalModelService
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers

internal class AndroidCredentialStore(
    context: Context,
    private val currentAccount: () -> HackerNewsAccount?,
) : CredentialStore {
    private val appContext = context.applicationContext
    private val aiCredentials = AndroidAiSummaryApiKeyStore(appContext)
    override fun read(id: String): String? = when (id) {
        CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.getApiKey()
        CredentialIds.HACKER_NEWS_USERNAME -> currentAccount()?.username
        CredentialIds.HACKER_NEWS_PASSWORD -> currentAccount()?.password
        else -> null
    }

    override fun write(id: String, value: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.setApiKey(value)
            // Account mutations must use the suspend, atomically published account repository.
            CredentialIds.HACKER_NEWS_USERNAME, CredentialIds.HACKER_NEWS_PASSWORD -> false
            else -> false
        }

    override fun remove(id: String): Boolean =
        when (id) {
            CredentialIds.AI_SUMMARY_API_KEY -> aiCredentials.clearApiKey()
            CredentialIds.HACKER_NEWS_USERNAME, CredentialIds.HACKER_NEWS_PASSWORD -> false
            else -> false
        }
}

/** Android only supplies the atomic encrypted vault; shared code adds observation and locking. */
internal class AndroidHackerNewsAccountRepository(context: Context) : HackerNewsAccountRepository {
    private val appContext = context.applicationContext
    override fun load(): HackerNewsAccount? = AndroidHackerNewsAccountStorage.load(appContext)
    override fun save(account: HackerNewsAccount): Boolean =
        AndroidHackerNewsAccountStorage.save(appContext, account)
    override fun clear(): Boolean = AndroidHackerNewsAccountStorage.clear(appContext)
}

class AndroidHistoryStore(context: Context) : ObservableHistoryStore by StoredHistoryStore(
    AndroidKeyValueStore.global(context.applicationContext),
    storageDispatcher = Dispatchers.IO,
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
    private val manager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    @Volatile
    private var connectivityStatus = AndroidConnectivityStatus.Offline
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            manager?.getNetworkCapabilities(network)?.let(::updateCapabilities)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            updateCapabilities(networkCapabilities)
        }

        override fun onLost(network: Network) {
            val activeNetwork = manager?.activeNetwork
            val activeCapabilities = activeNetwork
                ?.takeUnless { it == network }
                ?.let { manager.getNetworkCapabilities(it) }
            if (activeCapabilities == null) {
                connectivityStatus = AndroidConnectivityStatus.Offline
            } else {
                updateCapabilities(activeCapabilities)
            }
        }
    }
    private val callbackRegistered: Boolean

    init {
        manager?.activeNetwork
            ?.let(manager::getNetworkCapabilities)
            ?.let(::updateCapabilities)
        callbackRegistered = manager?.let {
            runCatching { it.registerDefaultNetworkCallback(callback) }.isSuccess
        } == true
    }

    override fun isOnline(): Boolean =
        if (callbackRegistered) connectivityStatus.online else AndroidNetworkStatus.isOnline(appContext)

    override fun isUnmetered(): Boolean =
        if (callbackRegistered) connectivityStatus.unmetered
        else AndroidNetworkStatus.isUnmetered(appContext)

    private fun updateCapabilities(capabilities: NetworkCapabilities) {
        connectivityStatus = AndroidConnectivityCapabilities.evaluate(capabilities)
    }
}

class AndroidBatteryStatusService(context: Context) : BatteryStatusService {
    private val appContext = context.applicationContext

    override fun batteryPercent(): Int? = AndroidSettingsResources.batteryPercent(appContext)
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
    private val appContext = context.applicationContext

    override fun open(request: ExternalLinkRequest): Boolean = if (request.preferInApp) {
        AndroidExternalLinkLauncher.openCustomTab(appContext, request)
    } else {
        AndroidExternalLinkLauncher.openExternalBrowser(appContext, request)
    }
}

class AndroidShareService(context: Context) : ShareService {
    private val appContext = context.applicationContext

    override fun share(text: String, title: String?) {
        val content = title?.let { "$it | $text" } ?: text
        appContext.startActivity(
            ShareUtils.getShareIntent(content).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

fun createAndroidPlatformDependencies(
    context: Context,
    localModels: LocalModelService,
): AppPlatformDependencies {
    val accounts = ObservableAccountRepositoryAdapter(
        AndroidHackerNewsAccountRepository(context),
        storageDispatcher = Dispatchers.IO,
    )
    val credentials = AndroidCredentialStore(context, accounts::currentAccount)
    return AppPlatformDependencies(
        credentials = credentials,
        accounts = accounts,
        history = AndroidHistoryStore(context),
        externalLinks = AndroidExternalLinkOpener(context),
        sharing = AndroidShareService(context),
        clipboard = AndroidClipboardService(context),
        connectivity = AndroidConnectivityService(context),
        battery = AndroidBatteryStatusService(context),
        timeFormatting = AndroidTimeFormatter(context),
        credentialDispatcher = Dispatchers.IO,
        replyNotifications = AndroidReplyNotificationPlatform(context),
        localSummary = createAndroidLocalSummaryEngine(context, localModels),
    )
}
