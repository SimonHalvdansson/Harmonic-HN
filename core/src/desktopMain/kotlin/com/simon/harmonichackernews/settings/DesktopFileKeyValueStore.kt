package com.simon.harmonichackernews.settings

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Small atomic properties-backed store for desktop hosts.
 *
 * Each instance owns one file and publishes the same change signal as Android preferences. Writes
 * replace the file atomically where the host filesystem supports it, so an interrupted desktop
 * process cannot leave a partially written settings file behind.
 */
class DesktopFileKeyValueStore(
    private val file: Path,
) : KeyValueStore {
    private val lock = Any()
    private val values = Properties()
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    private var persistenceFailureReported = false

    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    init {
        synchronized(lock) {
            try {
                if (Files.isRegularFile(file)) {
                    Files.newInputStream(file).use(values::load)
                }
            } catch (error: Exception) {
                values.clear()
                reportPersistenceFailure("load", error)
            }
        }
    }

    override fun clear() = mutate { it.clear() }

    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun keys(): Set<String> = synchronized(lock) {
        values.stringPropertyNames().toSet()
    }

    override fun remove(key: String) = mutate { it.remove(key) }

    override fun getString(key: String, default: String?): String? = synchronized(lock) {
        values.getProperty(key) ?: default
    }

    override fun putString(key: String, value: String?) = putNullable(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        values.getProperty(key)?.toBooleanStrictOrNull() ?: default
    }

    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    override fun getInt(key: String, default: Int): Int = synchronized(lock) {
        values.getProperty(key)?.toIntOrNull() ?: default
    }

    override fun putInt(key: String, value: Int) = put(key, value.toString())

    override fun getLong(key: String, default: Long): Long = synchronized(lock) {
        values.getProperty(key)?.toLongOrNull() ?: default
    }

    override fun putLong(key: String, value: Long) = put(key, value.toString())

    override fun getFloat(key: String, default: Float): Float = synchronized(lock) {
        values.getProperty(key)?.toFloatOrNull() ?: default
    }

    override fun putFloat(key: String, value: Float) = put(key, value.toString())

    override fun getStringSet(key: String): Set<String> = synchronized(lock) {
        values.getProperty(key)
            ?.takeIf(String::isNotEmpty)
            ?.split(',')
            ?.mapNotNull { encoded ->
                runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }
                    .getOrNull()
            }
            ?.toSet()
            .orEmpty()
    }

    override fun putStringSet(key: String, value: Set<String>?) = putNullable(
        key,
        value?.let(::encodeStringSet),
    )

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        mutate { staged ->
            block(object : KeyValueStore.Editor {
                override fun remove(key: String) {
                    staged.remove(key)
                }

                override fun putString(key: String, value: String?) {
                    if (value == null) staged.remove(key) else staged.setProperty(key, value)
                }

                override fun putBoolean(key: String, value: Boolean) {
                    staged.setProperty(key, value.toString())
                }

                override fun putInt(key: String, value: Int) {
                    staged.setProperty(key, value.toString())
                }

                override fun putLong(key: String, value: Long) {
                    staged.setProperty(key, value.toString())
                }

                override fun putFloat(key: String, value: Float) {
                    staged.setProperty(key, value.toString())
                }

                override fun putStringSet(key: String, value: Set<String>?) {
                    if (value == null) staged.remove(key) else staged.setProperty(key, encodeStringSet(value))
                }
            })
        }
    }

    private fun putNullable(key: String, value: String?) = mutate { staged ->
        if (value == null) staged.remove(key) else staged.setProperty(key, value)
    }

    private fun put(key: String, value: String) = mutate { staged -> staged.setProperty(key, value) }

    private fun mutate(block: (Properties) -> Unit) {
        synchronized(lock) {
            val staged = Properties().apply { putAll(this@DesktopFileKeyValueStore.values) }
            block(staged)
            persistSafely(staged)
            values.clear()
            values.putAll(staged)
        }
        mutableChanges.tryEmit(Unit)
    }

    private fun persist(staged: Properties) {
        file.parent?.let(Files::createDirectories)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        var moved = false
        try {
            Files.newOutputStream(temporary).use { output ->
                staged.store(output, "Harmonic desktop settings")
            }
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
            moved = true
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temporary)
                } catch (_: Exception) {
                    // Preserve the original persistence failure.
                }
            }
        }
    }

    private fun persistSafely(staged: Properties) {
        try {
            persist(staged)
            persistenceFailureReported = false
        } catch (error: Exception) {
            reportPersistenceFailure("save", error)
        }
    }

    private fun reportPersistenceFailure(operation: String, error: Exception) {
        if (persistenceFailureReported) return
        persistenceFailureReported = true
        System.err.println("Harmonic could not $operation desktop settings at $file; using session values.")
        error.printStackTrace(System.err)
    }

    private fun encodeStringSet(value: Set<String>): String =
        value.sorted().joinToString(",") { item ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(item.toByteArray())
        }
}
