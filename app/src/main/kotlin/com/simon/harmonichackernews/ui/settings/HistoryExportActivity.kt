package com.simon.harmonichackernews.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.platform.PresentationCopy
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the picker result even when Android recreates the app before restoring Settings. */
class HistoryExportActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        finishExport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            try {
                picker.launch(requireNotNull(intent.getStringExtra(FILENAME)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "No system file picker is available.", Toast.LENGTH_LONG).show()
                finishExport(null)
            }
        }
    }

    private fun finishExport(uri: Uri?) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destination = uri?.let { target ->
                        { checkNotNull(contentResolver.openOutputStream(target, "wt")) }
                    }
                    HistoryExportFiles(cacheDir).complete(intent.getStringExtra(SNAPSHOT), destination)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Toast.makeText(this@HistoryExportActivity, PresentationCopy.WRITE_ERROR, Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    companion object {
        private const val SNAPSHOT = "snapshot"
        private const val FILENAME = "filename"

        suspend fun start(context: Context, filename: String, content: String) {
            val files = HistoryExportFiles(context.cacheDir)
            var staged: String? = null
            try {
                withContext(Dispatchers.IO) { staged = files.stage(content) }
                context.startActivity(Intent(context, HistoryExportActivity::class.java)
                    .putExtra(SNAPSHOT, staged)
                    .putExtra(FILENAME, filename))
            } catch (error: Exception) {
                files.complete(staged, null)
                throw error
            }
        }
    }
}

internal class HistoryExportFiles(private val cacheDir: File) {
    fun stage(content: String): String {
        val file = File.createTempFile("history-", ".csv", cacheDir)
        try {
            file.writeText(content)
            return file.name
        } catch (error: IOException) {
            file.delete()
            throw error
        }
    }

    /** The picker may return to a new process. Only the staged filename goes in saved state. */
    fun complete(name: String?, openDestination: (() -> OutputStream)?) {
        val file = name?.let { File(cacheDir, it) }
        try {
            if (openDestination != null) {
                checkNotNull(file).inputStream().use { input ->
                    openDestination().use { output -> input.copyTo(output) }
                }
            }
        } finally {
            file?.delete()
        }
    }
}
