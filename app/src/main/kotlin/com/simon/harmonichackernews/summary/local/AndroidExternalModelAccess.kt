package com.simon.harmonichackernews.summary.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.core.net.toUri
import com.simon.harmonichackernews.summary.ExternalModelAccess
import com.simon.harmonichackernews.summary.ExternalModelFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlinx.io.files.Path

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
            val file = ExternalModelFile.inspect(location, name, Path("/proc/self/fd/${fd.fd}"), stat.st_size)
            if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
                file.copy(displayPath = DocumentsContract.getDocumentId(uri))
            } else file
        }
    }

    override fun retain(location: String) =
        resolver.takePersistableUriPermission(location.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun release(location: String) {
        try {
            resolver.releasePersistableUriPermission(location.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider or user may already have revoked this grant.
        }
    }

    override suspend fun <T> withPath(file: ExternalModelFile, use: suspend (String) -> T): T =
        descriptor(file.location.toUri()).use { fd ->
            // LiteRT opens a filename with a .litertlm suffix. Keep both the link and its
            // descriptor alive until the engine has closed, including GPU-to-CPU retries.
            val stat = Os.fstat(fd.fileDescriptor)
            val identity = UUID.nameUUIDFromBytes("${file.location}/${stat.st_size}/${stat.st_mtime}".encodeToByteArray())
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "linked-model-")
            val link = directory.resolve("$identity.litertlm")
            try {
                Files.createSymbolicLink(link, Paths.get("/proc/self/fd/${fd.fd}"))
                use(link.toString())
            } finally {
                Files.deleteIfExists(link)
                Files.deleteIfExists(directory)
            }
        }

    private fun descriptor(uri: Uri) = resolver.openFileDescriptor(uri, "r")
        ?: error("Could not open the model file. Choose it again.")
}
