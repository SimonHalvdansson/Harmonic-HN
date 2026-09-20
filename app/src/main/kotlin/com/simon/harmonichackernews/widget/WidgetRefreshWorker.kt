package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.*
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.*
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.WidgetRefreshResult
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.utils.HarmonicLog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object WidgetState {
    val content = stringPreferencesKey("stories")
    val error = stringPreferencesKey("error")
    val updated = longPreferencesKey("updated")
    val refreshing = booleanPreferencesKey("refreshing")
    val feed = stringPreferencesKey("feed")
    val revision = longPreferencesKey("revision")
    val width = intPreferencesKey("width")
    val debug = stringPreferencesKey("debug")
}

internal data class WidgetEntry(val destination: StoryDestination, val imagePath: String?, val tintPath: String?, val faviconPath: String?)

internal fun decodeWidgetEntries(value: String?): List<WidgetEntry> = try {
    val array = JSONArray(value ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val entry = array.getJSONObject(index)
        val destination = AppDestinationCodec.decode(entry.getString("destination")) as? StoryDestination
        destination?.let { WidgetEntry(it, entry.optString("image").takeIf(String::isNotEmpty), entry.optString("tint").takeIf(String::isNotEmpty), entry.optString("favicon").takeIf(String::isNotEmpty)) }
    }
} catch (_: Exception) { emptyList() }

/** Network work outlives receivers and configuration activities; a failed refresh retains content. */
class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(WIDGET_ID, -1)
        if (widgetId < 0) return Result.failure()
        val context = applicationContext
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) == null) return Result.success()
        val app = context.harmonicAppComposition
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        if (inputData.getBoolean(RESIZE_ONLY, false)) {
            val width = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            updateAppWidgetState(context, glanceId) { it[WidgetState.width] = width }
            StoriesGlanceWidget().update(context, glanceId)
            return Result.success()
        }
        val configuration = app.widgets.configuration(widgetId)
        try {
            updateAppWidgetState(context, glanceId) { state ->
                // Never label a previous feed's stories as the newly selected feed.
                if (state[WidgetState.feed] != configuration.feedUrl) {
                    state.remove(WidgetState.content)
                    state.remove(WidgetState.updated)
                    state.remove(WidgetState.debug)
                }
                state[WidgetState.feed] = configuration.feedUrl
                state[WidgetState.refreshing] = true
                state.remove(WidgetState.error)
            }
            StoriesGlanceWidget().update(context, glanceId)
            app.widgets.setSkipFetch(widgetId, false)
            when (val result = app.widgetRefresh.refresh(widgetId, hasExistingStories = false)) {
                is WidgetRefreshResult.Loaded -> {
                    updateAppWidgetState(context, glanceId) {
                        it[WidgetState.debug] = "${result.stories.size}/${result.availableStoryCount} items · ${result.failedStoryCount} failed · timeout=${result.timedOut}"
                    }
                    val entries = result.stories.map { story ->
                        JSONObject().put("destination", AppDestinationCodec.encode(story.toDestination(showWebsite = story.isLink && !app.userSettings.story.alwaysOpenComments))).apply {
                            val image = File(imageDirectory(context, widgetId), "${story.id}-preview.png")
                            val favicon = File(imageDirectory(context, widgetId), "${story.id}-favicon.png")
                            if (image.exists()) put("image", image.absolutePath).put("tint", image.absolutePath)
                            if (favicon.exists()) {
                                put("favicon", favicon.absolutePath)
                                if (!image.exists()) put("tint", favicon.absolutePath)
                            }
                        }
                    }
                    // Publish text immediately; optional images must never turn a successful feed into an error.
                    publish(widgetId, entries, updated = System.currentTimeMillis())
                    if (configuration.previewImageMode != StoryPreviewMode.OFF || configuration.tint || app.userSettings.story.thumbnails) {
                        withTimeoutOrNull(25_000) {
                            result.stories.zip(entries).chunked(4).forEach { batch ->
                                coroutineScope {
                                    batch.map { (story, entry) -> async {
                                        withTimeoutOrNull(6_000) {
                                            try {
                                                val image = if (configuration.previewImageMode != StoryPreviewMode.OFF || configuration.tint) {
                                                    story.url?.takeIf { story.isLink }?.let { url ->
                                                        withTimeoutOrNull(3_000) { app.previewResources.load(story.id, url, requireSummary = false).imageUrl }
                                                    }?.let { loadImage(widgetId, story.id, it, "preview") }
                                                } else null
                                                if (image != null) {
                                                    entry.put("image", image)
                                                    entry.put("tint", image)
                                                }
                                                if ((configuration.tint || app.userSettings.story.thumbnails) && story.isLink) {
                                                    val favicon = FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), app.userSettings.story.faviconProvider)
                                                    loadImage(widgetId, story.id, favicon, "favicon")?.let {
                                                        entry.put("favicon", it)
                                                        if (image == null) entry.put("tint", it)
                                                    }
                                                }
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                HarmonicLog.debug("Widget optional image failed item=${story.id}: $error")
                                            }
                                        }
                                    } }.awaitAll()
                                }
                            }
                        }
                        publish(widgetId, entries)
                    }
                    val retained = entries.flatMap { entry -> listOf("image", "tint", "favicon").map { entry.optString(it) } }.toSet()
                    imageDirectory(context, widgetId).listFiles()?.filterNot { it.absolutePath in retained }?.forEach { it.delete() }
                    return Result.success()
                }
                is WidgetRefreshResult.Failed -> {
                    val cause = result.cause
                    HarmonicLog.debug("Widget refresh failed widgetId=$widgetId feed=${configuration.storyType}: $cause")
                    updateAppWidgetState(context, glanceId) { state ->
                        state[WidgetState.error] = "${configuration.storyType.label}: ${widgetFailureDescription(cause)}"
                        state[WidgetState.refreshing] = false
                    }
                    StoriesGlanceWidget().update(context, glanceId)
                    return Result.success()
                }
                WidgetRefreshResult.UseExisting -> return Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            HarmonicLog.debug("Widget refresh failed widgetId=$widgetId: $error")
            updateAppWidgetState(context, glanceId) {
                it[WidgetState.error] = "${configuration.storyType.label}: ${widgetFailureDescription(error)}"
            }
            return Result.failure()
        } finally {
            // Clear durable loading state even when WorkManager cancels/replaces a run.
            withContext(NonCancellable) {
                if (AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) != null) {
                    updateAppWidgetState(context, glanceId) { it[WidgetState.refreshing] = false }
                    StoriesGlanceWidget().update(context, glanceId)
                }
            }
        }
    }

    private suspend fun publish(widgetId: Int, entries: List<JSONObject>, updated: Long? = null) {
        updateAppWidgetState(applicationContext, GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId)) { state ->
            state[WidgetState.content] = JSONArray(entries).toString()
            updated?.let { state[WidgetState.updated] = it }
            state[WidgetState.revision] = System.currentTimeMillis()
            state[WidgetState.refreshing] = false
            state.remove(WidgetState.error)
        }
        StoriesGlanceWidget().update(applicationContext, GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId))
    }

    private suspend fun loadImage(widgetId: Int, storyId: Int, url: String, kind: String): String? {
        val result = SingletonImageLoader.get(applicationContext).execute(
            ImageRequest.Builder(applicationContext).data(url).size(160, 160).allowHardware(false).build(),
        ) as? SuccessResult ?: return null
        val decoded = result.image.toBitmap()
        val scale = minOf(1f, 160f / maxOf(decoded.width, decoded.height))
        val bitmap = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        val file = File(imageDirectory(applicationContext, widgetId), "$storyId-$kind.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    companion object {
        private const val WIDGET_ID = "widgetId"
        private const val RESIZE_ONLY = "resizeOnly"
        fun workName(widgetId: Int) = "stories-widget-$widgetId"
        fun resizeWorkName(widgetId: Int) = "stories-widget-resize-$widgetId"
        fun enqueueResize(context: Context, widgetId: Int) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                resizeWorkName(widgetId), ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setInputData(workDataOf(WIDGET_ID to widgetId, RESIZE_ONLY to true)).build(),
            )
        }
        fun enqueue(context: Context, widgetId: Int) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(widgetId), ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setInputData(workDataOf(WIDGET_ID to widgetId)).build(),
            )
        }
        internal fun imageDirectory(context: Context, widgetId: Int) = File(context.cacheDir, "widget-images/$widgetId")
    }
}

