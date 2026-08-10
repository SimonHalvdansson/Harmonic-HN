package com.simon.harmonichackernews.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.webkit.URLUtil
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.LocalSummaryManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.simon.harmonichackernews.settings.UserTagCodec
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.data.SavedItemKeys
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository

object Utils {
    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL"
    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET"
    const val KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS"
    private const val MAX_CACHED_STORIES = 200
    const val MAX_CACHED_ARTICLE_BYTES = 5L * 1024L * 1024L
    private const val STORY_CACHE_DIR = "story_cache"
    private const val STORY_CACHE_FULL_DIR = "full"
    private const val STORY_CACHE_SUMMARY_DIR = "summary"
    private const val STORY_CACHE_FILE_SUFFIX = ".json"
    const val GLOBAL_SHARED_PREFERENCES_KEY: String =
        "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY"

    const val KEY_SHARED_PREFERENCES_BOOKMARKS: String =
        SavedItemKeys.BOOKMARKS
    const val KEY_SHARED_PREFERENCES_USER_TAGS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
    const val KEY_SHARED_PREFERENCES_FIRST_TIME: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME"
    const val KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val KEY_SHARED_PREFERENCES_LAST_VERSION: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
    const val KEY_SHARED_PREFERENCES_FAVORITES: String =
        SavedItemKeys.FAVORITES
    const val KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS: String =
        SavedItemKeys.FAVORITE_COMMENTS
    const val KEY_SHARED_PREFERENCES_UPVOTED: String =
        SavedItemKeys.UPVOTED
    const val KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS: String =
        SavedItemKeys.UPVOTED_COMMENTS

    const val KEY_NIGHTTIME_FROM_HOUR: String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_HOUR"
    const val KEY_NIGHTTIME_FROM_MINUTE: String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_MINUTE"
    const val KEY_NIGHTTIME_TO_HOUR: String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_HOUR"
    const val KEY_NIGHTTIME_TO_MINUTE: String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_MINUTE"

    const val URL_TOP: String = "https://hacker-news.firebaseio.com/v0/topstories.json"
    const val URL_NEW: String = "https://hacker-news.firebaseio.com/v0/newstories.json"
    const val URL_BEST: String = "https://hacker-news.firebaseio.com/v0/beststories.json"
    const val URL_ASK: String = "https://hacker-news.firebaseio.com/v0/askstories.json"
    const val URL_SHOW: String = "https://hacker-news.firebaseio.com/v0/showstories.json"
    const val URL_JOBS: String = "https://hacker-news.firebaseio.com/v0/jobstories.json"

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
        var cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
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
        var cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
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

        val cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        cachedPostIds.addAll(StoryCacheIndex.storyIds(cachedStories))

        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        for (key in sharedPreferences.all.keys) {
            if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                addCachedPostId(
                    cachedPostIds,
                    key,
                    KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL
                )
            }
        }

        val articleCacheDir = getArticleCacheDir(ctx)
        articleCacheDir.listFiles()?.forEach { cachedArticleFile ->
                addCachedPostId(
                    cachedPostIds,
                    cachedArticleFile.name,
                    "",
                    ".html"
                )
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
            addCachedPostId(
                cachedPostIds,
                cachedStoryFile.name,
                "",
                STORY_CACHE_FILE_SUFFIX
            )
        }
    }

    private fun addCachedPostId(
        cachedPostIds: MutableSet<Int>,
        value: String,
        prefix: String,
        suffix: String = ""
    ) {
        if (!value.startsWith(prefix) || !value.endsWith(suffix)) {
            return
        }

        val end = value.length - suffix.length
        value.substring(prefix.length, end).toIntOrNull()?.let(cachedPostIds::add)
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
        if (cacheFile.length() <= 0L || cacheFile.length() > MAX_CACHED_ARTICLE_BYTES) {
            deleteCachedArticleSnapshot(ctx, id)
            return null
        }
        cacheFile.setLastModified(System.currentTimeMillis())

        try {
            val charsetName = SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id
            ).orEmpty().ifEmpty { "UTF-8" }
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
        return SettingsUtils.readStringFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id
        )
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
        val cached = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        return StoryCacheIndex.entries(cached).any { entry ->
            entry.cachedAtMillis >= limit &&
                loadCachedStoryForStoriesList(ctx, entry.storyId) != null
        }
    }

    fun loadCachedStories(ctx: Context): ArrayList<Story> {
        val cached = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        val stories = arrayListOf<Story>()
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000

        val orderedIds = mutableListOf<Pair<Long, Int>>()

        for (entry in StoryCacheIndex.entries(cached)) {
            if (entry.cachedAtMillis < limit) continue
            orderedIds += entry.cachedAtMillis to entry.storyId
        }

        orderedIds.sortBy { it.first }

        for (pair in orderedIds) {
            loadCachedStoryForStoriesList(ctx, pair.second)?.let(stories::add)
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

    fun loadBookmarks(
        ctx: Context,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        return SavedItemCodec.toBookmarks(
            savedItems(ctx).loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = sorted),
        )
    }

    fun loadBookmarks(
        sorted: Boolean,
        bookmarksString: String?
    ): ArrayList<Bookmark> {
        return SavedItemCodec.toBookmarks(SavedItemCodec.decode(bookmarksString, sorted))
    }

    fun isBookmarked(ctx: Context, id: Int): Boolean {
        return savedItems(ctx).contains(SavedItemSource.BOOKMARKS, id)
    }

    fun saveBookmarks(
        ctx: Context,
        bookmarks: ArrayList<Bookmark>
    ) {
        savedItems(ctx).saveItems(
            SavedItemSource.BOOKMARKS,
            SavedItemCodec.fromBookmarks(bookmarks),
        )
    }

    fun addBookmark(ctx: Context, id: Int) {
        savedItems(ctx).setMembership(
            SavedItemSource.BOOKMARKS,
            id,
            present = true,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun removeBookmark(ctx: Context, id: Int) {
        savedItems(ctx).setMembership(
            SavedItemSource.BOOKMARKS,
            id,
            present = false,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun loadFavorites(
        ctx: Context,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        return loadSavedItemList(ctx, SavedItemSource.FAVORITES, sorted)
    }

    fun loadUpvoted(
        ctx: Context,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        return loadSavedItemList(ctx, SavedItemSource.UPVOTED, sorted)
    }

    fun loadFavoriteCommentIds(ctx: Context): MutableSet<Int> {
        return savedItems(ctx).loadCommentIds(SavedItemSource.FAVORITES).toMutableSet()
    }

    fun loadUpvotedCommentIds(ctx: Context): MutableSet<Int> {
        return savedItems(ctx).loadCommentIds(SavedItemSource.UPVOTED).toMutableSet()
    }

    private fun loadSavedItemList(
        ctx: Context,
        source: SavedItemSource,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        val items = if (sorted) {
            savedItems(ctx).loadItemsByDescendingId(source)
        } else {
            savedItems(ctx).loadItems(source)
        }
        return SavedItemCodec.toBookmarks(items)
    }

    fun isFavorited(ctx: Context, id: Int): Boolean {
        return savedItems(ctx).contains(SavedItemSource.FAVORITES, id)
    }

    fun isUpvoted(ctx: Context, id: Int, comment: Boolean): Boolean {
        if (comment) {
            return id in savedItems(ctx).loadCommentIds(SavedItemSource.UPVOTED)
        }
        return savedItems(ctx).contains(SavedItemSource.UPVOTED, id)
    }

    fun saveFavorites(
        ctx: Context,
        favorites: ArrayList<Bookmark>
    ) {
        savedItems(ctx).saveItems(
            SavedItemSource.FAVORITES,
            SavedItemCodec.fromBookmarks(favorites),
        )
    }

    fun saveFavoriteIds(ctx: Context, ids: List<Int>) {
        savedItems(ctx).saveIds(
            SavedItemSource.FAVORITES,
            ids,
            System.currentTimeMillis(),
        )
    }

    fun saveFavoriteCommentIds(
        ctx: Context,
        ids: Set<Int>
    ) {
        savedItems(ctx).saveCommentIds(SavedItemSource.FAVORITES, ids)
    }

    fun saveUpvotedIds(ctx: Context, ids: List<Int>) {
        savedItems(ctx).saveIds(
            SavedItemSource.UPVOTED,
            ids,
            System.currentTimeMillis(),
        )
    }

    fun saveUpvotedCommentIds(
        ctx: Context,
        ids: Set<Int>
    ) {
        savedItems(ctx).saveCommentIds(SavedItemSource.UPVOTED, ids)
    }

    fun addFavorite(ctx: Context, id: Int) {
        savedItems(ctx).setMembership(
            SavedItemSource.FAVORITES,
            id,
            present = true,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean) {
        if (favorite) {
            addFavorite(ctx, id)
        } else {
            removeFavorite(ctx, id)
        }
    }

    fun removeFavorite(ctx: Context, id: Int) {
        savedItems(ctx).setMembership(
            SavedItemSource.FAVORITES,
            id,
            present = false,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun setUpvoted(
        ctx: Context,
        id: Int,
        comment: Boolean,
        upvoted: Boolean
    ) {
        if (comment) {
            savedItems(ctx).setCommentMembership(SavedItemSource.UPVOTED, id, upvoted)
            return
        }
        savedItems(ctx).setMembership(
            SavedItemSource.UPVOTED,
            id,
            present = upvoted,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    private fun savedItems(ctx: Context): SavedItemsRepository =
        SavedItemsRepository(AndroidKeyValueStore.global(ctx))

    fun getThousandSeparatedString(n: Int): String {
        val bd = BigDecimal(n)
        val formatter = NumberFormat.getInstance(Locale.US)

        return formatter.format(bd.toLong())
    }

    fun getFilterWords(ctx: Context): ArrayList<String> {
        return getCommaSeparatedPreference(
            ctx,
            "pref_filter"
        )
    }

    fun getFilterDomains(ctx: Context): ArrayList<String> {
        return getCommaSeparatedPreference(
            ctx,
            "pref_filter_domains"
        )
    }

    fun getFilteredUsers(ctx: Context): MutableSet<String> {
        return getCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            true
        )
    }

    fun removeFilteredUser(ctx: Context, username: String?): Boolean {
        val normalizedUsername = username
            ?.takeUnless(String::isEmpty)
            ?.lowercase(Locale.getDefault())
            ?.trim { it <= ' ' }
            ?: return false

        val users = getFilteredUsers(ctx)
        users.remove(normalizedUsername)
        saveCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            users
        )

        return true
    }

    fun addFilteredUser(ctx: Context, username: String?): Boolean {
        val normalizedUsername = username
            ?.takeUnless(String::isEmpty)
            ?.lowercase(Locale.getDefault())
            ?.trim { it <= ' ' }
            ?: return false

        val users = getFilteredUsers(ctx)
        users.add(normalizedUsername)
        saveCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            users
        )

        return true
    }

    private fun getCommaSeparatedPreference(
        ctx: Context,
        key: String?
    ): ArrayList<String> {
        return getCommaSeparatedPreference(ctx, key, false)
    }

    private fun getCommaSeparatedPreference(
        ctx: Context,
        key: String?,
        lowercase: Boolean
    ): ArrayList<String> {
        val prefText = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString(key, null)
            .orEmpty()
        if (prefText.isEmpty()) {
            return arrayListOf()
        }

        val normalizedText = if (lowercase) {
            prefText.lowercase(Locale.getDefault())
        } else {
            prefText
        }
        return normalizedText.split(',')
            .dropLastWhile(String::isEmpty)
            .mapTo(ArrayList()) { it.trim { character -> character <= ' ' } }
    }

    private fun getCommaSeparatedPreferenceSet(
        ctx: Context,
        key: String?,
        lowercase: Boolean
    ): MutableSet<String> {
        return HashSet(
            getCommaSeparatedPreference(
                ctx,
                key,
                lowercase
            )
        )
    }

    private fun saveCommaSeparatedPreferenceSet(
        ctx: Context,
        key: String?,
        values: MutableSet<String>
    ) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(key, joinCommaSeparated(values))
            .apply()
    }

    private fun joinCommaSeparated(values: Set<String>): String = values.joinToString(",")

    fun getUserTags(ctx: Context): MutableMap<String, String> {
        return readUserTags(ctx, true)
    }

    fun getUserTagsWithOriginalUsernames(ctx: Context): MutableMap<String, String> {
        return readUserTags(ctx, false)
    }

    private fun readUserTags(
        ctx: Context,
        normalizeUsernames: Boolean
    ): MutableMap<String, String> {
        return UserTagCodec.decode(
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_USER_TAGS,
            ),
            normalizeUsernames,
        )
    }

    fun getUserTag(ctx: Context, username: String?): String {
        return UserTagCodec.tagFor(
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_USER_TAGS,
            ),
            username,
        )
    }

    fun setUserTag(ctx: Context, username: String?, tag: String?) {
        val serialized = UserTagCodec.update(
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_USER_TAGS,
            ),
            username,
            tag,
        ) ?: return
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_USER_TAGS,
            serialized,
        )
    }

    fun shouldShowWelcomeDialog(ctx: Context): Boolean {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        return !sharedPref.getBoolean(
            KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN,
            false
        )
    }

    fun markWelcomeDialogShown(ctx: Context) {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        sharedPref.edit().putBoolean(
            KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN,
            true
        ).apply()
    }

    fun justUpdated(ctx: Context): Boolean {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        if (BuildConfig.VERSION_CODE > sharedPref.getInt(
                KEY_SHARED_PREFERENCES_LAST_VERSION,
                -1
            )
        ) {
            sharedPref.edit().putInt(
                KEY_SHARED_PREFERENCES_LAST_VERSION,
                BuildConfig.VERSION_CODE
            ).apply()
            return true
        }
        return false
    }

    fun getTimeAgo(time: Long): String {
        return RelativeTimeFormatter.format(time, System.currentTimeMillis())
    }

    fun isOnWiFi(ctx: Context): Boolean {
        val connectivityManager: ConnectivityManager =
            ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    @JvmOverloads
    fun launchCustomTab(
        ctx: Context,
        url: String?,
        shareable: Boolean = true
    ) {
        val originalUrl = url ?: return
        if (SettingsUtils.shouldUseExternalBrowser(ctx) || !isCustomTabSupported(ctx)) {
            launchInExternalBrowser(ctx, originalUrl)
            return
        }

        try {
            createCustomTabsIntent(ctx, shareable).launchUrl(ctx, Uri.parse(originalUrl))
        } catch (e: Exception) {
            e.printStackTrace()

            try {
                createCustomTabsIntent(ctx, shareable).launchUrl(
                    ctx,
                    Uri.parse(URLUtil.guessUrl(originalUrl))
                )
            } catch (_: Exception) {
                val fallbackUrl = originalUrl.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                } ?: "http://$originalUrl"
                try {
                    createCustomTabsIntent(ctx, shareable).launchUrl(ctx, Uri.parse(fallbackUrl))
                } catch (_: Exception) {
                    launchInExternalBrowser(ctx, fallbackUrl)
                }
            }
        }
    }

    private fun createCustomTabsIntent(ctx: Context, shareable: Boolean): CustomTabsIntent {
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(
                ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx))
            )
            .build()
        return CustomTabsIntent.Builder()
            .setShareState(
                if (shareable) CustomTabsIntent.SHARE_STATE_ON
                else CustomTabsIntent.SHARE_STATE_OFF
            )
            .setDefaultColorSchemeParams(colorScheme)
            .build()
    }

    fun launchInExternalBrowser(ctx: Context, url: String) {
        try {
            openExternalUrl(ctx, url)
        } catch (e: Exception) {
            // failed for the first time, let's try to guess a fix to the url
            try {
                openExternalUrl(ctx, URLUtil.guessUrl(url))
            } catch (_: Exception) {
                // automated fix didn't work, let's try to do it manually
                val fallbackUrl = url.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                } ?: "http://$url"
                try {
                    openExternalUrl(ctx, fallbackUrl)
                } catch (_: Exception) {
                    Toast.makeText(
                        ctx,
                        "Couldn't open link to: $fallbackUrl",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openExternalUrl(ctx: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        getPackageForExternalUrl(ctx, browserIntent)?.let(browserIntent::setPackage)
        ctx.startActivity(browserIntent)
    }

    private fun getPackageForExternalUrl(
        ctx: Context,
        browserIntent: Intent
    ): String? {
        val defaultBrowserPackageName = ctx.defaultBrowserPackageName() ?: return null
        val resolveInfo = ctx.packageManager
            .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val resolvedPackageName = resolveInfo?.activityInfo?.packageName

        // force browser only when VIEW resolves to Harmonic itself (self-loop) or a known bad resolver.
        if (ctx.packageName == resolvedPackageName
            || ctx.isInvalidViewHandlerPackage(resolvedPackageName)
        ) {
            return defaultBrowserPackageName
        }

        return null
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

    fun isCustomTabSupported(context: Context): Boolean {
        return getCustomTabsPackages(context).isNotEmpty()
    }

    /**
     * Returns a list of packages that support Custom Tabs.
     */
    private fun getCustomTabsPackages(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        // Get default VIEW intent handler.
        val activityIntent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null))

        // Get all apps that can handle VIEW intents.
        return pm.queryIntentActivities(activityIntent, 0).filter { info ->
            val serviceIntent = Intent().apply {
                action = ACTION_CUSTOM_TABS_CONNECTION
                setPackage(info.activityInfo.packageName)
            }
            // Check if this package also resolves the Custom Tabs service.
            pm.resolveService(serviceIntent, 0) != null
        }
    }

    fun getColorViaAttr(ctx: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val theme = ctx.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    @Throws(IOException::class)
    fun writeInFile(ctx: Context, uri: Uri, text: String?) {
        val outputStream = checkNotNull(ctx.contentResolver.openOutputStream(uri))
        outputStream.bufferedWriter().use { writer -> writer.write(text) }
    }

    @Throws(IOException::class)
    fun readFileContent(ctx: Context, uri: Uri): String {
        val inputStream = checkNotNull(ctx.contentResolver.openInputStream(uri))
        return inputStream.bufferedReader().use { reader ->
            buildString {
                reader.forEachLine(::append)
            }
        }
    }

    fun setNighttimeHours(
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        ctx: Context
    ) {
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_NIGHTTIME_FROM_HOUR,
            fromHour.toString()
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_NIGHTTIME_FROM_MINUTE,
            fromMinute.toString()
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_NIGHTTIME_TO_HOUR,
            toHour.toString()
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_NIGHTTIME_TO_MINUTE,
            toMinute.toString()
        )
    }

    fun getNighttimeHours(ctx: Context): IntArray {
        return intArrayOf(
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_NIGHTTIME_FROM_HOUR,
                "21"
            )?.toIntOrNull() ?: 21,
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_NIGHTTIME_FROM_MINUTE,
                "0"
            )?.toIntOrNull() ?: 0,
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_NIGHTTIME_TO_HOUR,
                "6"
            )?.toIntOrNull() ?: 6,
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_NIGHTTIME_TO_MINUTE,
                "0"
            )?.toIntOrNull() ?: 0
        )
    }

    fun timeInSecondsMoreThanTwoWeeksAgo(time: Int): Boolean {
        return AgePolicy.isOlderThan(time, System.currentTimeMillis(), TimeUnit.DAYS.toMillis(14))
    }

    fun timeInSecondsMoreThanTwoHoursAgo(time: Int): Boolean {
        return AgePolicy.isOlderThan(time, System.currentTimeMillis(), TimeUnit.HOURS.toMillis(2))
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

        launchCustomTab(context, href)
    }

    fun getHackerNewsItemUriFromText(text: String?): Uri? {
        return HackerNewsLinks.findItemLink(text)?.url?.let(Uri::parse)
    }

    fun isHackerNewsItemUri(uri: Uri?): Boolean {
        return HackerNewsLinks.parseItemLink(uri?.toString()) != null
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
        if (prefs.contains("pref_ai_summary_enabled")
            && !prefs.getBoolean("pref_ai_summary_enabled", false)
        ) {
            return false
        }
        val mode = prefs.getString("pref_ai_summary_mode", "cloud") ?: "cloud"
        if (mode == "local") {
            return LocalSummaryManager.canAttemptLocalSummarization()
        }
        return AiSummaryApiKeyStore.hasApiKey(ctx)
    }

    fun isAiSummaryEnabled(ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.contains("pref_ai_summary_enabled")) {
            return prefs.getBoolean("pref_ai_summary_enabled", false)
        }
        return isAiSummaryEnabledByDefault(ctx)
    }

    private fun isAiSummaryEnabledByDefault(ctx: Context): Boolean =
        LocalSummaryManager.canAttemptLocalSummarization() || AiSummaryApiKeyStore.hasApiKey(ctx)

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun linkify(input: String?): String? {
        return HtmlTextUtils.linkify(input)
    }

    fun expandShortenedAnchorText(inputHtml: String?): String? {
        return HtmlTextUtils.expandShortenedAnchorText(inputHtml)
    }
}
