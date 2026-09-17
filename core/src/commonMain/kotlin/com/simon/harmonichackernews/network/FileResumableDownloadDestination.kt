package com.simon.harmonichackernews.network

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

/** Shared filesystem destination for resumable model and other large-file downloads. */
class FileResumableDownloadDestination(
    private val completed: Path,
    private val partial: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ResumableDownloadDestination {
    private val fileMutex = Mutex()

    override suspend fun prepare(): Boolean = fileOperation {
        runCatching {
            completed.parent?.let(fileSystem::createDirectories)
            partial.parent?.let(fileSystem::createDirectories)
            partial.parent?.let { fileSystem.metadataOrNull(it)?.isDirectory == true } == true
        }.getOrDefault(false)
    }

    override suspend fun completedBytes(): Long = fileOperation {
        fileSystem.metadataOrNull(completed)?.takeIf { it.isRegularFile }?.size ?: 0L
    }

    override suspend fun partialBytes(): Long = fileOperation {
        fileSystem.metadataOrNull(partial)?.takeIf { it.isRegularFile }?.size ?: 0L
    }

    override suspend fun removeCompleted(): Boolean = fileOperation { delete(completed) }

    override suspend fun removePartial(): Boolean = fileOperation { delete(partial) }

    override suspend fun openPartial(append: Boolean): DownloadSink = fileOperation {
        FileResumableDownloadSink(append)
    }

    override suspend fun promotePartial(): Boolean = fileOperation {
        runCatching {
            fileSystem.delete(completed, mustExist = false)
            try {
                fileSystem.atomicMove(partial, completed)
            } catch (_: UnsupportedOperationException) {
                fileSystem.source(partial).buffered().use { input ->
                    fileSystem.sink(completed).buffered().use { output ->
                        input.transferTo(output)
                        output.flush()
                    }
                }
                fileSystem.delete(partial)
            }
            true
        }.getOrDefault(false)
    }

    private fun delete(path: Path): Boolean = runCatching {
        fileSystem.delete(path, mustExist = false)
        true
    }.getOrDefault(false)

    private suspend fun <T> fileOperation(block: () -> T): T = withContext(ioDispatcher) {
        fileMutex.withLock { block() }
    }

    /** Closing on abort deliberately preserves the resumable partial file. */
    private inner class FileResumableDownloadSink(
        append: Boolean,
    ) : DownloadSink {
        private var sink: Sink? = fileSystem.sink(partial, append = append).buffered()
        override val reference: String = partial.toString()

        override suspend fun <T> writeSession(block: suspend () -> T): T =
            withContext(ioDispatcher) { block() }

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
            fileMutex.withLock {
                checkNotNull(sink) { "Download sink is closed" }
                    .write(buffer, offset, offset + length)
            }
        }

        override suspend fun close() = fileOperation {
            sink?.flush()
            sink?.close()
            sink = null
        }

        override suspend fun abort() = fileOperation {
            runCatching { sink?.flush() }
            runCatching { sink?.close() }
            sink = null
        }
    }
}
