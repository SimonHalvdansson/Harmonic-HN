package com.simon.harmonichackernews.categorization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import okio.ByteString.Companion.encodeUtf8

/** One model revision at a time; callers serialize access with inference. */
interface ArticleEmbeddingCache {
    suspend fun select(signature: String)
    suspend fun get(text: String): FloatArray?
    suspend fun put(text: String, vector: FloatArray)
}

/** Individual atomic files avoid rewriting the app's settings for every generated vector. */
class FileArticleEmbeddingCache(private val root: Path) : ArticleEmbeddingCache {
    private var directory: Path? = null
    override suspend fun select(signature: String) = withContext(Dispatchers.Default) {
        val selected = Path(root, signature.encodeUtf8().sha256().hex())
        SystemFileSystem.createDirectories(selected)
        // Old model spaces cannot be reused. Only delete after the new directory is available.
        SystemFileSystem.list(root).filter { it != selected }.forEach { old ->
            if (SystemFileSystem.metadataOrNull(old)?.isDirectory == true) {
                SystemFileSystem.list(old).forEach { SystemFileSystem.delete(it) }
            }
            SystemFileSystem.delete(old)
        }
        directory = selected
    }
    override suspend fun get(text: String): FloatArray? = withContext(Dispatchers.Default) {
        try {
            val bytes = SystemFileSystem.source(path(text)).buffered().use { it.readByteArray() }
            if (bytes.isEmpty() || bytes.size % 4 != 0 || bytes.size > 65_536) return@withContext null
            FloatArray(bytes.size / 4) { i ->
                var bits = 0
                repeat(4) { bits = bits or ((bytes[i * 4 + it].toInt() and 255) shl (it * 8)) }
                Float.fromBits(bits)
            }
        } catch (_: Exception) { null }
    }
    override suspend fun put(text: String, vector: FloatArray) = withContext(Dispatchers.Default) {
        val target = path(text)
        val temp = Path(checkNotNull(directory), "${target.name}.tmp")
        val bytes = ByteArray(vector.size * 4)
        vector.forEachIndexed { i, value -> repeat(4) { bytes[i * 4 + it] = (value.toBits() ushr (it * 8)).toByte() } }
        try {
            SystemFileSystem.sink(temp).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(temp, target)
        } finally { SystemFileSystem.delete(temp, mustExist = false) }
    }
    private fun path(text: String) = Path(checkNotNull(directory), text.encodeUtf8().sha256().hex())
}

class InMemoryArticleEmbeddingCache : ArticleEmbeddingCache {
    private var signature: String? = null
    private val vectors = mutableMapOf<String, FloatArray>()
    override suspend fun select(signature: String) { if (this.signature != signature) vectors.clear(); this.signature = signature }
    override suspend fun get(text: String) = vectors[text]?.copyOf()
    override suspend fun put(text: String, vector: FloatArray) { vectors[text] = vector.copyOf() }
}
