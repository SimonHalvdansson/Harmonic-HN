package com.simon.harmonichackernews.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.simon.harmonichackernews.data.StoryCacheRepository
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Utils {
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
        if (ctx != null) storyCache(ctx).storeStory(id, data, System.currentTimeMillis())
    }

    fun loadCachedStory(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }

        return storyCache(ctx).loadStoryPayload(id)
    }

    fun loadCachedStorySummary(ctx: Context?, story: Story?): Boolean {
        if (ctx == null || story == null || story.id <= 0) {
            return false
        }

        return storyCache(ctx).hydrateStory(story)
    }

    fun getCachedPostCount(ctx: Context?): Int {
        if (ctx == null) {
            return 0
        }

        return storyCache(ctx).cachedItemIds().size
    }

    fun clearPostCache(ctx: Context?): Int {
        if (ctx == null) {
            return 0
        }

        val count = storyCache(ctx).clear()
        StoryPreviewImageLoader.clearDiskCache(ctx)
        return count
    }

    fun removeStoryFromCaches(ctx: Context?, id: Int) {
        if (ctx == null || id <= 0) {
            return
        }

        storyCache(ctx).remove(id)
    }

    fun loadCachedArticleSnapshot(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }

        return storyCache(ctx).loadArticle(id, System.currentTimeMillis())
    }

    fun loadCachedArticleUrl(ctx: Context?, id: Int): String? {
        if (ctx == null || id <= 0) {
            return null
        }
        return storyCache(ctx).articleUrl(id)
    }

    fun getArticleCacheDir(ctx: Context): File {
        return File(ctx.filesDir, "article_cache")
    }

    fun hasCachedStories(ctx: Context): Boolean {
        return storyCache(ctx).hasRecentStories(System.currentTimeMillis())
    }

    fun loadCachedStories(ctx: Context): ArrayList<Story> {
        return ArrayList(storyCache(ctx).recentStories(System.currentTimeMillis()))
    }

    private fun storyCache(context: Context): StoryCacheRepository {
        return AndroidStoryCacheRepositories.get(context)
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

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
