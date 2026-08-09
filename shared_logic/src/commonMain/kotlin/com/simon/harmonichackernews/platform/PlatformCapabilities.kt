package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History

/** Ports for facilities whose implementations belong to an Android, iOS, or other app shell. */
interface CredentialStore {
    fun read(id: String): String?
    fun write(id: String, value: String): Boolean
    fun remove(id: String): Boolean
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
    suspend fun isAvailable(): Boolean
    suspend fun summarize(request: SummaryRequest): SummaryResult
}
