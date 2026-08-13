package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.settings.KeyValueStore
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.charsets.forName
import io.ktor.utils.io.charsets.isSupported
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Multiplatform filesystem storage for story payloads, summaries and article snapshots. */
class FileStoryCacheStore(
    private val root: Path,
    private val accessTimes: FileAccessTimeStore,
    private val fileSystem: FileSystem = SystemFileSystem,
) : StoryCacheFileStore {
    override fun read(namespace: String, key: String): ByteArray? = runCatching {
        val path = resolve(namespace, key)
        if (fileSystem.metadataOrNull(path)?.isRegularFile != true) return@runCatching null
        fileSystem.source(path).buffered().use { it.readByteArray() }
    }.getOrNull()

    override fun readText(namespace: String, key: String, charsetName: String): String? =
        runCatching {
            val path = resolve(namespace, key)
            if (fileSystem.metadataOrNull(path)?.isRegularFile != true) return@runCatching null
            val charset = if (Charsets.isSupported(charsetName)) {
                Charsets.forName(charsetName)
            } else {
                Charsets.UTF_8
            }
            fileSystem.source(path).buffered().use { source ->
                charset.newDecoder().decode(source)
            }
        }.getOrNull()

    override fun write(namespace: String, key: String, value: ByteArray): Boolean = runCatching {
        val directory = directory(namespace)
        fileSystem.createDirectories(directory)
        val path = Path(directory, key)
        fileSystem.sink(path).buffered().use { sink ->
            sink.write(value)
            sink.flush()
        }
        true
    }.getOrDefault(false)

    override fun remove(namespace: String, key: String): Boolean = runCatching {
        val path = resolve(namespace, key)
        fileSystem.delete(path, mustExist = false)
        accessTimes.remove(path.toString())
        true
    }.getOrDefault(false)

    override fun list(namespace: String): List<CacheFileInfo> = runCatching {
        val directory = directory(namespace)
        if (fileSystem.metadataOrNull(directory)?.isDirectory != true) {
            return@runCatching emptyList()
        }
        fileSystem.list(directory).mapNotNull { path ->
            fileSystem.metadataOrNull(path)
                ?.takeIf { it.isRegularFile }
                ?.let { CacheFileInfo(key = path.name, sizeBytes = it.size) }
        }
    }.getOrDefault(emptyList())

    override fun clear(namespace: String) {
        val directory = directory(namespace)
        runCatching { deleteTree(directory) }
        accessTimes.removeTree(directory.toString())
    }

    override fun touch(namespace: String, key: String, modifiedAtMillis: Long) {
        val path = resolve(namespace, key)
        if (fileSystem.exists(path)) accessTimes.touch(path.toString(), modifiedAtMillis)
    }

    private fun directory(namespace: String): Path = when (namespace) {
        StoryCacheKeys.FULL_NAMESPACE,
        StoryCacheKeys.SUMMARY_NAMESPACE,
        StoryCacheKeys.ARTICLE_NAMESPACE -> Path(root, namespace)
        else -> error("Unknown story-cache namespace: $namespace")
    }

    private fun resolve(namespace: String, key: String): Path = Path(directory(namespace), key)

    private fun deleteTree(path: Path) {
        val metadata = fileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) fileSystem.list(path).forEach(::deleteTree)
        fileSystem.delete(path, mustExist = false)
    }
}

/** Reuses a host key-value store for the cache metadata schema shared by all platforms. */
class KeyValueStoryCacheMetadataStore(
    private val store: KeyValueStore,
) : StoryCacheMetadataStore {
    override fun getString(key: String): String? = store.getString(key)

    override fun putString(key: String, value: String?) {
        store.putString(key, value)
    }

    override fun remove(key: String) {
        store.remove(key)
    }

    override fun getStringSet(key: String): Set<String> = store.getStringSet(key)

    override fun putStringSet(key: String, value: Set<String>) {
        store.putStringSet(key, value)
    }

    override fun keys(): Set<String> = store.keys()
}
