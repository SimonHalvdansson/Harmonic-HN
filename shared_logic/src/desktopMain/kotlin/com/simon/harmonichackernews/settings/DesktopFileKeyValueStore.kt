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

    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    init {
        synchronized(lock) {
            if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(values::load)
            }
        }
    }

    override fun clear() = mutate { clear() }

    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun keys(): Set<String> = synchronized(lock) {
        values.stringPropertyNames().toSet()
    }

    override fun remove(key: String) = mutate { remove(key) }

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
        value?.sorted()?.joinToString(",") { item ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(item.toByteArray())
        },
    )

    private fun putNullable(key: String, value: String?) = mutate {
        if (value == null) remove(key) else setProperty(key, value)
    }

    private fun put(key: String, value: String) = mutate { setProperty(key, value) }

    private fun mutate(block: Properties.() -> Unit) {
        synchronized(lock) {
            values.block()
            persist()
        }
        mutableChanges.tryEmit(Unit)
    }

    private fun persist() {
        file.parent?.let(Files::createDirectories)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { output ->
            values.store(output, "Harmonic desktop settings")
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
    }
}
