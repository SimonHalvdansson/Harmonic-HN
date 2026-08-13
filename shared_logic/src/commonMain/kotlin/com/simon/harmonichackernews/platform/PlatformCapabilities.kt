package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.Bookmark
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

interface BookmarkStore {
    fun load(): List<Bookmark>
    fun add(id: Int)
    fun remove(id: Int)
    fun clear()
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

interface CacheStore {
    suspend fun read(namespace: String, key: String): ByteArray?
    suspend fun write(namespace: String, key: String, value: ByteArray)
    suspend fun remove(namespace: String, key: String)
    suspend fun clear(namespace: String)
}

interface FileStore {
    suspend fun read(reference: String): ByteArray?
    suspend fun write(reference: String, value: ByteArray)
    suspend fun remove(reference: String): Boolean
}

data class ExternalLinkRequest(
    val url: String,
    val preferInApp: Boolean = true,
    val shareable: Boolean = true,
)

interface ExternalLinkOpener {
    fun open(request: ExternalLinkRequest)
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

data class NotificationRequest(
    val id: String,
    val title: String,
    val body: String,
    val deepLink: String? = null,
)

interface NotificationScheduler {
    suspend fun schedule(request: NotificationRequest)
    suspend fun cancel(id: String)
}

data class ArticleRequest(
    val url: String,
    val readerMode: Boolean = false,
)

interface ArticleViewer {
    fun open(request: ArticleRequest)
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
