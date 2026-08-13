package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.FileAccessTimeStore
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Multiplatform filesystem implementation used for article snapshots and cached downloads.
 *
 * Hosts choose the root directory and persistent access-time store; streaming, temporary files,
 * atomic replacement and cleanup are shared across Android, iOS and desktop.
 */
class FileDownloadStore(
    private val root: Path?,
    private val fileNameForKey: (String) -> String,
    private val targetSuffix: String,
    private val accessTimes: FileAccessTimeStore,
    private val temporarySuffix: String = ".download",
    private val nowMillis: () -> Long,
    private val onCommit: (key: String, metadata: DownloadMetadata) -> Unit = { _, _ -> },
    private val onRemove: (reference: String) -> Unit = {},
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DownloadStore {
    private val fileMutex = Mutex()

    override suspend fun prepare(): Boolean = fileOperation {
        val directory = root ?: return@fileOperation false
        runCatching {
            fileSystem.createDirectories(directory)
            fileSystem.metadataOrNull(directory)?.isDirectory == true
        }.getOrDefault(false)
    }

    override suspend fun find(key: String): StoredDownload? = fileOperation {
        val path = target(key)
        val metadata = fileSystem.metadataOrNull(path)
        if (metadata?.isRegularFile == true && metadata.size > 0L) {
            path.toStoredDownload(temporary = false, sizeBytes = metadata.size)
        } else {
            null
        }
    }

    override suspend fun createTemporary(key: String): DownloadSink = fileOperation {
        val directory = checkNotNull(root) { "Download cache is unavailable" }
        fileSystem.createDirectories(directory)
        val prefix = fileNameForKey(key).substringBeforeLast('.').padEnd(3, '_')
        var path: Path
        do {
            val token = Random.nextLong().toULong().toString(16)
            path = Path(directory, "$prefix-$token$temporarySuffix")
        } while (fileSystem.exists(path))
        FileDownloadSink(path)
    }

    override suspend fun commit(
        temporaryReference: String,
        key: String,
        metadata: DownloadMetadata,
        nowMillis: Long,
    ): StoredDownload = fileOperation {
        val temporary = Path(temporaryReference)
        val target = target(key)
        moveReplacing(temporary, target)
        accessTimes.remove(temporaryReference)
        accessTimes.touch(target.toString(), nowMillis)
        onCommit(key, metadata)
        val size = fileSystem.metadataOrNull(target)?.size ?: 0L
        target.toStoredDownload(temporary = false, sizeBytes = size)
    }

    override suspend fun list(): List<StoredDownload> = fileOperation {
        val directory = root ?: return@fileOperation emptyList()
        if (fileSystem.metadataOrNull(directory)?.isDirectory != true) {
            return@fileOperation emptyList()
        }
        fileSystem.list(directory).mapNotNull { path ->
            val metadata = fileSystem.metadataOrNull(path)
            when {
                metadata?.isRegularFile != true -> null
                path.name.endsWith(temporarySuffix) ->
                    path.toStoredDownload(temporary = true, sizeBytes = metadata.size)
                path.name.endsWith(targetSuffix) ->
                    path.toStoredDownload(temporary = false, sizeBytes = metadata.size)
                else -> null
            }
        }
    }

    override suspend fun touch(reference: String, nowMillis: Long) = fileOperation {
        if (fileSystem.exists(Path(reference))) accessTimes.touch(reference, nowMillis)
    }

    override suspend fun remove(reference: String): Boolean = fileOperation {
        val path = Path(reference)
        val removed = runCatching {
            fileSystem.delete(path, mustExist = false)
            true
        }.getOrDefault(false)
        if (removed) {
            accessTimes.remove(reference)
            onRemove(reference)
        }
        removed
    }

    private fun target(key: String): Path = Path(checkNotNull(root), fileNameForKey(key))

    private fun Path.toStoredDownload(temporary: Boolean, sizeBytes: Long): StoredDownload {
        val reference = toString()
        return StoredDownload(
            reference = reference,
            sizeBytes = sizeBytes,
            lastModifiedMillis = accessTimes.readOrInitialize(reference, nowMillis()),
            temporary = temporary,
        )
    }

    private fun moveReplacing(source: Path, destination: Path) {
        try {
            fileSystem.atomicMove(source, destination)
        } catch (_: UnsupportedOperationException) {
            fileSystem.source(source).buffered().use { input ->
                fileSystem.sink(destination).buffered().use { output ->
                    input.transferTo(output)
                    output.flush()
                }
            }
            fileSystem.delete(source)
        }
    }

    private suspend fun <T> fileOperation(block: () -> T): T = withContext(ioDispatcher) {
        fileMutex.withLock { block() }
    }

    private inner class FileDownloadSink(
        private val path: Path,
    ) : DownloadSink {
        private var sink: Sink? = null
        private var closed = false
        override val reference: String = path.toString()

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) = fileOperation {
            check(!closed) { "Download sink is closed" }
            val output = sink ?: fileSystem.sink(path).buffered().also { sink = it }
            output.write(buffer, offset, offset + length)
        }

        override suspend fun close() = fileOperation {
            if (!closed) {
                sink?.flush()
                sink?.close()
                sink = null
                closed = true
                accessTimes.touch(reference, nowMillis())
            }
        }

        override suspend fun abort() = fileOperation {
            if (!closed) {
                runCatching { sink?.close() }
                sink = null
                closed = true
            }
            fileSystem.delete(path, mustExist = false)
            accessTimes.remove(reference)
        }
    }
}
