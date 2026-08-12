package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.network.DownloadMetadata
import com.simon.harmonichackernews.network.DownloadSink
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.network.StoredDownload
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidDownloadStore(
    private val root: File?,
    private val fileNameForKey: (String) -> String,
    private val targetSuffix: String,
    private val temporarySuffix: String = ".download",
    private val onCommit: (key: String, metadata: DownloadMetadata) -> Unit = { _, _ -> },
    private val onRemove: (File) -> Unit = {},
) : DownloadStore {
    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        val directory = root ?: return@withContext false
        (directory.isDirectory || directory.mkdirs()) && directory.isDirectory
    }

    override suspend fun find(key: String): StoredDownload? = withContext(Dispatchers.IO) {
        target(key).takeIf { it.isFile && it.length() > 0L }?.toStoredDownload(temporary = false)
    }

    override suspend fun createTemporary(key: String): DownloadSink = withContext(Dispatchers.IO) {
        val directory = checkNotNull(root) { "Download cache is unavailable" }
        val prefix = fileNameForKey(key).substringBeforeLast('.').padEnd(3, '_')
        val file = File.createTempFile("$prefix-", temporarySuffix, directory)
        AndroidDownloadSink(file)
    }

    override suspend fun commit(
        temporaryReference: String,
        key: String,
        metadata: DownloadMetadata,
        nowMillis: Long,
    ): StoredDownload = withContext(Dispatchers.IO) {
        val temporary = File(temporaryReference)
        val target = target(key)
        moveReplacing(temporary, target)
        target.setLastModified(nowMillis)
        onCommit(key, metadata)
        target.toStoredDownload(temporary = false)
    }

    override suspend fun list(): List<StoredDownload> = withContext(Dispatchers.IO) {
        root?.listFiles()?.mapNotNull { file ->
            when {
                !file.isFile -> null
                file.name.endsWith(temporarySuffix) -> file.toStoredDownload(temporary = true)
                file.name.endsWith(targetSuffix) -> file.toStoredDownload(temporary = false)
                else -> null
            }
        }.orEmpty()
    }

    override suspend fun touch(reference: String, nowMillis: Long) = withContext(Dispatchers.IO) {
        File(reference).setLastModified(nowMillis)
        Unit
    }

    override suspend fun remove(reference: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(reference)
        val removed = !file.exists() || file.delete()
        if (removed) onRemove(file)
        removed
    }

    private fun target(key: String): File = File(checkNotNull(root), fileNameForKey(key))

    private fun File.toStoredDownload(temporary: Boolean): StoredDownload = StoredDownload(
        reference = absolutePath,
        sizeBytes = length(),
        lastModifiedMillis = lastModified(),
        temporary = temporary,
    )

    private class AndroidDownloadSink(private val file: File) : DownloadSink {
        private val output = FileOutputStream(file)
        override val reference: String get() = file.absolutePath

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) =
            withContext(Dispatchers.IO) {
                output.write(buffer, offset, length)
            }

        override suspend fun close() = withContext(Dispatchers.IO) {
            output.fd.sync()
            output.close()
        }

        override suspend fun abort() = withContext(Dispatchers.IO) {
            runCatching { output.close() }
            file.delete()
            Unit
        }
    }

    private companion object {
        fun moveReplacing(source: File, target: File) {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }
}
