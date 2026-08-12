package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.simon.harmonichackernews.data.CacheFileInfo
import com.simon.harmonichackernews.data.StoryCacheFileStore
import com.simon.harmonichackernews.data.StoryCacheKeys
import com.simon.harmonichackernews.data.StoryCacheMetadataStore
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

internal class AndroidStoryCacheFileStore(context: Context) : StoryCacheFileStore {
    private val filesRoot = context.applicationContext.filesDir

    override fun read(namespace: String, key: String): ByteArray? = runCatching {
        resolve(namespace, key).takeIf(File::isFile)?.readBytes()
    }.getOrElse { error ->
        Log.e(LOG_TAG, "Failed to read cached bytes for $key", error)
        null
    }

    override fun readText(namespace: String, key: String, charsetName: String): String? =
        runCatching {
            val file = resolve(namespace, key).takeIf(File::isFile) ?: return@runCatching null
            InputStreamReader(FileInputStream(file), charsetName).buffered().use { reader ->
                buildString { reader.forEachLine { line -> append(line).append('\n') } }
            }
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Failed to read cached text for $key", error)
            null
        }

    override fun write(namespace: String, key: String, value: ByteArray): Boolean = runCatching {
        val file = resolve(namespace, key)
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) return@runCatching false
        FileOutputStream(file).use { it.write(value) }
        true
    }.getOrElse { error ->
        Log.e(LOG_TAG, "Failed to write cached data for $key", error)
        false
    }

    override fun remove(namespace: String, key: String): Boolean {
        val file = resolve(namespace, key)
        return !file.exists() || file.delete()
    }

    override fun list(namespace: String): List<CacheFileInfo> = directory(namespace)
        .listFiles()
        ?.mapNotNull { file ->
            file.takeIf(File::isFile)?.let {
                CacheFileInfo(key = it.name, sizeBytes = it.length())
            }
        }
        .orEmpty()

    override fun clear(namespace: String) {
        deleteRecursively(directory(namespace))
    }

    override fun touch(namespace: String, key: String, modifiedAtMillis: Long) {
        resolve(namespace, key).takeIf(File::exists)?.setLastModified(modifiedAtMillis)
    }

    private fun directory(namespace: String): File = when (namespace) {
        StoryCacheKeys.FULL_NAMESPACE -> File(filesRoot, "story_cache/full")
        StoryCacheKeys.SUMMARY_NAMESPACE -> File(filesRoot, "story_cache/summary")
        StoryCacheKeys.ARTICLE_NAMESPACE -> File(filesRoot, "article_cache")
        else -> error("Unknown story-cache namespace: $namespace")
    }

    private fun resolve(namespace: String, key: String): File = File(directory(namespace), key)

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.listFiles()?.forEach(::deleteRecursively)
        if (!file.delete()) file.deleteOnExit()
    }

    private companion object {
        const val LOG_TAG = "HARMONIC_TAG"
    }
}

internal class AndroidStoryCacheMetadataStore(
    private val preferences: SharedPreferences,
) : StoryCacheMetadataStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun getStringSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet())?.toSet().orEmpty()

    override fun putStringSet(key: String, value: Set<String>) {
        preferences.edit().putStringSet(key, value.toSet()).apply()
    }

    override fun keys(): Set<String> = preferences.all.keys
}

internal object AndroidStoryCacheRepositories {
    @Volatile
    private var shared: StoryCacheRepository? = null

    fun get(context: Context): StoryCacheRepository {
        shared?.let { return it }
        return synchronized(this) {
            shared ?: context.applicationContext.let { appContext ->
                StoryCacheRepository(
                    files = AndroidStoryCacheFileStore(appContext),
                    metadata = AndroidStoryCacheMetadataStore(
                        appContext.getSharedPreferences(
                            AppLaunchPreferenceKeys.STORE_NAME,
                            Context.MODE_PRIVATE,
                        ),
                    ),
                )
            }.also { shared = it }
        }
    }
}
