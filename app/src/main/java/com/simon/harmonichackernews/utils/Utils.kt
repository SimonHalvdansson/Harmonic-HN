package com.simon.harmonichackernews.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.data.ArticleSnapshotPolicy
import com.simon.harmonichackernews.data.CacheFileNamePolicy
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.LocalSummaryManager
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.settings.NighttimeScheduleStore
import com.simon.harmonichackernews.summary.AiSummaryAvailabilityPolicy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Utils {
    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL"
    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET"
    const val KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS"
    private const val MAX_CACHED_STORIES = 200
    private const val STORY_CACHE_DIR = "story_cache"
    private const val STORY_CACHE_FULL_DIR = "full"
    private const val STORY_CACHE_SUMMARY_DIR = "summary"
    private const val STORY_CACHE_FILE_SUFFIX = ".json"
    const val GLOBAL_SHARED_PREFERENCES_KEY: String = AppLaunchPreferenceKeys.STORE_NAME

    @Volatile
    var adservers: AdHostBlocklist = AdHostBlocklist.empty()
    private val adserversLoading = AtomicBoolean(false)
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    fun log(s: String) {
        Log.d("HARMONIC_TAG", s)
    }

    fun log(i: Long) {
        Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(i: Int) {
        Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(i: Float) {
        Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(b: Boolean) {
        Log.d("HARMONIC_TAG", b.toString())
    }

    fun toast(s: String?, ctx: Context?) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    }

    fun loadAdservers(resources: Resources) {
        if (!adservers.isEmpty || !adserversLoading.compareAndSet(
                false,
                true
            )
        ) {
            return
        }
        backgroundExecutor.execute {
            try {
                val blocklist = resources.openRawResource(R.raw.adblockserverlist).use { input ->
                    AdHostBlocklist.decode(input.readBytes())
                }
                if (!blocklist.isEmpty) {
                    adservers = blocklist
                }
            } catch (e: Exception) {
                Log.e("HARMONIC_TAG", "Failed to load ad host blocklist", e)
            } finally {
                adserversLoading.set(false)
            }
        }
    }

    fun cacheStory(ctx: Context?, id: Int, data: String?) {
        if (ctx == null || id <= 0 || data.isNullOrEmpty() || JSONParser.ALGOLIA_ERROR_STRING == data) {
            return
        }

        writeCachedStoryFiles(ctx, id, data)

        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        var cachedStories = AndroidKeyValueStore.global(ctx)
            .getStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
            .toMutableSet()
        val cacheUpdate = StoryCacheIndex.record(
            cachedStories,
            id,
            System.currentTimeMillis(),
            MAX_CACHED_STORIES,
        )
        cachedStories = cacheUpdate.encodedEntries.toMutableSet()
        cacheUpdate.evictedStoryIds.forEach { evictedId ->
            deleteCachedStoryFiles(ctx, evictedId)
            deleteCachedArticleSnapshot(ctx, evictedId)
        }

        sharedPreferences.edit()
            .putStringSet(
                KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS,
                cachedStories
            )
            .apply()
    }

    fun loadCachedStory(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }

        return readStringFromFile(
            getCachedStoryFullFile(
                ctx,
                id
            )
        )
    }

    fun loadCachedStorySummary(ctx: Context?, story: Story?): Boolean {
        if (ctx == null || story == null || story.id <= 0) {
            return false
        }

        var summary = readStringFromFile(
            getCachedStorySummaryFile(
                ctx,
                story.id
            )
        )
        if (summary.isNullOrEmpty()) {
            val fullStory = readStringFromFile(
                getCachedStoryFullFile(
                    ctx,
                    story.id
                )
            )
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, story.id)
            if (!summary.isNullOrEmpty()) {
                writeStringToFile(
                    getCachedStorySummaryFile(
                        ctx,
                        story.id
                    ), summary
                )
            }
        }

        return JSONParser.updateStoryWithCachedStorySummary(story, summary)
    }

    fun cacheStoryPreviewState(ctx: Context?, story: Story?) {
        if (ctx == null || story == null || story.id <= 0 ||
            (!story.previewImageUrlLoaded && story.previewImageUrl.isNullOrEmpty() &&
                    !story.faviconTintColorLoaded)
        ) {
            return
        }

        val appContext = ctx.applicationContext
        val previewState: Story = Story()
        previewState.id = story.id
        previewState.previewImageUrl = story.previewImageUrl
        previewState.previewImageUrlLoaded =
            story.previewImageUrlLoaded || !story.previewImageUrl.isNullOrEmpty()
        previewState.previewImageLoadFailed = story.previewImageLoadFailed
        previewState.previewImageTintColor = story.previewImageTintColor
        previewState.previewImageTintColorLoaded = story.previewImageTintColorLoaded
        previewState.previewImageTintSourceUrl = story.previewImageTintSourceUrl
        previewState.previewImageTintBaseColor = story.previewImageTintBaseColor
        previewState.previewImageTintMode = story.previewImageTintMode
        previewState.faviconTintColor = story.faviconTintColor
        previewState.faviconTintColorLoaded = story.faviconTintColorLoaded
        previewState.faviconTintSourceUrl = story.faviconTintSourceUrl
        previewState.faviconTintBaseColor = story.faviconTintBaseColor
        previewState.faviconTintMode = story.faviconTintMode

        backgroundExecutor.execute {
            writeCachedStoryPreviewState(appContext, previewState)
        }
    }

    fun getCachedPostCount(ctx: Context?): Int {
        if (ctx == null) {
            return 0
        }

        return getCachedPostIds(ctx).size
    }

    fun clearPostCache(ctx: Context?): Int {
        if (ctx == null) {
            return 0
        }

        val cachedPostIds = getCachedPostIds(ctx)
        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        val editor: SharedPreferences.Editor = sharedPreferences.edit()

        for (key in sharedPreferences.all.keys) {
            if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                editor.remove(key)
            } else if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET)) {
                editor.remove(key)
            }
        }

        editor.remove(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
            .apply()

        deleteFileOrDirectory(
            getStoryCacheDir(
                ctx
            )
        )
        deleteFileOrDirectory(
            getArticleCacheDir(
                ctx
            )
        )

        StoryPreviewImageLoader.clearDiskCache(ctx)

        return cachedPostIds.size
    }

    fun removeStoryFromCaches(ctx: Context?, id: Int) {
        if (ctx == null || id <= 0) {
            return
        }

        val sharedPreferences: SharedPreferences =
            ctx.getSharedPreferences(
                GLOBAL_SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE
            )
        var cachedStories = AndroidKeyValueStore.global(ctx)
            .getStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
            .toMutableSet()
        cachedStories = StoryCacheIndex.remove(cachedStories, id).toMutableSet()

        sharedPreferences.edit()
            .remove(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id)
            .remove(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id)
            .putStringSet(
                KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS,
                cachedStories
            )
            .apply()

        deleteCachedStoryFiles(ctx, id)

        val articleFile = getArticleCacheFile(ctx, id)
        if (articleFile.exists() && !articleFile.delete()) {
            articleFile.deleteOnExit()
        }
    }

    private fun getCachedPostIds(ctx: Context): MutableSet<Int> {
        val cachedPostIds = mutableSetOf<Int>()

        val cachedStories = AndroidKeyValueStore.global(ctx)
            .getStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
        cachedPostIds.addAll(StoryCacheIndex.storyIds(cachedStories))

        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        for (key in sharedPreferences.all.keys) {
            if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                CacheFileNamePolicy.storyId(
                    key,
                    KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL,
                )?.let(cachedPostIds::add)
            }
        }

        val articleCacheDir = getArticleCacheDir(ctx)
        articleCacheDir.listFiles()?.forEach { cachedArticleFile ->
                CacheFileNamePolicy.storyId(cachedArticleFile.name, suffix = ".html")
                    ?.let(cachedPostIds::add)
        }

        addCachedPostIdsFromStoryCacheDir(
            cachedPostIds,
            getCachedStoryFullDir(ctx)
        )
        addCachedPostIdsFromStoryCacheDir(
            cachedPostIds,
            getCachedStorySummaryDir(ctx)
        )

        return cachedPostIds
    }

    private fun addCachedPostIdsFromStoryCacheDir(
        cachedPostIds: MutableSet<Int>,
        cacheDir: File
    ) {
        cacheDir.listFiles()?.forEach { cachedStoryFile ->
            CacheFileNamePolicy.storyId(
                cachedStoryFile.name,
                suffix = STORY_CACHE_FILE_SUFFIX,
            )?.let(cachedPostIds::add)
        }
    }

    private fun writeCachedStoryFiles(ctx: Context, id: Int, data: String?) {
        writeStringToFile(
            getCachedStoryFullFile(
                ctx,
                id
            ), data
        )

        val summary: String? = JSONParser.compactAlgoliaStoryResponse(data, id)
        if (!summary.isNullOrEmpty()) {
            writeStringToFile(
                getCachedStorySummaryFile(
                    ctx,
                    id
                ), summary
            )
        }
    }

    private fun writeCachedStoryPreviewState(ctx: Context, previewState: Story) {
        val summaryFile =
            getCachedStorySummaryFile(ctx, previewState.id)
        if (!summaryFile.exists()) {
            return
        }

        var summary = readStringFromFile(summaryFile)
        if (summary.isNullOrEmpty()) {
            val fullStory = readStringFromFile(
                getCachedStoryFullFile(
                    ctx,
                    previewState.id
                )
            )
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, previewState.id)
        }

        val updatedSummary: String? =
            JSONParser.updateCachedStorySummaryPreviewState(summary, previewState)
        if (!updatedSummary.isNullOrEmpty() && summary != updatedSummary) {
            writeStringToFile(summaryFile, updatedSummary)
        }
    }

    private fun getStoryCacheDir(ctx: Context): File {
        return File(
            ctx.filesDir,
            STORY_CACHE_DIR
        )
    }

    private fun getCachedStoryFullDir(ctx: Context): File {
        return File(
            getStoryCacheDir(ctx),
            STORY_CACHE_FULL_DIR
        )
    }

    private fun getCachedStorySummaryDir(ctx: Context): File {
        return File(
            getStoryCacheDir(ctx),
            STORY_CACHE_SUMMARY_DIR
        )
    }

    private fun getCachedStoryFullFile(ctx: Context, id: Int): File {
        return File(
            getCachedStoryFullDir(ctx),
            id.toString() + STORY_CACHE_FILE_SUFFIX
        )
    }

    private fun getCachedStorySummaryFile(ctx: Context, id: Int): File {
        return File(
            getCachedStorySummaryDir(ctx),
            id.toString() + STORY_CACHE_FILE_SUFFIX
        )
    }

    private fun deleteCachedStoryFiles(ctx: Context, id: Int) {
        val fullFile = getCachedStoryFullFile(ctx, id)
        if (fullFile.exists() && !fullFile.delete()) {
            fullFile.deleteOnExit()
        }

        val summaryFile =
            getCachedStorySummaryFile(ctx, id)
        if (summaryFile.exists() && !summaryFile.delete()) {
            summaryFile.deleteOnExit()
        }
    }

    private fun readStringFromFile(file: File?): String? {
        if (file == null || !file.exists()) {
            return null
        }

        try {
            return FileInputStream(file).bufferedReader(Charsets.UTF_8).use { reader ->
                buildString {
                    reader.forEachLine { line -> append(line).append('\n') }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    private fun writeStringToFile(file: File?, data: String?): Boolean {
        if (file == null || data.isNullOrEmpty()) {
            return false
        }

        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false
            }
            FileOutputStream(file).use { outputStream ->
                outputStream.write(data.toByteArray(Charsets.UTF_8))
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    fun loadCachedArticleSnapshot(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }

        val cacheFile = getArticleCacheFile(ctx, id)
        if (!cacheFile.exists()) {
            return null
        }
        if (!ArticleSnapshotPolicy.isValidSize(cacheFile.length())) {
            deleteCachedArticleSnapshot(ctx, id)
            return null
        }
        cacheFile.setLastModified(System.currentTimeMillis())

        try {
            val charsetName = AndroidKeyValueStore.global(ctx)
                .getString(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id)
                .orEmpty()
                .ifEmpty { "UTF-8" }
            return InputStreamReader(FileInputStream(cacheFile), charsetName).buffered().use { reader ->
                buildString {
                    reader.forEachLine { line -> append(line).append('\n') }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    fun loadCachedArticleUrl(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }
        return AndroidKeyValueStore.global(ctx)
            .getString(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id)
    }

    fun deleteCachedArticleSnapshot(ctx: Context?, id: Int) {
        if (ctx == null || id <= 0) {
            return
        }

        val cacheFile = getArticleCacheFile(ctx, id)
        if (cacheFile.exists() && !cacheFile.delete()) {
            cacheFile.deleteOnExit()
        }
        ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id)
            .remove(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id)
            .apply()
    }

    fun getArticleCacheDir(ctx: Context): File {
        return File(ctx.filesDir, "article_cache")
    }

    fun getArticleCacheFile(ctx: Context, id: Int): File {
        return File(
            getArticleCacheDir(ctx),
            id.toString() + ".html"
        )
    }

    private fun deleteFileOrDirectory(file: File?) {
        if (file == null || !file.exists()) {
            return
        }

        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteFileOrDirectory)
        }

        if (!file.delete()) {
            file.deleteOnExit()
        }
    }

    fun hasCachedStories(ctx: Context): Boolean {
        val cached = AndroidKeyValueStore.global(ctx)
            .getStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
        return StoryCacheIndex.recentEntries(cached, System.currentTimeMillis()).any { entry ->
            loadCachedStoryForStoriesList(ctx, entry.storyId) != null
        }
    }

    fun loadCachedStories(ctx: Context): ArrayList<Story> {
        val cached = AndroidKeyValueStore.global(ctx)
            .getStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
        val stories = arrayListOf<Story>()
        for (entry in StoryCacheIndex.recentEntries(cached, System.currentTimeMillis())) {
            loadCachedStoryForStoriesList(ctx, entry.storyId)?.let(stories::add)
        }

        return stories
    }

    private fun loadCachedStoryForStoriesList(ctx: Context?, id: Int): Story? {
        val story = Story().apply { this.id = id }
        var loaded = loadCachedStorySummary(ctx, story)
        if (!loaded) {
            val fullStory = loadCachedStory(ctx, id)
            val summary = JSONParser.compactAlgoliaStoryResponse(fullStory, id)
            loaded = !summary.isNullOrEmpty() && JSONParser.updateStoryWithCachedStorySummary(
                story,
                summary
            )
        }

        if (!loaded || story.isComment) {
            return null
        }

        return story
    }

    fun shouldShowWelcomeDialog(ctx: Context): Boolean {
        return AppLaunchStateStore(AndroidKeyValueStore.global(ctx)).shouldShowWelcomeDialog
    }

    fun markWelcomeDialogShown(ctx: Context) {
        AppLaunchStateStore(AndroidKeyValueStore.global(ctx)).markWelcomeDialogShown()
    }

    fun justUpdated(ctx: Context): Boolean {
        return AppLaunchStateStore(AndroidKeyValueStore.global(ctx))
            .consumeVersionUpgrade(BuildConfig.VERSION_CODE)
    }

    fun isOnWiFi(ctx: Context): Boolean {
        val connectivityManager: ConnectivityManager =
            ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun downloadPDF(context: Context, pdfUrl: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(pdfUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Check if there's an app that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        }
        return false
    }

    fun getColorViaAttr(ctx: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val theme = ctx.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    fun setNighttimeHours(
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        ctx: Context
    ) {
        NighttimeScheduleStore(AndroidKeyValueStore.global(ctx)).save(
            NighttimeSchedule(fromHour, fromMinute, toHour, toMinute),
        )
    }

    fun getNighttimeHours(ctx: Context): IntArray {
        return NighttimeScheduleStore(AndroidKeyValueStore.global(ctx)).load().toIntArray()
    }

    fun pxFromDp(resources: Resources, dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    fun pxFromDpInt(resources: Resources, dp: Float): Int {
        return Math.round(pxFromDp(resources, dp))
    }

    fun isTablet(res: Resources): Boolean {
        return res.getBoolean(R.bool.is_tablet)
    }

    fun openLinkMaybeHN(context: Context?, href: String?) {
        if (context == null || href.isNullOrEmpty()) {
            return
        }

        HackerNewsLinks.parseItemLink(href)?.let { link ->
            openCommentsActivity(link.itemId, link.scrollToCommentId, context)
            return
        }

        AndroidExternalLinkLauncher.openCustomTab(context, href)
    }

    fun openCommentsActivity(id: Int, scrollToCommentId: Int, context: Context) {
        if (context is MainActivity && context.openCommentsItem(id, scrollToCommentId)) {
            return
        }
        val builder = Uri.parse("https://news.ycombinator.com/item").buildUpon()
            .appendQueryParameter("id", id.toString())
        if (scrollToCommentId > 0) {
            builder.fragment(scrollToCommentId.toString())
        }
        val uri = builder.build()

        val intent: Intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canProvideSummary(ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val explicitlyEnabled = if (prefs.contains("pref_ai_summary_enabled")) {
            prefs.getBoolean("pref_ai_summary_enabled", false)
        } else {
            null
        }
        return AiSummaryAvailabilityPolicy.canProvideSummary(
            explicitlyEnabled = explicitlyEnabled,
            mode = prefs.getString("pref_ai_summary_mode", "cloud") ?: "cloud",
            localAvailable = LocalSummaryManager.canAttemptLocalSummarization(),
            cloudApiKeyAvailable = AiSummaryApiKeyStore.hasApiKey(ctx),
        )
    }

    fun isAiSummaryEnabled(ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val explicitlyEnabled = if (prefs.contains("pref_ai_summary_enabled")) {
            prefs.getBoolean("pref_ai_summary_enabled", false)
        } else {
            null
        }
        return AiSummaryAvailabilityPolicy.isEnabled(
            explicitlyEnabled = explicitlyEnabled,
            localAvailable = LocalSummaryManager.canAttemptLocalSummarization(),
            cloudApiKeyAvailable = AiSummaryApiKeyStore.hasApiKey(ctx),
        )
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

}
