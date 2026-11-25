package com.simon.harmonichackernews.summary.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.core.net.toUri
import com.simon.harmonichackernews.summary.ExternalModelAccess
import com.simon.harmonichackernews.summary.ExternalModelFile
import com.simon.harmonichackernews.summary.formatDecimalBytes
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSource
import kotlinx.io.buffered

internal class AndroidExternalModelAccess(private val context: Context) : ExternalModelAccess {
    private val resolver get() = context.contentResolver

    override fun inspect(location: String): ExternalModelFile {
        val uri = location.toUri()
        require(uri.scheme == "content") { "Choose a local model from the Android file picker." }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("The file provider did not return a model filename.")
        return descriptor(uri).use { fd ->
            val stat = Os.fstat(fd.fileDescriptor)
            require(OsConstants.S_ISREG(stat.st_mode)) { "Choose a model stored locally on this device." }
            val file = ParcelFileDescriptor.AutoCloseInputStream(fd).asSource().buffered().use { source ->
                ExternalModelFile.inspect(location, name, stat.st_size, source)
            }
            if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
                file.copy(displayPath = DocumentsContract.getDocumentId(uri))
            } else file
        }
    }

    override fun retain(location: String) {
        try {
            resolver.takePersistableUriPermission(location.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            throw IllegalStateException("Could not keep read access to this model. Choose it from local storage in Files.", error)
        }
    }

    override fun release(location: String) {
        try {
            resolver.releasePersistableUriPermission(location.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider or user may already have revoked this grant.
        }
    }

    override suspend fun <T> withPath(file: ExternalModelFile, use: suspend (String) -> T): T =
        descriptor(file.location.toUri()).use { fd ->
            // LiteRT needs a .litertlm path for the lifetime of the engine, including CPU retries.
            val stat = Os.fstat(fd.fileDescriptor)
            val identity = UUID.nameUUIDFromBytes("${file.location}/${stat.st_size}/${stat.st_mtime}".encodeToByteArray())
            val descriptorPath = Paths.get("/proc/self/fd/${fd.fd}")
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                withReadableModelPath(context.cacheDir.toPath(), identity, descriptorPath, stat.st_size, input, use)
            }
        }

    private fun descriptor(uri: Uri) = resolver.openFileDescriptor(uri, "r")
        ?: error("Could not open the model file. Choose it again.")
}

private val linkedModelMutex = Mutex()

internal suspend fun <T> withReadableModelPath(
    cache: Path,
    identity: UUID,
    descriptorPath: Path,
    sizeBytes: Long,
    input: InputStream,
    use: suspend (String) -> T,
): T = linkedModelMutex.withLock {
    val directory = Files.createDirectories(cache.resolve("linked-models"))
    // Recover a temporary model left behind if Android killed the process during inference.
    Files.newDirectoryStream(directory, "*.litertlm").use { entries -> entries.forEach(Files::delete) }
    val model = directory.resolve("$identity.litertlm")
    try {
        try {
            Files.createSymbolicLink(model, descriptorPath)
            Files.newInputStream(model).use { /* Verify that LiteRT can reopen the descriptor by name. */ }
        } catch (_: IOException) {
            Files.deleteIfExists(model)
            check(immediatelyUsableSpaceBytes(directory.toFile()) >= sizeBytes) {
                "Opening this model requires ${formatDecimalBytes(sizeBytes)} of free space for a temporary copy."
            }
            val context = currentCoroutineContext()
            Files.newOutputStream(model).use { output ->
                val buffer = ByteArray(256 * 1024)
                var written = 0L
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    check(written <= sizeBytes) { "The model file grew while it was being opened. Choose it again." }
                    output.write(buffer, 0, count)
                }
                check(written == sizeBytes) { "The model file changed while it was being opened. Choose it again." }
            }
        }
        use(model.toString())
    } finally {
        Files.deleteIfExists(model)
    }
}
