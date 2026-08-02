package com.simon.harmonichackernews.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.SummaryManager
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.net.URI
import java.text.NumberFormat
import java.util.Collections
import java.util.Iterator
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements

object Utils {
    private val HN_ITEM_URL_PATTERN: java.util.regex.Pattern = java.util.regex.Pattern.compile(
        "https?://news\\.ycombinator\\.com/item\\?[^\\s<>\"']+",
        java.util.regex.Pattern.CASE_INSENSITIVE
    )
    private val LINKIFY_ANCHOR_PATTERN: java.util.regex.Pattern =
        java.util.regex.Pattern.compile("(?is)<a\\b[^>]*>.*?</a>")
    private val LINKIFY_URL_PATTERN: java.util.regex.Pattern = java.util.regex.Pattern.compile(
        ("(https?:(?:/{1}|(?:&#x2F;)|(?:&#47;))"
                + "(?:/{1}|(?:&#x2F;)|(?:&#47;))"
                + "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)")
    )
    private const val LINKIFY_TRAILING_PUNCTUATION = ".,;:!?)"

    private const val SECOND_MILLIS: kotlin.Long = 1000
    private val MINUTE_MILLIS = 60 * com.simon.harmonichackernews.utils.Utils.SECOND_MILLIS
    private val HOUR_MILLIS = 60 * com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS
    private val DAY_MILLIS = 24 * com.simon.harmonichackernews.utils.Utils.HOUR_MILLIS
    private val YEAR_MILLIS = 365 * com.simon.harmonichackernews.utils.Utils.DAY_MILLIS

    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL"
    const val KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET"
    const val KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS"
    private const val MAX_CACHED_STORIES = 200
    val MAX_CACHED_ARTICLE_BYTES: kotlin.Long = 5L * 1024L * 1024L
    private const val STORY_CACHE_DIR = "story_cache"
    private const val STORY_CACHE_FULL_DIR = "full"
    private const val STORY_CACHE_SUMMARY_DIR = "summary"
    private const val STORY_CACHE_FILE_SUFFIX = ".json"
    const val GLOBAL_SHARED_PREFERENCES_KEY: kotlin.String =
        "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY"

    const val KEY_SHARED_PREFERENCES_BOOKMARKS: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS"
    const val KEY_SHARED_PREFERENCES_USER_TAGS: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
    const val KEY_SHARED_PREFERENCES_FIRST_TIME: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME"
    const val KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN"
    const val KEY_SHARED_PREFERENCES_LAST_VERSION: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION"
    const val KEY_SHARED_PREFERENCES_FAVORITES: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITES"
    const val KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS"
    const val KEY_SHARED_PREFERENCES_UPVOTED: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED"
    const val KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS: kotlin.String =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS"

    const val KEY_NIGHTTIME_FROM_HOUR: kotlin.String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_HOUR"
    const val KEY_NIGHTTIME_FROM_MINUTE: kotlin.String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_MINUTE"
    const val KEY_NIGHTTIME_TO_HOUR: kotlin.String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_HOUR"
    const val KEY_NIGHTTIME_TO_MINUTE: kotlin.String =
        "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_MINUTE"

    const val URL_TOP: kotlin.String = "https://hacker-news.firebaseio.com/v0/topstories.json"
    const val URL_NEW: kotlin.String = "https://hacker-news.firebaseio.com/v0/newstories.json"
    const val URL_BEST: kotlin.String = "https://hacker-news.firebaseio.com/v0/beststories.json"
    const val URL_ASK: kotlin.String = "https://hacker-news.firebaseio.com/v0/askstories.json"
    const val URL_SHOW: kotlin.String = "https://hacker-news.firebaseio.com/v0/showstories.json"
    const val URL_JOBS: kotlin.String = "https://hacker-news.firebaseio.com/v0/jobstories.json"

    @kotlin.concurrent.Volatile
    var adservers: AdHostBlocklist = AdHostBlocklist.empty()
    private val adserversLoading = java.util.concurrent.atomic.AtomicBoolean(false)

    fun log(s: kotlin.String) {
        android.util.Log.d("HARMONIC_TAG", s)
    }

    fun log(i: kotlin.Long) {
        android.util.Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(i: Int) {
        android.util.Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(i: kotlin.Float) {
        android.util.Log.d("HARMONIC_TAG", i.toString())
    }

    fun log(b: kotlin.Boolean) {
        android.util.Log.d("HARMONIC_TAG", b.toString())
    }

    fun toast(s: kotlin.String?, ctx: android.content.Context?) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    }

    @kotlin.Throws(java.lang.Exception::class)
    fun getDomainName(url: kotlin.String): kotlin.String {
        var url = url
        val commonHttpDomain = com.simon.harmonichackernews.utils.Utils.getCommonHttpDomain(url)
        if (commonHttpDomain != null) {
            return commonHttpDomain
        }

        if (url.endsWith("#")) {
            url = url.substring(0, url.length - 1)
        }
        val uri = java.net.URI(url)
        val domain = uri.getHost()
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    private fun getCommonHttpDomain(url: kotlin.String?): kotlin.String? {
        // Story URLs overwhelmingly use this shape. Keep uncommon authorities and malformed
        // URLs on the URI parser so the fast path does not broaden the accepted input.
        if (url == null) {
            return null
        }

        val hostStart: Int
        if (url.startsWith("https://")) {
            hostStart = 8
        } else if (url.startsWith("http://")) {
            hostStart = 7
        } else {
            return null
        }

        val length = url.length
        if (hostStart >= length || !com.simon.harmonichackernews.utils.Utils.hasOnlyCommonUriCharacters(
                url
            )
        ) {
            return null
        }

        var authorityEnd = length
        for (i in hostStart..<length) {
            val character = url.get(i)
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
            val character = url.get(i)
            if (character == '@' || character == '[' || character == ']') {
                return null
            }
            if (character == ':') {
                hostEnd = i
                if (i + 1 >= authorityEnd) {
                    return null
                }
                for (portIndex in i + 1..<authorityEnd) {
                    val portCharacter = url.get(portIndex)
                    if (portCharacter < '0' || portCharacter > '9') {
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
            val character = url.get(i)
            if (character == '.') {
                if (i == labelStart || url.get(i - 1) == '-') {
                    return null
                }
                labelStart = i + 1
                continue
            }

            val isLetter = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
            val isDigit = character >= '0' && character <= '9'
            if (!isLetter && !isDigit && character != '-') {
                return null
            }
            if (i == labelStart && character == '-') {
                return null
            }
            containsLetter = containsLetter or isLetter
        }

        val finalHostCharacter = url.get(hostEnd - 1)
        if (finalHostCharacter == '-' || !containsLetter) {
            return null
        }

        val domain = url.substring(hostStart, hostEnd)
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    private fun hasOnlyCommonUriCharacters(url: kotlin.String): kotlin.Boolean {
        var sawFragment = false
        var i = 0
        while (i < url.length) {
            val character = url.get(i)
            val unreserved = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_' || character == '~'
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
            if (character != '%' || i + 2 >= url.length || !com.simon.harmonichackernews.utils.Utils.isHexDigit(
                    url.get(i + 1)
                ) || !com.simon.harmonichackernews.utils.Utils.isHexDigit(url.get(i + 2))
            ) {
                return false
            }
            i += 2
            i++
        }
        return true
    }

    private fun isHexDigit(character: Char): kotlin.Boolean {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F')
    }

    fun formatDomainNameForDisplay(
        domain: kotlin.String?,
        includeTopLevelDomain: kotlin.Boolean
    ): kotlin.String? {
        if (includeTopLevelDomain || TextUtils.isEmpty(domain)) {
            return domain
        }

        val lastDotIndex = domain!!.lastIndexOf('.')
        if (lastDotIndex <= 0) {
            return domain
        }

        return domain.substring(0, lastDotIndex)
    }

    fun loadAdservers(resources: android.content.res.Resources) {
        if (!com.simon.harmonichackernews.utils.Utils.adservers.isEmpty || !com.simon.harmonichackernews.utils.Utils.adserversLoading.compareAndSet(
                false,
                true
            )
        ) {
            return
        }
        val r: java.lang.Runnable = object : java.lang.Runnable {
            override fun run() {
                try {
                    val blocklist = AdHostBlocklist.read(
                        resources.openRawResource(R.raw.adblockserverlist)
                    )
                    if (!blocklist.isEmpty) {
                        com.simon.harmonichackernews.utils.Utils.adservers = blocklist
                    }
                } catch (e: java.io.IOException) {
                    android.util.Log.e("HARMONIC_TAG", "Failed to load ad host blocklist", e)
                } finally {
                    com.simon.harmonichackernews.utils.Utils.adserversLoading.set(false)
                }
            }
        }
        AsyncTask.execute(r)
    }

    fun cacheStory(ctx: android.content.Context?, id: Int, data: kotlin.String?) {
        if (ctx == null || id <= 0 || TextUtils.isEmpty(data) || JSONParser.ALGOLIA_ERROR_STRING == data) {
            return
        }

        com.simon.harmonichackernews.utils.Utils.writeCachedStoryFiles(ctx, id, data)

        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        var cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        com.simon.harmonichackernews.utils.Utils.addCachedStoryIndexEntry(
            cachedStories,
            id,
            java.lang.System.currentTimeMillis()
        )
        com.simon.harmonichackernews.utils.Utils.evictOldCachedStories(ctx, cachedStories)

        sharedPreferences.edit()
            .putStringSet(
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS,
                cachedStories
            )
            .apply()
    }

    fun loadCachedStory(ctx: android.content.Context?, id: Int): kotlin.String? {
        if (ctx == null || id <= 0) {
            return null
        }

        return com.simon.harmonichackernews.utils.Utils.readStringFromFile(
            com.simon.harmonichackernews.utils.Utils.getCachedStoryFullFile(
                ctx,
                id
            )
        )
    }

    fun loadCachedStorySummary(ctx: android.content.Context?, story: Story?): kotlin.Boolean {
        if (ctx == null || story == null || story.id <= 0) {
            return false
        }

        var summary = com.simon.harmonichackernews.utils.Utils.readStringFromFile(
            com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryFile(
                ctx,
                story.id
            )
        )
        if (TextUtils.isEmpty(summary)) {
            val fullStory = com.simon.harmonichackernews.utils.Utils.readStringFromFile(
                com.simon.harmonichackernews.utils.Utils.getCachedStoryFullFile(
                    ctx,
                    story.id
                )
            )
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, story.id)
            if (!TextUtils.isEmpty(summary)) {
                com.simon.harmonichackernews.utils.Utils.writeStringToFile(
                    com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryFile(
                        ctx,
                        story.id
                    ), summary
                )
            }
        }

        return JSONParser.updateStoryWithCachedStorySummary(story, summary)
    }

    fun cacheStoryPreviewState(ctx: android.content.Context?, story: Story?) {
        if (ctx == null || story == null || story.id <= 0 || (!story.previewImageUrlLoaded && TextUtils.isEmpty(
                story.previewImageUrl
            )
                    && !story.faviconTintColorLoaded)
        ) {
            return
        }

        val appContext = ctx.getApplicationContext()
        val previewState: Story = Story()
        previewState.id = story.id
        previewState.previewImageUrl = story.previewImageUrl
        previewState.previewImageUrlLoaded =
            story.previewImageUrlLoaded || !TextUtils.isEmpty(story.previewImageUrl)
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

        AsyncTask.execute(java.lang.Runnable {
            com.simon.harmonichackernews.utils.Utils.writeCachedStoryPreviewState(
                appContext,
                previewState
            )
        })
    }

    fun getCachedPostCount(ctx: android.content.Context?): Int {
        if (ctx == null) {
            return 0
        }

        return com.simon.harmonichackernews.utils.Utils.getCachedPostIds(ctx).size
    }

    fun clearPostCache(ctx: android.content.Context?): Int {
        if (ctx == null) {
            return 0
        }

        val cachedPostIds = com.simon.harmonichackernews.utils.Utils.getCachedPostIds(ctx)
        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        val editor: SharedPreferences.Editor = sharedPreferences.edit()

        for (key in sharedPreferences.getAll().keys) {
            if (key.startsWith(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                editor.remove(key)
            } else if (key.startsWith(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET)) {
                editor.remove(key)
            }
        }

        editor.remove(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS)
            .apply()

        com.simon.harmonichackernews.utils.Utils.deleteFileOrDirectory(
            com.simon.harmonichackernews.utils.Utils.getStoryCacheDir(
                ctx
            )
        )
        com.simon.harmonichackernews.utils.Utils.deleteFileOrDirectory(
            com.simon.harmonichackernews.utils.Utils.getArticleCacheDir(
                ctx
            )
        )

        StoryPreviewImageLoader.clearDiskCache(ctx)

        return cachedPostIds.size
    }

    fun removeStoryFromCaches(ctx: android.content.Context?, id: Int) {
        if (ctx == null || id <= 0) {
            return
        }

        val sharedPreferences: SharedPreferences =
            ctx.getSharedPreferences(
                com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
                android.content.Context.MODE_PRIVATE
            )
        val cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        com.simon.harmonichackernews.utils.Utils.removeCachedStoryIndexEntry(cachedStories, id)

        sharedPreferences.edit()
            .remove(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id)
            .remove(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id)
            .putStringSet(
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS,
                cachedStories
            )
            .apply()

        com.simon.harmonichackernews.utils.Utils.deleteCachedStoryFiles(ctx, id)

        val articleFile = com.simon.harmonichackernews.utils.Utils.getArticleCacheFile(ctx, id)
        if (articleFile.exists() && !articleFile.delete()) {
            articleFile.deleteOnExit()
        }
    }

    private fun getCachedPostIds(ctx: android.content.Context): kotlin.collections.MutableSet<Int?> {
        val cachedPostIds: kotlin.collections.MutableSet<Int?> = java.util.HashSet<Int?>()

        val cachedStories = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        if (cachedStories != null) {
            for (cachedStory in cachedStories) {
                val id =
                    com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryId(cachedStory)
                if (id > 0) {
                    cachedPostIds.add(id)
                }
            }
        }

        val sharedPreferences: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        for (key in sharedPreferences.getAll().keys) {
            if (key.startsWith(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                addCachedPostId(
                    cachedPostIds,
                    key,
                    com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL
                )
            }
        }

        val articleCacheDir = com.simon.harmonichackernews.utils.Utils.getArticleCacheDir(ctx)
        val cachedArticleFiles = articleCacheDir.listFiles()
        if (cachedArticleFiles != null) {
            for (cachedArticleFile in cachedArticleFiles) {
                com.simon.harmonichackernews.utils.Utils.addCachedPostId(
                    cachedPostIds,
                    cachedArticleFile.getName(),
                    "",
                    ".html"
                )
            }
        }

        com.simon.harmonichackernews.utils.Utils.addCachedPostIdsFromStoryCacheDir(
            cachedPostIds,
            com.simon.harmonichackernews.utils.Utils.getCachedStoryFullDir(ctx)
        )
        com.simon.harmonichackernews.utils.Utils.addCachedPostIdsFromStoryCacheDir(
            cachedPostIds,
            com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryDir(ctx)
        )

        return cachedPostIds
    }

    private fun addCachedStoryIndexEntry(
        cachedStories: kotlin.collections.MutableSet<kotlin.String>,
        id: Int,
        time: kotlin.Long
    ) {
        com.simon.harmonichackernews.utils.Utils.removeCachedStoryIndexEntry(cachedStories, id)
        cachedStories.add(id.toString() + "-" + time)
    }

    private fun removeCachedStoryIndexEntry(
        cachedStories: kotlin.collections.MutableSet<kotlin.String>?,
        id: Int
    ) {
        if (cachedStories == null) {
            return
        }

        val iterator = cachedStories.iterator()
        while (iterator.hasNext()) {
            val cached = iterator.next()
            val cachedId =
                com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryId(cached)
            if (cachedId <= 0 || cachedId == id) {
                iterator.remove()
            }
        }
    }

    private fun evictOldCachedStories(
        ctx: android.content.Context,
        cachedStories: kotlin.collections.MutableSet<kotlin.String>
    ) {
        while (cachedStories.size > com.simon.harmonichackernews.utils.Utils.MAX_CACHED_STORIES) {
            var oldestEntry: kotlin.String? = null
            var oldestTime: kotlin.Long = -1
            var oldestId = -1

            for (cachedStory in cachedStories) {
                val id =
                    com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryId(cachedStory)
                val time = com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryTime(
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
                com.simon.harmonichackernews.utils.Utils.deleteCachedStoryFiles(ctx, oldestId)
                com.simon.harmonichackernews.utils.Utils.deleteCachedArticleSnapshot(ctx, oldestId)
            }
        }
    }

    private fun getCachedStoryIndexEntryId(entry: kotlin.String?): Int {
        val idAndDate: kotlin.Array<kotlin.String?> =
            if (entry == null) kotlin.arrayOfNulls<kotlin.String>(0) else entry.split("-".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        if (idAndDate.size != 2) {
            return -1
        }
        try {
            return idAndDate[0]!!.toInt()
        } catch (e: java.lang.NumberFormatException) {
            return -1
        }
    }

    private fun getCachedStoryIndexEntryTime(entry: kotlin.String?): kotlin.Long {
        val idAndDate: kotlin.Array<kotlin.String?> =
            if (entry == null) kotlin.arrayOfNulls<kotlin.String>(0) else entry.split("-".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        if (idAndDate.size != 2) {
            return -1
        }
        try {
            return idAndDate[1]!!.toLong()
        } catch (e: java.lang.NumberFormatException) {
            return -1
        }
    }

    private fun addCachedPostIdsFromStoryCacheDir(
        cachedPostIds: kotlin.collections.MutableSet<Int?>,
        cacheDir: java.io.File
    ) {
        val cachedStoryFiles = cacheDir.listFiles()
        if (cachedStoryFiles == null) {
            return
        }

        for (cachedStoryFile in cachedStoryFiles) {
            com.simon.harmonichackernews.utils.Utils.addCachedPostId(
                cachedPostIds,
                cachedStoryFile.getName(),
                "",
                com.simon.harmonichackernews.utils.Utils.STORY_CACHE_FILE_SUFFIX
            )
        }
    }

    private fun addCachedPostId(
        cachedPostIds: kotlin.collections.MutableSet<Int?>,
        value: kotlin.String,
        prefix: kotlin.String,
        suffix: kotlin.String = ""
    ) {
        if (!value.startsWith(prefix) || !value.endsWith(suffix)) {
            return
        }

        val end = value.length - suffix.length
        try {
            cachedPostIds.add(value.substring(prefix.length, end).toInt())
        } catch (ignored: java.lang.NumberFormatException) {
        }
    }

    private fun writeCachedStoryFiles(ctx: android.content.Context, id: Int, data: kotlin.String?) {
        com.simon.harmonichackernews.utils.Utils.writeStringToFile(
            com.simon.harmonichackernews.utils.Utils.getCachedStoryFullFile(
                ctx,
                id
            ), data
        )

        val summary: kotlin.String? = JSONParser.compactAlgoliaStoryResponse(data, id)
        if (!TextUtils.isEmpty(summary)) {
            com.simon.harmonichackernews.utils.Utils.writeStringToFile(
                com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryFile(
                    ctx,
                    id
                ), summary
            )
        }
    }

    private fun writeCachedStoryPreviewState(ctx: android.content.Context, previewState: Story) {
        val summaryFile =
            com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryFile(ctx, previewState.id)
        if (!summaryFile.exists()) {
            return
        }

        var summary = com.simon.harmonichackernews.utils.Utils.readStringFromFile(summaryFile)
        if (TextUtils.isEmpty(summary)) {
            val fullStory = com.simon.harmonichackernews.utils.Utils.readStringFromFile(
                com.simon.harmonichackernews.utils.Utils.getCachedStoryFullFile(
                    ctx,
                    previewState.id
                )
            )
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, previewState.id)
        }

        val updatedSummary: kotlin.String? =
            JSONParser.updateCachedStorySummaryPreviewState(summary, previewState)
        if (!TextUtils.isEmpty(updatedSummary) && !TextUtils.equals(summary, updatedSummary)) {
            com.simon.harmonichackernews.utils.Utils.writeStringToFile(summaryFile, updatedSummary)
        }
    }

    private fun getStoryCacheDir(ctx: android.content.Context): java.io.File {
        return java.io.File(
            ctx.getFilesDir(),
            com.simon.harmonichackernews.utils.Utils.STORY_CACHE_DIR
        )
    }

    private fun getCachedStoryFullDir(ctx: android.content.Context): java.io.File {
        return java.io.File(
            com.simon.harmonichackernews.utils.Utils.getStoryCacheDir(ctx),
            com.simon.harmonichackernews.utils.Utils.STORY_CACHE_FULL_DIR
        )
    }

    private fun getCachedStorySummaryDir(ctx: android.content.Context): java.io.File {
        return java.io.File(
            com.simon.harmonichackernews.utils.Utils.getStoryCacheDir(ctx),
            com.simon.harmonichackernews.utils.Utils.STORY_CACHE_SUMMARY_DIR
        )
    }

    private fun getCachedStoryFullFile(ctx: android.content.Context, id: Int): java.io.File {
        return java.io.File(
            com.simon.harmonichackernews.utils.Utils.getCachedStoryFullDir(ctx),
            id.toString() + com.simon.harmonichackernews.utils.Utils.STORY_CACHE_FILE_SUFFIX
        )
    }

    private fun getCachedStorySummaryFile(ctx: android.content.Context, id: Int): java.io.File {
        return java.io.File(
            com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryDir(ctx),
            id.toString() + com.simon.harmonichackernews.utils.Utils.STORY_CACHE_FILE_SUFFIX
        )
    }

    private fun deleteCachedStoryFiles(ctx: android.content.Context, id: Int) {
        val fullFile = com.simon.harmonichackernews.utils.Utils.getCachedStoryFullFile(ctx, id)
        if (fullFile.exists() && !fullFile.delete()) {
            fullFile.deleteOnExit()
        }

        val summaryFile =
            com.simon.harmonichackernews.utils.Utils.getCachedStorySummaryFile(ctx, id)
        if (summaryFile.exists() && !summaryFile.delete()) {
            summaryFile.deleteOnExit()
        }
    }

    private fun readStringFromFile(file: java.io.File?): kotlin.String? {
        if (file == null || !file.exists()) {
            return null
        }

        var inputStream: java.io.FileInputStream? = null
        try {
            inputStream = java.io.FileInputStream(file)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8"))
            val builder = java.lang.StringBuilder()
            var line: kotlin.String?
            while ((reader.readLine().also { line = it }) != null) {
                builder.append(line).append('\n')
            }
            return builder.toString()
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            return null
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (ignored: java.io.IOException) {
                }
            }
        }
    }

    private fun writeStringToFile(file: java.io.File?, data: kotlin.String?): kotlin.Boolean {
        if (file == null || TextUtils.isEmpty(data)) {
            return false
        }

        var outputStream: java.io.FileOutputStream? = null
        try {
            val parent = file.getParentFile()
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false
            }
            outputStream = java.io.FileOutputStream(file)
            outputStream.write(data!!.toByteArray(kotlin.text.charset("UTF-8")))
            return true
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            return false
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (ignored: java.io.IOException) {
                }
            }
        }
    }

    fun loadCachedArticleSnapshot(ctx: android.content.Context?, id: Int): kotlin.String? {
        if (ctx == null || id <= 0) {
            return null
        }

        val cacheFile = com.simon.harmonichackernews.utils.Utils.getArticleCacheFile(ctx, id)
        if (!cacheFile.exists()) {
            return null
        }
        if (cacheFile.length() <= 0L || cacheFile.length() > com.simon.harmonichackernews.utils.Utils.MAX_CACHED_ARTICLE_BYTES) {
            com.simon.harmonichackernews.utils.Utils.deleteCachedArticleSnapshot(ctx, id)
            return null
        }
        cacheFile.setLastModified(java.lang.System.currentTimeMillis())

        var inputStream: java.io.FileInputStream? = null
        try {
            inputStream = java.io.FileInputStream(cacheFile)
            var charset = SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id
            )
            if (TextUtils.isEmpty(charset)) {
                charset = "UTF-8"
            }
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(inputStream, charset)
            )
            val builder = java.lang.StringBuilder()
            var line: kotlin.String?
            while ((reader.readLine().also { line = it }) != null) {
                builder.append(line).append('\n')
            }
            return builder.toString()
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            return null
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (ignored: java.io.IOException) {
                }
            }
        }
    }

    fun loadCachedArticleUrl(ctx: android.content.Context?, id: Int): kotlin.String? {
        if (ctx == null || id <= 0) {
            return null
        }
        return SettingsUtils.readStringFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id
        )
    }

    fun deleteCachedArticleSnapshot(ctx: android.content.Context?, id: Int) {
        if (ctx == null || id <= 0) {
            return
        }

        val cacheFile = com.simon.harmonichackernews.utils.Utils.getArticleCacheFile(ctx, id)
        if (cacheFile.exists() && !cacheFile.delete()) {
            cacheFile.deleteOnExit()
        }
        ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
            .edit()
            .remove(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id)
            .remove(com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + id)
            .apply()
    }

    fun getArticleCacheDir(ctx: android.content.Context): java.io.File {
        return java.io.File(ctx.getFilesDir(), "article_cache")
    }

    fun getArticleCacheFile(ctx: android.content.Context, id: Int): java.io.File {
        return java.io.File(
            com.simon.harmonichackernews.utils.Utils.getArticleCacheDir(ctx),
            id.toString() + ".html"
        )
    }

    private fun deleteFileOrDirectory(file: java.io.File?) {
        if (file == null || !file.exists()) {
            return
        }

        if (file.isDirectory()) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    com.simon.harmonichackernews.utils.Utils.deleteFileOrDirectory(child)
                }
            }
        }

        if (!file.delete()) {
            file.deleteOnExit()
        }
    }

    fun hasCachedStories(ctx: android.content.Context): kotlin.Boolean {
        val cached = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        if (cached == null) {
            return false
        }

        val limit = java.lang.System.currentTimeMillis() - 24 * 60 * 60 * 1000
        for (entry in cached) {
            val id = com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryId(entry)
            val time = com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryTime(entry)
            if (id > 0 && time >= limit && com.simon.harmonichackernews.utils.Utils.loadCachedStoryForStoriesList(
                    ctx,
                    id
                ) != null
            ) {
                return true
            }
        }
        return false
    }

    fun loadCachedStories(ctx: android.content.Context): java.util.ArrayList<Story> {
        val cached = SettingsUtils.readStringSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS
        )
        val stories: java.util.ArrayList<Story> = java.util.ArrayList()
        if (cached == null) {
            return stories
        }

        val limit = java.lang.System.currentTimeMillis() - 24 * 60 * 60 * 1000

        val orderedIds: kotlin.collections.MutableList<androidx.core.util.Pair<kotlin.Long, Int>> =
            java.util.ArrayList()

        for (entry in cached) {
            val id = com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryId(entry)
            val time = com.simon.harmonichackernews.utils.Utils.getCachedStoryIndexEntryTime(entry)
            if (id <= 0 || time < 0) continue
            if (time < limit) continue

            orderedIds.add(androidx.core.util.Pair(time, id))
        }

        //dont replace, is there for old API compatibility
        orderedIds.sortBy { it.first }

        for (pair in orderedIds) {
            val story: Story? =
                com.simon.harmonichackernews.utils.Utils.loadCachedStoryForStoriesList(
                    ctx,
                    pair.second
                )
            if (story != null) {
                stories.add(story)
            }
        }

        return stories
    }

    private fun loadCachedStoryForStoriesList(ctx: android.content.Context?, id: Int): Story? {
        val story: Story = Story()
        story.id = id
        var loaded = com.simon.harmonichackernews.utils.Utils.loadCachedStorySummary(ctx, story)
        if (!loaded) {
            val fullStory = com.simon.harmonichackernews.utils.Utils.loadCachedStory(ctx, id)
            val summary: kotlin.String? = JSONParser.compactAlgoliaStoryResponse(fullStory, id)
            loaded = !TextUtils.isEmpty(summary) && JSONParser.updateStoryWithCachedStorySummary(
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
        ctx: android.content.Context,
        sorted: kotlin.Boolean
    ): java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark> {
        return com.simon.harmonichackernews.utils.Utils.loadBookmarks(
            sorted,
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_BOOKMARKS
            )
        )
    }

    fun loadBookmarks(
        sorted: kotlin.Boolean,
        bookmarksString: kotlin.String?
    ): java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark> {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */

        val bookmarks = java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark>()

        if (bookmarksString == null || bookmarksString.isEmpty()) {
            return bookmarks
        }

        val pairs =
            bookmarksString.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairs) {
            val b = com.simon.harmonichackernews.data.Bookmark()
            val info = pair.split("q".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (info.size == 2) {
                b.id = info[0].toInt()
                b.created = info[1].toLong()
                bookmarks.add(b)
            }
        }

        if (sorted) {
            bookmarks.sortByDescending { it.created }
        }

        return bookmarks
    }

    fun isBookmarked(ctx: android.content.Context, id: Int): kotlin.Boolean {
        val bookmarks = com.simon.harmonichackernews.utils.Utils.loadBookmarks(ctx, false)
        for (b in bookmarks) {
            if (b.id == id) {
                return true
            }
        }

        return false
    }

    fun saveBookmarks(
        ctx: android.content.Context,
        bookmarks: java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark>
    ) {
        com.simon.harmonichackernews.utils.Utils.saveBookmarkList(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_BOOKMARKS,
            bookmarks
        )
    }

    private fun saveBookmarkList(
        ctx: android.content.Context,
        key: kotlin.String?,
        bookmarks: java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark>
    ) {
        val sb = java.lang.StringBuilder()
        val size = bookmarks.size

        for (i in 0..<size) {
            val b = bookmarks.get(i)
            sb.append(b.id)
            sb.append("q")
            sb.append(b.created)
            if (i != size - 1) {
                sb.append("-")
            }
        }

        SettingsUtils.saveStringToSharedPreferences(ctx, key, sb.toString())
    }

    fun addBookmark(ctx: android.content.Context, id: Int) {
        if (com.simon.harmonichackernews.utils.Utils.isBookmarked(ctx, id)) {
            return
        }

        val bookmarks = com.simon.harmonichackernews.utils.Utils.loadBookmarks(ctx, false)
        val b = com.simon.harmonichackernews.data.Bookmark()
        b.id = id
        b.created = java.lang.System.currentTimeMillis()
        bookmarks.add(b)
        com.simon.harmonichackernews.utils.Utils.saveBookmarks(ctx, bookmarks)
    }

    fun removeBookmark(ctx: android.content.Context, id: Int) {
        val bookmarks = com.simon.harmonichackernews.utils.Utils.loadBookmarks(ctx, false)

        for (bookmark in bookmarks) {
            if (bookmark.id == id) {
                bookmarks.remove(bookmark)
                break
            }
        }

        com.simon.harmonichackernews.utils.Utils.saveBookmarks(ctx, bookmarks)
    }

    fun loadFavorites(
        ctx: android.content.Context,
        sorted: kotlin.Boolean
    ): java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark> {
        return com.simon.harmonichackernews.utils.Utils.loadSavedItemList(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_FAVORITES,
            sorted
        )
    }

    fun loadUpvoted(
        ctx: android.content.Context,
        sorted: kotlin.Boolean
    ): java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark> {
        return com.simon.harmonichackernews.utils.Utils.loadSavedItemList(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED,
            sorted
        )
    }

    fun loadFavoriteCommentIds(ctx: android.content.Context): kotlin.collections.MutableSet<Int> {
        return SettingsUtils.readIntSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS
        )
    }

    fun loadUpvotedCommentIds(ctx: android.content.Context): kotlin.collections.MutableSet<Int> {
        return SettingsUtils.readIntSetFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS
        )
    }

    private fun loadSavedItemList(
        ctx: android.content.Context,
        key: kotlin.String?,
        sorted: kotlin.Boolean
    ): java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark> {
        val items = com.simon.harmonichackernews.utils.Utils.loadBookmarks(
            false,
            SettingsUtils.readStringFromSharedPreferences(ctx, key)
        )
        if (sorted) {
            items.sortByDescending { it.id }
        }
        return items
    }

    fun isFavorited(ctx: android.content.Context, id: Int): kotlin.Boolean {
        val favorites = com.simon.harmonichackernews.utils.Utils.loadFavorites(ctx, false)
        for (favorite in favorites) {
            if (favorite.id == id) {
                return true
            }
        }

        return false
    }

    fun isUpvoted(ctx: android.content.Context, id: Int, comment: kotlin.Boolean): kotlin.Boolean {
        if (comment) {
            return com.simon.harmonichackernews.utils.Utils.loadUpvotedCommentIds(ctx).contains(id)
        }

        val upvoted = com.simon.harmonichackernews.utils.Utils.loadUpvoted(ctx, false)
        for (item in upvoted) {
            if (item.id == id) {
                return true
            }
        }

        return false
    }

    fun saveFavorites(
        ctx: android.content.Context,
        favorites: java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark>
    ) {
        com.simon.harmonichackernews.utils.Utils.saveBookmarkList(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_FAVORITES,
            favorites
        )
    }

    fun saveFavoriteIds(ctx: android.content.Context, ids: kotlin.collections.MutableList<Int>) {
        com.simon.harmonichackernews.utils.Utils.saveSavedItemIds(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_FAVORITES,
            ids
        )
    }

    fun saveFavoriteCommentIds(
        ctx: android.content.Context,
        ids: kotlin.collections.MutableSet<Int>
    ) {
        SettingsUtils.saveIntSetToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS,
            ids
        )
    }

    fun saveUpvotedIds(ctx: android.content.Context, ids: kotlin.collections.MutableList<Int>) {
        com.simon.harmonichackernews.utils.Utils.saveSavedItemIds(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED,
            ids
        )
    }

    fun saveUpvotedCommentIds(
        ctx: android.content.Context,
        ids: kotlin.collections.MutableSet<Int>
    ) {
        SettingsUtils.saveIntSetToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS,
            ids
        )
    }

    private fun saveSavedItemIds(
        ctx: android.content.Context,
        key: kotlin.String?,
        ids: kotlin.collections.MutableList<Int>
    ) {
        val items = java.util.ArrayList<com.simon.harmonichackernews.data.Bookmark>()
        val seenIds: kotlin.collections.MutableSet<Int> = java.util.HashSet()
        val now = java.lang.System.currentTimeMillis()

        for (id in ids) {
            if (!seenIds.add(id)) {
                continue
            }

            val item = com.simon.harmonichackernews.data.Bookmark()
            item.id = id
            item.created = now - items.size
            items.add(item)
        }

        com.simon.harmonichackernews.utils.Utils.saveBookmarkList(ctx, key, items)
    }

    fun addFavorite(ctx: android.content.Context, id: Int) {
        if (com.simon.harmonichackernews.utils.Utils.isFavorited(ctx, id)) {
            return
        }

        val favorites = com.simon.harmonichackernews.utils.Utils.loadFavorites(ctx, false)
        val favorite = com.simon.harmonichackernews.data.Bookmark()
        favorite.id = id
        favorite.created = java.lang.System.currentTimeMillis()
        favorites.add(favorite)
        com.simon.harmonichackernews.utils.Utils.saveFavorites(ctx, favorites)
    }

    fun setFavorite(ctx: android.content.Context, id: Int, favorite: kotlin.Boolean) {
        if (favorite) {
            com.simon.harmonichackernews.utils.Utils.addFavorite(ctx, id)
        } else {
            com.simon.harmonichackernews.utils.Utils.removeFavorite(ctx, id)
        }
    }

    fun removeFavorite(ctx: android.content.Context, id: Int) {
        val favorites = com.simon.harmonichackernews.utils.Utils.loadFavorites(ctx, false)

        for (favorite in favorites) {
            if (favorite.id == id) {
                favorites.remove(favorite)
                break
            }
        }

        com.simon.harmonichackernews.utils.Utils.saveFavorites(ctx, favorites)
    }

    fun setUpvoted(
        ctx: android.content.Context,
        id: Int,
        comment: kotlin.Boolean,
        upvoted: kotlin.Boolean
    ) {
        if (comment) {
            val upvotedCommentIds =
                com.simon.harmonichackernews.utils.Utils.loadUpvotedCommentIds(ctx)
            if (upvoted) {
                upvotedCommentIds.add(id)
            } else {
                upvotedCommentIds.remove(id)
            }
            com.simon.harmonichackernews.utils.Utils.saveUpvotedCommentIds(ctx, upvotedCommentIds)
            return
        }

        val upvotedItems = com.simon.harmonichackernews.utils.Utils.loadUpvoted(ctx, false)
        for (item in upvotedItems) {
            if (item.id == id) {
                if (!upvoted) {
                    upvotedItems.remove(item)
                    com.simon.harmonichackernews.utils.Utils.saveBookmarkList(
                        ctx,
                        com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED,
                        upvotedItems
                    )
                }
                return
            }
        }

        if (upvoted) {
            val item = com.simon.harmonichackernews.data.Bookmark()
            item.id = id
            item.created = java.lang.System.currentTimeMillis()
            upvotedItems.add(item)
            com.simon.harmonichackernews.utils.Utils.saveBookmarkList(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_UPVOTED,
                upvotedItems
            )
        }
    }

    fun getThousandSeparatedString(n: Int): kotlin.String {
        val bd = java.math.BigDecimal(n)
        val formatter = java.text.NumberFormat.getInstance(java.util.Locale("en_US"))

        return formatter.format(bd.toLong())
    }

    fun getFilterWords(ctx: android.content.Context): java.util.ArrayList<kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.getCommaSeparatedPreference(
            ctx,
            "pref_filter"
        )
    }

    fun getFilterDomains(ctx: android.content.Context): java.util.ArrayList<kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.getCommaSeparatedPreference(
            ctx,
            "pref_filter_domains"
        )
    }

    fun getFilteredUsers(ctx: android.content.Context): kotlin.collections.MutableSet<kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.getCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            true
        )
    }

    fun removeFilteredUser(ctx: android.content.Context, username: kotlin.String?): kotlin.Boolean {
        if (TextUtils.isEmpty(username)) return false

        val users = com.simon.harmonichackernews.utils.Utils.getFilteredUsers(ctx)
        users.remove(username!!.lowercase(java.util.Locale.getDefault()).trim { it <= ' ' })
        com.simon.harmonichackernews.utils.Utils.saveCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            users
        )

        return true
    }

    fun addFilteredUser(ctx: android.content.Context, username: kotlin.String?): kotlin.Boolean {
        if (TextUtils.isEmpty(username)) return false

        val users = com.simon.harmonichackernews.utils.Utils.getFilteredUsers(ctx)
        users.add(username!!.lowercase(java.util.Locale.getDefault()).trim { it <= ' ' })
        com.simon.harmonichackernews.utils.Utils.saveCommaSeparatedPreferenceSet(
            ctx,
            "pref_filter_users",
            users
        )

        return true
    }

    private fun getCommaSeparatedPreference(
        ctx: android.content.Context,
        key: kotlin.String?
    ): java.util.ArrayList<kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.getCommaSeparatedPreference(ctx, key, false)
    }

    private fun getCommaSeparatedPreference(
        ctx: android.content.Context,
        key: kotlin.String?,
        lowercase: kotlin.Boolean
    ): java.util.ArrayList<kotlin.String> {
        val prefs: SharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        var prefText: kotlin.String? = prefs.getString(key, null)

        val values = java.util.ArrayList<kotlin.String>()
        if (!TextUtils.isEmpty(prefText)) {
            if (lowercase) {
                prefText = prefText!!.lowercase(java.util.Locale.getDefault())
            }
            for (value in prefText!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                values.add(value.trim { it <= ' ' })
            }
        }
        return values
    }

    private fun getCommaSeparatedPreferenceSet(
        ctx: android.content.Context,
        key: kotlin.String?,
        lowercase: kotlin.Boolean
    ): kotlin.collections.MutableSet<kotlin.String> {
        return java.util.HashSet<kotlin.String>(
            com.simon.harmonichackernews.utils.Utils.getCommaSeparatedPreference(
                ctx,
                key,
                lowercase
            )
        )
    }

    private fun saveCommaSeparatedPreferenceSet(
        ctx: android.content.Context,
        key: kotlin.String?,
        values: kotlin.collections.MutableSet<kotlin.String>
    ) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(key, com.simon.harmonichackernews.utils.Utils.joinCommaSeparated(values))
            .apply()
    }

    private fun joinCommaSeparated(values: kotlin.collections.MutableSet<kotlin.String>): kotlin.String {
        val sb = java.lang.StringBuilder()
        val iter = values.iterator()
        while (iter.hasNext()) {
            sb.append(iter.next())
            if (iter.hasNext()) {
                sb.append(",")
            }
        }
        return sb.toString()
    }

    fun getUserTags(ctx: android.content.Context): kotlin.collections.MutableMap<kotlin.String, kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.readUserTags(ctx, true)
    }

    fun getUserTagsWithOriginalUsernames(ctx: android.content.Context): kotlin.collections.MutableMap<kotlin.String, kotlin.String> {
        return com.simon.harmonichackernews.utils.Utils.readUserTags(ctx, false)
    }

    private fun readUserTags(
        ctx: android.content.Context,
        normalizeUsernames: kotlin.Boolean
    ): kotlin.collections.MutableMap<kotlin.String, kotlin.String> {
        val jsonString = SettingsUtils.readStringFromSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_USER_TAGS
        )
        val map: kotlin.collections.MutableMap<kotlin.String, kotlin.String> = java.util.HashMap()
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                val obj: JSONObject = JSONObject(jsonString)
                val keys: kotlin.collections.MutableIterator<kotlin.String> = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value: kotlin.String = obj.optString(key, "")
                    val username = key.trim { it <= ' ' }
                    map.put(
                        if (normalizeUsernames) username.lowercase(java.util.Locale.getDefault()) else username,
                        value
                    )
                }
            } catch (e: JSONException) {
                // Invalid JSON in prefs; just start fresh
                e.printStackTrace()
            }
        }
        return map
    }

    fun getUserTag(ctx: android.content.Context, username: kotlin.String?): kotlin.String {
        if (TextUtils.isEmpty(username)) return ""
        val map = com.simon.harmonichackernews.utils.Utils.getUserTags(ctx)
        val tag = map.get(username!!.lowercase(java.util.Locale.getDefault()).trim { it <= ' ' })
        return if (tag == null) "" else tag
    }

    fun setUserTag(ctx: android.content.Context, username: kotlin.String?, tag: kotlin.String?) {
        if (TextUtils.isEmpty(username)) return
        val map = com.simon.harmonichackernews.utils.Utils.getUserTagsWithOriginalUsernames(ctx)
        val key = username!!.trim { it <= ' ' }
        val savedUsernames: kotlin.collections.MutableIterator<kotlin.String> = map.keys.iterator()
        while (savedUsernames.hasNext()) {
            val savedUsername = savedUsernames.next()
            if (savedUsername.equals(key, ignoreCase = true)) {
                savedUsernames.remove()
            }
        }
        if (!TextUtils.isEmpty(tag)) {
            map.put(key, tag!!.trim { it <= ' ' })
        }
        // Convert back to JSON
        val obj: JSONObject = JSONObject()
        for (e in map.entries) {
            try {
                obj.put(e.key, e.value)
            } catch (ex: JSONException) {
                ex.printStackTrace()
            }
        }
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_USER_TAGS,
            obj.toString()
        )
    }

    fun shouldShowWelcomeDialog(ctx: android.content.Context): kotlin.Boolean {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        return !sharedPref.getBoolean(
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN,
            false
        )
    }

    fun markWelcomeDialogShown(ctx: android.content.Context) {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        sharedPref.edit().putBoolean(
            com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN,
            true
        ).apply()
    }

    fun justUpdated(ctx: android.content.Context): kotlin.Boolean {
        val sharedPref: SharedPreferences = ctx.getSharedPreferences(
            com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            android.content.Context.MODE_PRIVATE
        )
        if (com.simon.harmonichackernews.BuildConfig.VERSION_CODE > sharedPref.getInt(
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_LAST_VERSION,
                -1
            )
        ) {
            sharedPref.edit().putInt(
                com.simon.harmonichackernews.utils.Utils.KEY_SHARED_PREFERENCES_LAST_VERSION,
                com.simon.harmonichackernews.BuildConfig.VERSION_CODE
            ).apply()
            return true
        }
        return false
    }

    fun getTimeAgo(time: kotlin.Long): kotlin.String {
        var time = time
        if (time < 1000000000000L) {
            // if timestamp given in seconds, convert to millis
            time *= 1000
        }

        val now = java.lang.System.currentTimeMillis()
        if (time > now || time <= 0) {
            return "?"
        }

        val diff = now - time
        if (diff < com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS) {
            return "just now"
        } else if (diff < 2 * com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS) {
            return "1m"
        } else if (diff < 50 * com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS) {
            return (diff / com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS).toString() + "m"
        } else if (diff < 120 * com.simon.harmonichackernews.utils.Utils.MINUTE_MILLIS) {
            return "1h"
        } else if (diff < 24 * com.simon.harmonichackernews.utils.Utils.HOUR_MILLIS) {
            return (diff / com.simon.harmonichackernews.utils.Utils.HOUR_MILLIS).toString() + "h"
        } else if (diff < 48 * com.simon.harmonichackernews.utils.Utils.HOUR_MILLIS) {
            return "1d"
        } else if (diff < 365 * com.simon.harmonichackernews.utils.Utils.DAY_MILLIS) {
            return (diff / com.simon.harmonichackernews.utils.Utils.DAY_MILLIS).toString() + "d"
        } else if (diff < 2 * com.simon.harmonichackernews.utils.Utils.YEAR_MILLIS) {
            return "1y"
        } else {
            return (diff / com.simon.harmonichackernews.utils.Utils.YEAR_MILLIS).toString() + "y"
        }
    }

    fun isOnWiFi(ctx: android.content.Context): kotlin.Boolean {
        val connectivityManager: ConnectivityManager =
            ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: android.net.Network? = connectivityManager.getActiveNetwork()
        if (network == null) {
            return false
        }
        val networkCapabilities: NetworkCapabilities? =
            connectivityManager.getNetworkCapabilities(network)

        return networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @kotlin.jvm.JvmOverloads
    fun launchCustomTab(
        ctx: android.content.Context,
        url: kotlin.String?,
        shareable: kotlin.Boolean = true
    ) {
        var url = url
        if (url != null) {
            if (SettingsUtils.shouldUseExternalBrowser(ctx) || !com.simon.harmonichackernews.utils.Utils.isCustomTabSupported(
                    ctx
                )
            ) {
                com.simon.harmonichackernews.utils.Utils.launchInExternalBrowser(ctx, url)
            } else {
                try {
                    val builder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
                    builder.setShareState(if (shareable) CustomTabsIntent.SHARE_STATE_ON else CustomTabsIntent.SHARE_STATE_OFF)

                    val colorBuilder: CustomTabColorSchemeParams.Builder =
                        CustomTabColorSchemeParams.Builder()
                    colorBuilder.setToolbarColor(
                        ContextCompat.getColor(
                            ctx,
                            com.simon.harmonichackernews.utils.ThemeUtils.getBackgroundColorResource(
                                ctx
                            )
                        )
                    )
                    builder.setDefaultColorSchemeParams(colorBuilder.build())

                    val customTabsIntent: CustomTabsIntent = builder.build()

                    customTabsIntent.launchUrl(ctx, android.net.Uri.parse(url))
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()

                    try {
                        val builder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
                        builder.setShareState(if (shareable) CustomTabsIntent.SHARE_STATE_ON else CustomTabsIntent.SHARE_STATE_OFF)

                        val colorBuilder: CustomTabColorSchemeParams.Builder =
                            CustomTabColorSchemeParams.Builder()
                        colorBuilder.setToolbarColor(
                            ContextCompat.getColor(
                                ctx,
                                com.simon.harmonichackernews.utils.ThemeUtils.getBackgroundColorResource(
                                    ctx
                                )
                            )
                        )
                        builder.setDefaultColorSchemeParams(colorBuilder.build())

                        val customTabsIntent: CustomTabsIntent = builder.build()

                        customTabsIntent.launchUrl(
                            ctx,
                            android.net.Uri.parse(android.webkit.URLUtil.guessUrl(url))
                        )
                    } catch (e1: java.lang.Exception) {
                        try {
                            if (!url.startsWith("http://") && !url.startsWith("https://")) url =
                                "http://" + url

                            val builder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
                            builder.setShareState(if (shareable) CustomTabsIntent.SHARE_STATE_ON else CustomTabsIntent.SHARE_STATE_OFF)

                            val colorBuilder: CustomTabColorSchemeParams.Builder =
                                CustomTabColorSchemeParams.Builder()
                            colorBuilder.setToolbarColor(
                                ContextCompat.getColor(
                                    ctx,
                                    com.simon.harmonichackernews.utils.ThemeUtils.getBackgroundColorResource(
                                        ctx
                                    )
                                )
                            )
                            builder.setDefaultColorSchemeParams(colorBuilder.build())

                            val customTabsIntent: CustomTabsIntent = builder.build()

                            customTabsIntent.launchUrl(ctx, android.net.Uri.parse(url))
                        } catch (e2: java.lang.Exception) {
                            com.simon.harmonichackernews.utils.Utils.launchInExternalBrowser(
                                ctx,
                                url
                            )
                        }
                    }
                }
            }
        }
    }

    fun launchInExternalBrowser(ctx: android.content.Context, url: kotlin.String) {
        var url = url
        try {
            com.simon.harmonichackernews.utils.Utils.openExternalUrl(ctx, url)
        } catch (e: java.lang.Exception) {
            // failed for the first time, let's try to guess a fix to the url
            try {
                com.simon.harmonichackernews.utils.Utils.openExternalUrl(
                    ctx,
                    android.webkit.URLUtil.guessUrl(url)
                )
            } catch (e1: java.lang.Exception) {
                // automated fix didn't work, let's try to do it manually
                try {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url =
                        "http://" + url
                    com.simon.harmonichackernews.utils.Utils.openExternalUrl(ctx, url)
                } catch (e2: java.lang.Exception) {
                    Toast.makeText(ctx, "Couldn't open link to: " + url, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openExternalUrl(ctx: android.content.Context, url: kotlin.String?) {
        val browserIntent: Intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        val packageName =
            com.simon.harmonichackernews.utils.Utils.getPackageForExternalUrl(ctx, browserIntent)
        if (packageName != null) {
            browserIntent.setPackage(packageName)
        }
        ctx.startActivity(browserIntent)
    }

    private fun getPackageForExternalUrl(
        ctx: android.content.Context,
        browserIntent: Intent
    ): kotlin.String? {
        val defaultBrowserPackageName = ctx.defaultBrowserPackageName()
        if (defaultBrowserPackageName == null) {
            return null
        }

        val resolveInfo: ResolveInfo? = ctx.getPackageManager()
            .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        var resolvedPackageName: kotlin.String? = null
        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            resolvedPackageName = resolveInfo.activityInfo.packageName
        }

        // force browser only when VIEW resolves to Harmonic itself (self-loop) or a known bad resolver.
        if (ctx.getPackageName() == resolvedPackageName
            || ctx.isInvalidViewHandlerPackage(resolvedPackageName)
        ) {
            return defaultBrowserPackageName
        }

        return null
    }

    fun downloadPDF(context: android.content.Context, pdfUrl: kotlin.String?): kotlin.Boolean {
        val intent: Intent = Intent(Intent.ACTION_VIEW)
        intent.setData(android.net.Uri.parse(pdfUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Check if there's an app that can handle this intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent)
            return true
        }
        return false
    }

    fun isCustomTabSupported(context: android.content.Context): kotlin.Boolean {
        return !com.simon.harmonichackernews.utils.Utils.getCustomTabsPackages(context).isEmpty()
    }

    /**
     * Returns a list of packages that support Custom Tabs.
     */
    fun getCustomTabsPackages(context: android.content.Context): java.util.ArrayList<ResolveInfo?> {
        val pm: PackageManager = context.getPackageManager()
        // Get default VIEW intent handler.
        val activityIntent: Intent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(android.net.Uri.fromParts("http", "", null))

        // Get all apps that can handle VIEW intents.
        val resolvedActivityList: kotlin.collections.MutableList<ResolveInfo> =
            pm.queryIntentActivities(activityIntent, 0)
        val packagesSupportingCustomTabs: java.util.ArrayList<ResolveInfo?> =
            java.util.ArrayList<ResolveInfo?>()
        for (info in resolvedActivityList) {
            val serviceIntent: Intent = Intent()
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION)
            serviceIntent.setPackage(info.activityInfo.packageName)
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info)
            }
        }

        return packagesSupportingCustomTabs
    }

    fun getColorViaAttr(ctx: android.content.Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        val theme = ctx.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    @kotlin.Throws(java.io.IOException::class)
    fun writeInFile(ctx: android.content.Context, uri: android.net.Uri, text: kotlin.String?) {
        val outputStream: java.io.OutputStream?
        outputStream = ctx.getContentResolver().openOutputStream(uri)
        val bw = java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream))
        bw.write(text)
        bw.flush()
        bw.close()
    }

    @kotlin.Throws(java.io.IOException::class)
    fun readFileContent(ctx: android.content.Context, uri: android.net.Uri): kotlin.String {
        val inputStream = ctx.getContentResolver().openInputStream(uri)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        val stringBuilder = java.lang.StringBuilder()
        var currentline: kotlin.String?
        while ((reader.readLine().also { currentline = it }) != null) {
            stringBuilder.append(currentline)
        }
        inputStream!!.close()
        return stringBuilder.toString()
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
        initialTime: kotlin.Long,
        finalTime: kotlin.Long,
        currentTime: kotlin.Long
    ): kotlin.Boolean {
        var finalTime = finalTime
        var currentTime = currentTime
        if (finalTime < initialTime) {
            finalTime += java.util.concurrent.TimeUnit.DAYS.toMinutes(1)
        }

        if (currentTime < initialTime) {
            currentTime += java.util.concurrent.TimeUnit.DAYS.toMinutes(1)
        }

        return initialTime <= currentTime && currentTime < finalTime
    }

    fun setNighttimeHours(
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        ctx: android.content.Context
    ) {
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_FROM_HOUR,
            fromHour.toString() + ""
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_FROM_MINUTE,
            fromMinute.toString() + ""
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_TO_HOUR,
            toHour.toString() + ""
        )
        SettingsUtils.saveStringToSharedPreferences(
            ctx,
            com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_TO_MINUTE,
            toMinute.toString() + ""
        )
    }

    fun getNighttimeHours(ctx: android.content.Context): IntArray {
        return kotlin.intArrayOf(
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_FROM_HOUR,
                "21"
            )!!.toInt(),
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_FROM_MINUTE,
                "0"
            )!!.toInt(),
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_TO_HOUR,
                "6"
            )!!.toInt(),
            SettingsUtils.readStringFromSharedPreferences(
                ctx,
                com.simon.harmonichackernews.utils.Utils.KEY_NIGHTTIME_TO_MINUTE,
                "0"
            )!!.toInt()
        )
    }

    fun timeInSecondsMoreThanTwoWeeksAgo(time: Int): kotlin.Boolean {
        return (java.lang.System.currentTimeMillis() - (time.toLong()) * 1000) / 1000 / 60 / 60 / 24 > 14
    }

    fun timeInSecondsMoreThanTwoHoursAgo(time: Int): kotlin.Boolean {
        return (java.lang.System.currentTimeMillis() - (time.toLong()) * 1000) / 1000 / 60 / 60 > 2
    }

    fun pxFromDp(resources: android.content.res.Resources, dp: kotlin.Float): kotlin.Float {
        return dp * resources.getDisplayMetrics().density
    }

    fun pxFromDpInt(resources: android.content.res.Resources, dp: kotlin.Float): Int {
        return java.lang.Math.round(
            com.simon.harmonichackernews.utils.Utils.pxFromDp(
                resources,
                dp
            )
        )
    }

    fun isTablet(res: android.content.res.Resources): kotlin.Boolean {
        return res.getBoolean(R.bool.is_tablet)
    }

    fun openLinkMaybeHN(context: android.content.Context?, href: kotlin.String?) {
        if (context == null || TextUtils.isEmpty(href)) {
            return
        }

        val uri = android.net.Uri.parse(href)

        // Validate the scheme (http or https)
        val scheme = uri.getScheme()
        if ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)) {
            // Validate the host and path
            if ("news.ycombinator.com".equals(
                    uri.getHost(),
                    ignoreCase = true
                ) && "/item" == uri.getPath()
            ) {
                val id = com.simon.harmonichackernews.utils.Utils.parseHackerNewsItemId(
                    uri.getQueryParameter("id")
                )
                if (id > 0) {
                    var scrollToCommentId = -1
                    val parsedFragment =
                        com.simon.harmonichackernews.utils.Utils.parseHackerNewsItemId(uri.getFragment())
                    if (parsedFragment > 0) {
                        scrollToCommentId = parsedFragment
                    }
                    com.simon.harmonichackernews.utils.Utils.openCommentsActivity(
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

    private fun parseHackerNewsItemId(value: kotlin.String?): Int {
        if (TextUtils.isEmpty(value) || !TextUtils.isDigitsOnly(value)) {
            return -1
        }

        try {
            val id = value!!.toInt()
            return if (id > 0) id else -1
        } catch (ignored: java.lang.NumberFormatException) {
            return -1
        }
    }

    fun getHackerNewsItemUriFromText(text: kotlin.String?): android.net.Uri? {
        if (text == null) return null

        val matcher = com.simon.harmonichackernews.utils.Utils.HN_ITEM_URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val url =
                com.simon.harmonichackernews.utils.Utils.trimTrailingUrlPunctuation(matcher.group())
            val uri = android.net.Uri.parse(url)
            if (com.simon.harmonichackernews.utils.Utils.isHackerNewsItemUri(uri)) {
                return uri
            }
        }

        return null
    }

    fun isHackerNewsItemUri(uri: android.net.Uri?): kotlin.Boolean {
        if (uri == null) return false

        val scheme = uri.getScheme()
        if (!"http".equals(scheme, ignoreCase = true) && !"https".equals(
                scheme,
                ignoreCase = true
            )
        ) return false
        if (!"news.ycombinator.com".equals(uri.getHost(), ignoreCase = true)) return false
        if ("/item" != uri.getPath()) return false

        val sId = uri.getQueryParameter("id")
        return sId != null && !sId.isEmpty() && TextUtils.isDigitsOnly(sId)
    }

    private fun trimTrailingUrlPunctuation(url: kotlin.String): kotlin.String {
        var url = url
        while (!url.isEmpty()) {
            val last = url.get(url.length - 1)
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == ')' || last == ']') {
                url = url.substring(0, url.length - 1)
            } else {
                break
            }
        }
        return url
    }

    fun openCommentsActivity(id: Int, scrollToCommentId: Int, context: android.content.Context) {
        if (context is MainActivity
            && (context as MainActivity).openCommentsItem(id, scrollToCommentId)
        ) {
            return
        }
        val builder = android.net.Uri.parse("https://news.ycombinator.com/item").buildUpon()
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

    fun canProvideSummary(ctx: android.content.Context): kotlin.Boolean {
        val prefs: SharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.contains("pref_ai_summary_enabled")
            && !prefs.getBoolean("pref_ai_summary_enabled", false)
        ) {
            return false
        }
        val mode: kotlin.String = prefs.getString("pref_ai_summary_mode", "cloud") ?: "cloud"
        if ("local" == mode) {
            return SummaryManager.canAttemptLocalSummarization()
        }
        return AiSummaryApiKeyStore.hasApiKey(ctx)
    }

    fun isAiSummaryEnabled(ctx: android.content.Context): kotlin.Boolean {
        val prefs: SharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.contains("pref_ai_summary_enabled")) {
            return prefs.getBoolean("pref_ai_summary_enabled", false)
        }
        return com.simon.harmonichackernews.utils.Utils.isAiSummaryEnabledByDefault(ctx)
    }

    private fun isAiSummaryEnabledByDefault(ctx: android.content.Context): kotlin.Boolean {
        return SummaryManager.canAttemptLocalSummarization()
                || AiSummaryApiKeyStore.hasApiKey(ctx)
    }

    fun isNetworkAvailable(context: android.content.Context): kotlin.Boolean {
        val cm: ConnectivityManager? =
            context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (cm == null) return false

        val net: android.net.Network? = cm.getActiveNetwork()
        if (net == null) return false
        val caps: NetworkCapabilities? = cm.getNetworkCapabilities(net)
        return caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun linkify(input: kotlin.String?): kotlin.String? {
        if (input == null || input.isEmpty()) return input
        if (!input.contains("http:") && !input.contains("https:")) return input

        // Existing <a>...</a> blocks: keep as-is
        val out = java.lang.StringBuilder(input.length)
        val a = com.simon.harmonichackernews.utils.Utils.LINKIFY_ANCHOR_PATTERN.matcher(input)
        var idx = 0

        // Helper-like inline blocks only
        while (a.find()) {
            val segment = input.substring(idx, a.start())
            val m = com.simon.harmonichackernews.utils.Utils.LINKIFY_URL_PATTERN.matcher(segment)
            val sb = java.lang.StringBuffer(segment.length)

            while (m.find()) {
                val rep = com.simon.harmonichackernews.utils.Utils.getString(
                    m,
                    com.simon.harmonichackernews.utils.Utils.LINKIFY_TRAILING_PUNCTUATION
                )
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rep))
            }
            m.appendTail(sb)
            out.append(sb)

            // Keep existing anchor untouched
            out.append(a.group())
            idx = a.end()
        }

        // Tail after last <a>
        val segment = input.substring(idx)
        val m = com.simon.harmonichackernews.utils.Utils.LINKIFY_URL_PATTERN.matcher(segment)
        val sb = java.lang.StringBuffer(segment.length)
        while (m.find()) {
            val rep = com.simon.harmonichackernews.utils.Utils.getString(
                m,
                com.simon.harmonichackernews.utils.Utils.LINKIFY_TRAILING_PUNCTUATION
            )
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rep))
        }
        m.appendTail(sb)
        out.append(sb)

        return out.toString()
    }

    private fun getString(m: java.util.regex.Matcher, trailing: kotlin.String): kotlin.String {
        val u = m.group()

        // Trim common trailing punctuation
        var end = u.length
        while (end > 0 && trailing.indexOf(u.get(end - 1)) >= 0) end--

        // Balance unmatched ')'
        if (end > 0 && u.get(end - 1) == ')') {
            var opens = 0
            var closes = 0
            for (i in 0..<end) {
                val c = u.get(i)
                if (c == '(') opens++
                else if (c == ')') closes++
            }
            if (closes > opens) end--
        }

        val core = u.substring(0, end)
        val rest = u.substring(end)

        // Normalize HTML-escaped slashes in the URL for href and text
        val normalized = core
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")

        val rep = "<a href=\"" + normalized + "\">" + normalized + "</a>" + rest
        return rep
    }

    fun expandShortenedAnchorText(inputHtml: kotlin.String?): kotlin.String? {
        if (inputHtml == null || inputHtml.isEmpty() || !inputHtml.contains("<a")) {
            return inputHtml
        }

        val document: org.jsoup.nodes.Document =
            Jsoup.parse(inputHtml, "", org.jsoup.parser.Parser.htmlParser())
        val links = document.select("a[href]")

        for (link in links) {
            val href = link.attr("href")
            val linkText = link.text()

            val decodedHref: kotlin.String = Jsoup.parse(href).text()
            val decodedLinkText: kotlin.String = Jsoup.parse(linkText).text()

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
