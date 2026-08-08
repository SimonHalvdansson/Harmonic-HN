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
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.SummaryManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.URI
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.json.JSONException
import org.json.JSONObject
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser

object Utils {
    private val HN_ITEM_URL_PATTERN: Pattern = Pattern.compile(
        "https?://news\\.ycombinator\\.com/item\\?[^\\s<>\"']+",
        Pattern.CASE_INSENSITIVE
    )
    private val LINKIFY_ANCHOR_PATTERN: Pattern =
        Pattern.compile("(?is)<a\\b[^>]*>.*?</a>")
    private val LINKIFY_URL_PATTERN: Pattern = Pattern.compile(
        ("(https?:(?:/{1}|(?:&#x2F;)|(?:&#47;))"
                + "(?:/{1}|(?:&#x2F;)|(?:&#47;))"
                + "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)")
    )
    private const val LINKIFY_TRAILING_PUNCTUATION = ".,;:!?)"

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
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS"
    const val KEY_SHARED_PREFERENCES_USER_TAGS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
    const val KEY_SHARED_PREFERENCES_FIRST_TIME: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME"
    const val KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val KEY_SHARED_PREFERENCES_LAST_VERSION: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
    const val KEY_SHARED_PREFERENCES_FAVORITES: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITES"
    const val KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS"
    const val KEY_SHARED_PREFERENCES_UPVOTED: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED"
    const val KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS: String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS"

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

    @Throws(Exception::class)
    fun getDomainName(url: String): String {
        val commonHttpDomain = getCommonHttpDomain(url)
        if (commonHttpDomain != null) {
            return commonHttpDomain
        }

        val domain = URI(url.removeSuffix("#")).host
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    private fun getCommonHttpDomain(url: String?): String? {
        // Story URLs overwhelmingly use this shape. Keep uncommon authorities and malformed
        // URLs on the URI parser so the fast path does not broaden the accepted input.
        url ?: return null

        val hostStart = when {
            url.startsWith("https://") -> 8
            url.startsWith("http://") -> 7
            else -> return null
        }

        val length = url.length
        if (hostStart >= length || !hasOnlyCommonUriCharacters(url)) {
            return null
        }

        var authorityEnd = length
        for (i in hostStart..<length) {
            val character = url[i]
            if (character == '/' || character == '?' || character == '#') {
                authorityEnd = i
                break
            }
        }
        if (authorityEnd <= hostStart) {
            return null
        }

        var hostEnd = authorityEnd
        for (i in hostStart..<authorityEnd) {
            val character = url[i]
            if (character == '@' || character == '[' || character == ']') {
                return null
            }
            if (character == ':') {
                hostEnd = i
                if (i + 1 >= authorityEnd) {
                    return null
                }
                for (portIndex in i + 1..<authorityEnd) {
                    val portCharacter = url[portIndex]
                    if (portCharacter !in '0'..'9') {
                        return null
                    }
                }
                break
            }
        }
        if (hostEnd <= hostStart) {
            return null
        }

        var labelStart = hostStart
        var containsLetter = false
        for (i in hostStart..<hostEnd) {
            val character = url[i]
            if (character == '.') {
                if (i == labelStart || url[i - 1] == '-') {
                    return null
                }
                labelStart = i + 1
                continue
            }

            val isLetter = character in 'a'..'z' || character in 'A'..'Z'
            val isDigit = character in '0'..'9'
            if (!isLetter && !isDigit && character != '-') {
                return null
            }
            if (i == labelStart && character == '-') {
                return null
            }
            containsLetter = containsLetter || isLetter
        }

        val finalHostCharacter = url[hostEnd - 1]
        if (finalHostCharacter == '-' || !containsLetter) {
            return null
        }

        val domain = url.substring(hostStart, hostEnd)
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    private fun hasOnlyCommonUriCharacters(url: String): Boolean {
        var sawFragment = false
        var i = 0
        while (i < url.length) {
            val character = url[i]
            val unreserved = character in 'a'..'z'
                    || character in 'A'..'Z'
                    || character in '0'..'9'
                    || character in "-._~"
            if (unreserved
                || character == ':' || character == '/' || character == '?' || character == '@' || character == '!' || character == '$' || character == '&' || character == '\'' || character == '(' || character == ')' || character == '*' || character == '+' || character == ',' || character == ';' || character == '='
            ) {
                i++
                continue
            }
            if (character == '#' && !sawFragment) {
                sawFragment = true
                i++
                continue
            }
            if (character != '%' || i + 2 >= url.length || !isHexDigit(
                    url[i + 1]
                ) || !isHexDigit(url[i + 2])
            ) {
                return false
            }
            i += 3
        }
        return true
    }

    private fun isHexDigit(character: Char): Boolean =
        character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'

    fun formatDomainNameForDisplay(
        domain: String?,
        includeTopLevelDomain: Boolean
    ): String? {
        if (includeTopLevelDomain || domain.isNullOrEmpty()) {
            return domain
        }

        val lastDotIndex = domain.lastIndexOf('.')
        if (lastDotIndex <= 0) {
            return domain
        }

        return domain.substring(0, lastDotIndex)
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
                val blocklist = AdHostBlocklist.read(
                    resources.openRawResource(R.raw.adblockserverlist)
                )
                if (!blocklist.isEmpty) {
                    adservers = blocklist
                }
            } catch (e: IOException) {
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
        addCachedStoryIndexEntry(
            cachedStories,
            id,
            System.currentTimeMillis()
        )
        evictOldCachedStories(ctx, cachedStories)

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
        val cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        removeCachedStoryIndexEntry(cachedStories, id)

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
        cachedStories?.forEach { cachedStory ->
                val id =
                    getCachedStoryIndexEntryId(cachedStory)
                if (id > 0) {
                    cachedPostIds.add(id)
                }
        }

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

    private fun addCachedStoryIndexEntry(
        cachedStories: MutableSet<String>,
        id: Int,
        time: Long
    ) {
        removeCachedStoryIndexEntry(cachedStories, id)
        cachedStories.add("$id-$time")
    }

    private fun removeCachedStoryIndexEntry(
        cachedStories: MutableSet<String>?,
        id: Int
    ) {
        cachedStories?.removeAll { cached ->
            val cachedId = getCachedStoryIndexEntryId(cached)
            cachedId <= 0 || cachedId == id
        }
    }

    private fun evictOldCachedStories(
        ctx: Context,
        cachedStories: MutableSet<String>
    ) {
        while (cachedStories.size > MAX_CACHED_STORIES) {
            var oldestEntry: String? = null
            var oldestTime: Long = -1
            var oldestId = -1

            for (cachedStory in cachedStories) {
                val id =
                    getCachedStoryIndexEntryId(cachedStory)
                val time = getCachedStoryIndexEntryTime(
                    cachedStory
                )
                if (id <= 0 || time < 0) {
                    oldestEntry = cachedStory
                    break
                }
                if (oldestTime == -1L || time < oldestTime) {
                    oldestTime = time
                    oldestId = id
                    oldestEntry = cachedStory
                }
            }

            if (oldestEntry == null) {
                break
            }

            cachedStories.remove(oldestEntry)
            if (oldestId > 0) {
                deleteCachedStoryFiles(ctx, oldestId)
                deleteCachedArticleSnapshot(ctx, oldestId)
            }
        }
    }

    private fun getCachedStoryIndexEntryId(entry: String?): Int =
        entry?.split('-')?.takeIf { it.size == 2 }?.first()?.toIntOrNull() ?: -1

    private fun getCachedStoryIndexEntryTime(entry: String?): Long =
        entry?.split('-')?.takeIf { it.size == 2 }?.last()?.toLongOrNull() ?: -1

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
        return cached.any { entry ->
            val id = getCachedStoryIndexEntryId(entry)
            val time = getCachedStoryIndexEntryTime(entry)
            id > 0 && time >= limit && loadCachedStoryForStoriesList(ctx, id) != null
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

        for (entry in cached) {
            val id = getCachedStoryIndexEntryId(entry)
            val time = getCachedStoryIndexEntryTime(entry)
            if (id <= 0 || time < 0) continue
            if (time < limit) continue

            orderedIds += time to id
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
        return loadBookmarks(
            sorted,
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_BOOKMARKS
            )
        )
    }

    fun loadBookmarks(
        sorted: Boolean,
        bookmarksString: String?
    ): ArrayList<Bookmark> {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */

        val bookmarks = ArrayList<Bookmark>()

        if (bookmarksString == null || bookmarksString.isEmpty()) {
            return bookmarks
        }

        val pairs =
            bookmarksString.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairs) {
            val info = pair.split("q".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (info.size == 2) {
                bookmarks += Bookmark().apply {
                    id = info[0].toInt()
                    created = info[1].toLong()
                }
            }
        }

        if (sorted) {
            bookmarks.sortByDescending { it.created }
        }

        return bookmarks
    }

    fun isBookmarked(ctx: Context, id: Int): Boolean {
        return loadBookmarks(ctx, false).any { it.id == id }
    }

    fun saveBookmarks(
        ctx: Context,
        bookmarks: ArrayList<Bookmark>
    ) {
        saveBookmarkList(
            ctx,
            KEY_SHARED_PREFERENCES_BOOKMARKS,
            bookmarks
        )
    }

    private fun saveBookmarkList(
        ctx: Context,
        key: String?,
        bookmarks: List<Bookmark>
    ) {
        val value = bookmarks.joinToString("-") { bookmark ->
            "${bookmark.id}q${bookmark.created}"
        }
        SettingsUtils.saveStringToSharedPreferences(ctx, key, value)
    }

    fun addBookmark(ctx: Context, id: Int) {
        if (isBookmarked(ctx, id)) {
            return
        }

        val bookmarks = loadBookmarks(ctx, false)
        bookmarks += Bookmark().apply {
            this.id = id
            created = System.currentTimeMillis()
        }
        saveBookmarks(ctx, bookmarks)
    }

    fun removeBookmark(ctx: Context, id: Int) {
        val bookmarks = loadBookmarks(ctx, false)

        val index = bookmarks.indexOfFirst { it.id == id }
        if (index >= 0) {
            bookmarks.removeAt(index)
        }

        saveBookmarks(ctx, bookmarks)
    }

    fun loadFavorites(
        ctx: Context,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        return loadSavedItemList(
            ctx,
            KEY_SHARED_PREFERENCES_FAVORITES,
            sorted
        )
    }

    fun loadUpvoted(
        ctx: Context,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        return loadSavedItemList(
            ctx,
            KEY_SHARED_PREFERENCES_UPVOTED,
            sorted
        )
    }

    fun loadFavoriteCommentIds(ctx: Context): MutableSet<Int> {
        return SettingsUtils.readIntSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS
        )
    }

    fun loadUpvotedCommentIds(ctx: Context): MutableSet<Int> {
        return SettingsUtils.readIntSetFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS
        )
    }

    private fun loadSavedItemList(
        ctx: Context,
        key: String?,
        sorted: Boolean
    ): ArrayList<Bookmark> {
        val items = loadBookmarks(
            false,
            SettingsUtils.readStringFromSharedPreferences(ctx, key)
        )
        if (sorted) {
            items.sortByDescending { it.id }
        }
        return items
    }

    fun isFavorited(ctx: Context, id: Int): Boolean {
        return loadFavorites(ctx, false).any { it.id == id }
    }

    fun isUpvoted(ctx: Context, id: Int, comment: Boolean): Boolean {
        if (comment) {
            return loadUpvotedCommentIds(ctx).contains(id)
        }

        return loadUpvoted(ctx, false).any { it.id == id }
    }

    fun saveFavorites(
        ctx: Context,
        favorites: ArrayList<Bookmark>
    ) {
        saveBookmarkList(
            ctx,
            KEY_SHARED_PREFERENCES_FAVORITES,
            favorites
        )
    }

    fun saveFavoriteIds(ctx: Context, ids: MutableList<Int>) {
        saveSavedItemIds(
            ctx,
            KEY_SHARED_PREFERENCES_FAVORITES,
            ids
        )
    }

    fun saveFavoriteCommentIds(
        ctx: Context,
        ids: MutableSet<Int>
    ) {
        SettingsUtils.saveIntSetToSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS,
            ids
        )
    }

    fun saveUpvotedIds(ctx: Context, ids: MutableList<Int>) {
        saveSavedItemIds(
            ctx,
            KEY_SHARED_PREFERENCES_UPVOTED,
            ids
        )
    }

    fun saveUpvotedCommentIds(
        ctx: Context,
        ids: MutableSet<Int>
    ) {
        SettingsUtils.saveIntSetToSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS,
            ids
        )
    }

    private fun saveSavedItemIds(
        ctx: Context,
        key: String?,
        ids: MutableList<Int>
    ) {
        val items = ArrayList<Bookmark>()
        val seenIds = mutableSetOf<Int>()
        val now = System.currentTimeMillis()

        for (id in ids) {
            if (!seenIds.add(id)) {
                continue
            }

            items += Bookmark().apply {
                this.id = id
                created = now - items.size
            }
        }

        saveBookmarkList(ctx, key, items)
    }

    fun addFavorite(ctx: Context, id: Int) {
        if (isFavorited(ctx, id)) {
            return
        }

        val favorites = loadFavorites(ctx, false)
        favorites += Bookmark().apply {
            this.id = id
            created = System.currentTimeMillis()
        }
        saveFavorites(ctx, favorites)
    }

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean) {
        if (favorite) {
            addFavorite(ctx, id)
        } else {
            removeFavorite(ctx, id)
        }
    }

    fun removeFavorite(ctx: Context, id: Int) {
        val favorites = loadFavorites(ctx, false)

        val index = favorites.indexOfFirst { it.id == id }
        if (index >= 0) {
            favorites.removeAt(index)
        }

        saveFavorites(ctx, favorites)
    }

    fun setUpvoted(
        ctx: Context,
        id: Int,
        comment: Boolean,
        upvoted: Boolean
    ) {
        if (comment) {
            val upvotedCommentIds =
                loadUpvotedCommentIds(ctx)
            if (upvoted) {
                upvotedCommentIds.add(id)
            } else {
                upvotedCommentIds.remove(id)
            }
            saveUpvotedCommentIds(ctx, upvotedCommentIds)
            return
        }

        val upvotedItems = loadUpvoted(ctx, false)
        val existingIndex = upvotedItems.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            if (!upvoted) {
                upvotedItems.removeAt(existingIndex)
                saveBookmarkList(
                    ctx,
                    KEY_SHARED_PREFERENCES_UPVOTED,
                    upvotedItems
                )
            }
            return
        }

        if (upvoted) {
            upvotedItems += Bookmark().apply {
                this.id = id
                created = System.currentTimeMillis()
            }
            saveBookmarkList(
                ctx,
                KEY_SHARED_PREFERENCES_UPVOTED,
                upvotedItems
            )
        }
    }

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
        val jsonString = SettingsUtils.readStringFromSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_USER_TAGS
        )
        val map = mutableMapOf<String, String>()
        if (!jsonString.isNullOrEmpty()) {
            try {
                val obj = JSONObject(jsonString)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optString(key, "")
                    val username = key.trim { it <= ' ' }
                    val normalizedUsername = if (normalizeUsernames) {
                        username.lowercase(Locale.getDefault())
                    } else {
                        username
                    }
                    map[normalizedUsername] = value
                }
            } catch (e: JSONException) {
                // Invalid JSON in prefs; just start fresh
                e.printStackTrace()
            }
        }
        return map
    }

    fun getUserTag(ctx: Context, username: String?): String {
        val normalizedUsername = username
            ?.takeUnless(String::isEmpty)
            ?.lowercase(Locale.getDefault())
            ?.trim { it <= ' ' }
            ?: return ""
        return getUserTags(ctx)[normalizedUsername].orEmpty()
    }

    fun setUserTag(ctx: Context, username: String?, tag: String?) {
        val key = username?.takeUnless(String::isEmpty)?.trim { it <= ' ' } ?: return
        val map = getUserTagsWithOriginalUsernames(ctx)
        map.keys.removeAll { savedUsername -> savedUsername.equals(key, ignoreCase = true) }
        if (!tag.isNullOrEmpty()) {
            map[key] = tag.trim { it <= ' ' }
        }
        // Convert back to JSON
        val obj = JSONObject()
        for ((savedUsername, savedTag) in map) {
            try {
                obj.put(savedUsername, savedTag)
            } catch (ex: JSONException) {
                ex.printStackTrace()
            }
        }
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            KEY_SHARED_PREFERENCES_USER_TAGS,
            obj.toString()
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

    /**
     * Check if time represented as minutes since midnight is between two other times.
     * 
     * 
     * If `initialTime` is after `finalTime`, then `currentTime` must be between
     * last day's `initialTime` and this day's `finalTime` or this day's `initialTime`
     * and next day's `finalTime`
     */
    fun isTimeBetweenTwoTimes(
        initialTime: Long,
        finalTime: Long,
        currentTime: Long
    ): Boolean {
        var normalizedFinalTime = finalTime
        var normalizedCurrentTime = currentTime
        if (normalizedFinalTime < initialTime) {
            normalizedFinalTime += TimeUnit.DAYS.toMinutes(1)
        }

        if (normalizedCurrentTime < initialTime) {
            normalizedCurrentTime += TimeUnit.DAYS.toMinutes(1)
        }

        return normalizedCurrentTime in initialTime..<normalizedFinalTime
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
        return System.currentTimeMillis() - time.toLong() * 1_000 > TimeUnit.DAYS.toMillis(14)
    }

    fun timeInSecondsMoreThanTwoHoursAgo(time: Int): Boolean {
        return System.currentTimeMillis() - time.toLong() * 1_000 > TimeUnit.HOURS.toMillis(2)
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

        val uri = Uri.parse(href)

        // Validate the scheme (http or https)
        val scheme = uri.scheme
        if ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)) {
            // Validate the host and path
            if ("news.ycombinator.com".equals(
                    uri.host,
                    ignoreCase = true
                ) && "/item" == uri.path
            ) {
                val id = parseHackerNewsItemId(
                    uri.getQueryParameter("id")
                )
                if (id > 0) {
                    var scrollToCommentId = -1
                    val parsedFragment =
                        parseHackerNewsItemId(uri.fragment)
                    if (parsedFragment > 0) {
                        scrollToCommentId = parsedFragment
                    }
                    openCommentsActivity(
                        id,
                        scrollToCommentId,
                        context
                    )
                    return
                }
            }
        }

        launchCustomTab(context, href)
    }

    private fun parseHackerNewsItemId(value: String?): Int =
        value?.takeIf(TextUtils::isDigitsOnly)?.toIntOrNull()?.takeIf { it > 0 } ?: -1

    fun getHackerNewsItemUriFromText(text: String?): Uri? {
        if (text == null) return null

        val matcher = HN_ITEM_URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val url =
                trimTrailingUrlPunctuation(matcher.group())
            val uri = Uri.parse(url)
            if (isHackerNewsItemUri(uri)) {
                return uri
            }
        }

        return null
    }

    fun isHackerNewsItemUri(uri: Uri?): Boolean {
        if (uri == null) return false

        val scheme = uri.scheme
        if (!"http".equals(scheme, ignoreCase = true) && !"https".equals(
                scheme,
                ignoreCase = true
            )
        ) return false
        if (!"news.ycombinator.com".equals(uri.host, ignoreCase = true)) return false
        if ("/item" != uri.path) return false

        val sId = uri.getQueryParameter("id")
        return !sId.isNullOrEmpty() && TextUtils.isDigitsOnly(sId)
    }

    private fun trimTrailingUrlPunctuation(url: String): String =
        url.trimEnd { it in ".,;:)]" }

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
            return SummaryManager.canAttemptLocalSummarization()
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
        SummaryManager.canAttemptLocalSummarization() || AiSummaryApiKeyStore.hasApiKey(ctx)

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun linkify(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        if (!input.contains("http:") && !input.contains("https:")) return input

        // Existing <a>...</a> blocks: keep as-is
        val out = StringBuilder(input.length)
        val a = LINKIFY_ANCHOR_PATTERN.matcher(input)
        var idx = 0

        // Helper-like inline blocks only
        while (a.find()) {
            val segment = input.substring(idx, a.start())
            val m = LINKIFY_URL_PATTERN.matcher(segment)
            val sb = StringBuffer(segment.length)

            while (m.find()) {
                val rep = createLinkReplacement(
                    m,
                    LINKIFY_TRAILING_PUNCTUATION
                )
                m.appendReplacement(sb, Matcher.quoteReplacement(rep))
            }
            m.appendTail(sb)
            out.append(sb)

            // Keep existing anchor untouched
            out.append(a.group())
            idx = a.end()
        }

        // Tail after last <a>
        val segment = input.substring(idx)
        val m = LINKIFY_URL_PATTERN.matcher(segment)
        val sb = StringBuffer(segment.length)
        while (m.find()) {
            val rep = createLinkReplacement(
                m,
                LINKIFY_TRAILING_PUNCTUATION
            )
            m.appendReplacement(sb, Matcher.quoteReplacement(rep))
        }
        m.appendTail(sb)
        out.append(sb)

        return out.toString()
    }

    private fun createLinkReplacement(matcher: Matcher, trailing: String): String {
        val url = matcher.group()

        // Trim common trailing punctuation
        var end = url.length
        while (end > 0 && url[end - 1] in trailing) end--

        // Balance unmatched ')'
        if (end > 0 && url[end - 1] == ')') {
            var opens = 0
            var closes = 0
            for (i in 0..<end) {
                val c = url[i]
                if (c == '(') opens++
                else if (c == ')') closes++
            }
            if (closes > opens) end--
        }

        val core = url.substring(0, end)
        val rest = url.substring(end)

        // Normalize HTML-escaped slashes in the URL for href and text
        val normalized = core
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")

        return "<a href=\"$normalized\">$normalized</a>$rest"
    }

    fun expandShortenedAnchorText(inputHtml: String?): String? {
        if (inputHtml.isNullOrEmpty() || !inputHtml.contains("<a")) {
            return inputHtml
        }

        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        val links = document.select("a[href]")

        for (link in links) {
            val href = link.attr("href")
            val linkText = link.text()

            val decodedHref = Ksoup.parse(href).text()
            val decodedLinkText = Ksoup.parse(linkText).text()

            if (decodedLinkText.endsWith("...")) {
                val linkTextPrefix = decodedLinkText.substring(0, decodedLinkText.length - 3)
                if (decodedHref.startsWith(linkTextPrefix)) {
                    link.text(decodedHref)
                }
            }
        }

        return document.body().html()
    }
}
