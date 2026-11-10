package com.simon.harmonichackernews.development

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.AtomicFile
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.awaitHarmonicAppComposition
import com.simon.harmonichackernews.settings.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** ADB-only control surface, compiled exclusively into development variants. */
class DevelopmentSettingsProvider : ContentProvider() {
    override fun onCreate() = true // Application.onCreate has not run yet; initialize only in call().

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // Check on the Binder thread before dispatching work or clearing caller identity.
        check(BuildConfig.DEBUG_SETTINGS_ENABLED)
        if (Binder.getCallingUid() != ShellUid) throw SecurityException("ADB shell access only")
        val reply = try {
            runBlocking(Dispatchers.IO) {
                withTimeout(30_000) { commands.withLock { execute(method, arg, extras) } }
            }
        } catch (error: Exception) {
            buildJsonObject {
                put("ok", false)
                put("error", error.message ?: error.javaClass.simpleName)
            }
        }
        return Bundle().apply { putString("json", reply.toString()) }
    }

    private suspend fun execute(method: String, arg: String?, extras: Bundle?): JsonObject {
        val context = requireNotNull(context)
        val app = context.awaitHarmonicAppComposition()
        val settings = AndroidKeyValueStore.defaults(context).also { it.awaitLoaded() }
        val schedule = AndroidKeyValueStore.global(context).also { it.awaitLoaded() }
        val directory = File(context.noBackupFilesDir, "development-settings")
        fun current() = PreferenceSnapshot(
            settings = settings.snapshotValues().filterKeys(DevelopmentSettingsScope::isSetting),
            nighttime = schedule.snapshotValues().filterKeys(DevelopmentSettingsScope::isNighttime),
        )
        fun snapshotFile(): AtomicFile {
            val name = requireNotNull(arg) { "Snapshot name required" }
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))) { "Invalid snapshot name" }
            return AtomicFile(File(directory, "$name.json"))
        }
        val result = buildJsonObject {
            when (method) {
                "snapshot" -> {
                    val file = snapshotFile()
                    require(!file.baseFile.exists()) { "Snapshot already exists; use another name or delete it" }
                    check(directory.isDirectory || directory.mkdirs()) { "Cannot create snapshot directory" }
                    settings.awaitPersisted()
                    schedule.awaitPersisted()
                    val saved = current()
                    val stream = file.startWrite()
                    try {
                        stream.write(saved.encode().toByteArray(Charsets.UTF_8))
                        file.finishWrite(stream)
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                    put("snapshot", arg)
                    put("count", saved.settings.size + saved.nighttime.size)
                }
                "restore" -> {
                    val file = snapshotFile()
                    require(file.baseFile.length() <= 1_048_576) { "Snapshot is too large" }
                    val saved = PreferenceSnapshot.decode(file.openRead().bufferedReader().use { it.readText() })
                    // Decode/validate both scopes before publishing either one.
                    settings.restoreValues(saved.settings, DevelopmentSettingsScope::isSetting)
                    schedule.restoreValues(saved.nighttime, DevelopmentSettingsScope::isNighttime)
                    settings.awaitPersisted()
                    schedule.awaitPersisted()
                    put("snapshot", arg)
                    put("count", saved.settings.size + saved.nighttime.size)
                }
                "delete" -> { snapshotFile().delete(); put("snapshot", arg) }
                "snapshots" -> put("snapshots", JsonArray(directory.listFiles().orEmpty()
                    .filter { it.extension == "json" }.sortedBy { it.name }
                    .map { JsonPrimitive(it.nameWithoutExtension) }))
                "list" -> put("values", Json.parseToJsonElement(current().encode()))
                "get", "set", "reset" -> {
                    val requested = requireNotNull(arg) { "Setting name required" }
                    val alias = readingAliases[requested]
                    val key = alias?.first ?: requested
                    val nighttime = DevelopmentSettingsScope.isNighttime(key)
                    require(nighttime || DevelopmentSettingsScope.isSetting(key)) { "Key is outside settings scope" }
                    if (method != "get") {
                        val value = if (method == "reset") JsonNull else Json.parseToJsonElement(
                            requireNotNull(extras?.getString("value")) { "JSON value required" },
                        )
                        if (alias != null && value != JsonNull) {
                            val boolean = (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                            requireNotNull(boolean) { "This setting requires a boolean" }
                            app.settings.setReadingBoolean(alias.second, boolean)
                        } else {
                            require(SettingsTransfer(settings, schedule).setValue(key, value, nighttime)) {
                                "Unsupported setting or invalid value; use a key from the portable settings schema"
                            }
                        }
                        settings.awaitPersisted()
                        schedule.awaitPersisted()
                    }
                    val stored = (if (nighttime) schedule else settings).snapshotValues()[key]
                    put("key", key)
                    put("stored", stored != null)
                    put("type", stored?.type?.let(::JsonPrimitive) ?: JsonNull)
                    put("value", stored?.value ?: JsonNull)
                }
                else -> error("Unknown command: $method")
            }
            put("ok", true)
        }
        return result
    }

    // This endpoint has no table/file interface; all supported operations go through call().
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    private companion object {
        const val ShellUid = 2000
        val commands = Mutex()
        val readingAliases = mapOf(
            "integrated-browser" to (UserPreferenceKeys.WEBVIEW to ReadingBooleanPreference.INTEGRATED_WEB_VIEW),
            "external-browser" to (UserPreferenceKeys.EXTERNAL_BROWSER to ReadingBooleanPreference.EXTERNAL_BROWSER),
            "pull-to-open-browser" to (UserPreferenceKeys.PULL_TO_OPEN_BROWSER to ReadingBooleanPreference.PULL_TO_OPEN_BROWSER),
        )
    }
}
