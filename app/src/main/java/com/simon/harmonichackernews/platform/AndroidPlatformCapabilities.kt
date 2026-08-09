package com.simon.harmonichackernews.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.utils.HistoriesUtils
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.utils.Utils

class AndroidAiCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext

    override fun read(id: String): String? = when (id) {
        AI_SUMMARY_API_KEY -> AiSummaryApiKeyStore.getApiKey(appContext)
        else -> null
    }

    override fun write(id: String, value: String): Boolean =
        id == AI_SUMMARY_API_KEY && AiSummaryApiKeyStore.setApiKey(appContext, value)

    override fun remove(id: String): Boolean =
        id == AI_SUMMARY_API_KEY && AiSummaryApiKeyStore.clearApiKey(appContext)

    companion object {
        const val AI_SUMMARY_API_KEY = "ai_summary_api_key"
    }
}

class AndroidBookmarkStore(context: Context) : BookmarkStore {
    private val appContext = context.applicationContext

    override fun load(): List<Bookmark> = Utils.loadBookmarks(appContext, true)

    override fun add(id: Int) = Utils.addBookmark(appContext, id)

    override fun remove(id: Int) = Utils.removeBookmark(appContext, id)

    override fun clear() = Utils.saveBookmarks(appContext, arrayListOf())
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
