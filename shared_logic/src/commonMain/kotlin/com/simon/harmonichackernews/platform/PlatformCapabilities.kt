package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Ports for facilities whose implementations belong to an Android, iOS, or other app shell. */
interface CredentialStore {
    fun read(id: String): String?
    fun write(id: String, value: String): Boolean
    fun remove(id: String): Boolean
}

object CredentialIds {
    const val AI_SUMMARY_API_KEY = "ai_summary_api_key"
    const val HACKER_NEWS_USERNAME = "hacker_news_username"
    const val HACKER_NEWS_PASSWORD = "hacker_news_password"
}

interface HistoryStore {
    fun initialize()
    fun load(): List<History>
    fun record(id: Int, createdAtMillis: Long)
    fun remove(id: Int)
    fun clear()
    fun contains(id: Int): Boolean
    val size: Int
    val changeVersion: Long
}

data class ExternalLinkRequest(
    val url: String,
    val preferInApp: Boolean = true,
    val shareable: Boolean = true,
)

/** URL recovery and user-preference decisions shared by every platform link launcher. */
object ExternalLinkPolicy {
    fun applyExternalBrowserPreference(
        request: ExternalLinkRequest,
        externalBrowser: Boolean,
    ): ExternalLinkRequest = if (externalBrowser && request.preferInApp) {
        request.copy(preferInApp = false)
    } else {
        request
    }

    fun openCandidates(value: String): List<String> {
        val original = value.trim()
        if (original.isEmpty()) return emptyList()
        val withScheme = if (
            original.startsWith("http://", ignoreCase = true) ||
            original.startsWith("https://", ignoreCase = true) ||
            "://" in original
        ) {
            original
        } else {
            "http://$original"
        }
        return listOf(original, withScheme).distinct()
    }
}

interface ExternalLinkOpener {
    /** Returns whether a native destination accepted the request. */
    fun open(request: ExternalLinkRequest): Boolean
}

class ConfiguredExternalLinkOpener(
    private val delegate: ExternalLinkOpener,
    private val externalBrowser: () -> Boolean,
) : ExternalLinkOpener {
    override fun open(request: ExternalLinkRequest): Boolean = delegate.open(
        ExternalLinkPolicy.applyExternalBrowserPreference(request, externalBrowser()),
    )
}

interface ShareService {
    fun share(text: String, title: String? = null)
}

interface ClipboardService {
    fun copy(label: String, text: String)
}

interface ConnectivityService {
    fun isOnline(): Boolean
    fun isUnmetered(): Boolean
}

data class SummaryRequest(
    val text: String,
    val prompt: String? = null,
    val model: String? = null,
)

data class SummaryResult(
    val text: String,
    val debugInfo: String? = null,
)

interface LocalSummaryEngine {
    fun canAttempt(): Boolean = true
    suspend fun availability(): LocalSummaryAvailability = LocalSummaryAvailability(
        available = isAvailable(),
        downloadableFallbackRequired = false,
    )
    suspend fun isAvailable(): Boolean
    fun isReady(): Boolean = canAttempt()
    suspend fun summarize(request: SummaryRequest): SummaryResult

    fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> = flow {
        val result = summarize(request)
        result.debugInfo?.takeIf(String::isNotBlank)?.let {
            emit(StorySummaryEvent.DebugInfo(it))
        }
        emit(StorySummaryEvent.Success(result.text))
    }
}
