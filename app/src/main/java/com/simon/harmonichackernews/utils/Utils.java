package com.simon.harmonichackernews.utils;

import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.BuildConfig;
import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.SummaryManager;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import androidx.core.util.Pair;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HN_ITEM_URL_PATTERN = Pattern.compile(
            "https?://news\\.ycombinator\\.com/item\\?[^\\s<>\"']+",
            Pattern.CASE_INSENSITIVE);

    private static final long SECOND_MILLIS = 1000;
    private static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    private static final long YEAR_MILLIS = 365 * DAY_MILLIS;

    public final static String KEY_SHARED_PREFERENCES_CACHED_STORY = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORY";
    public final static String KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL";
    public final static String KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS";
    private static final int MAX_CACHED_STORIES = 200;
    private static final String STORY_CACHE_DIR = "story_cache";
    private static final String STORY_CACHE_FULL_DIR = "full";
    private static final String STORY_CACHE_SUMMARY_DIR = "summary";
    private static final String STORY_CACHE_FILE_SUFFIX = ".json";
    public final static String GLOBAL_SHARED_PREFERENCES_KEY = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY";
    private static boolean legacyStoryCacheMigrationScheduled = false;

    public final static String KEY_SHARED_PREFERENCES_BOOKMARKS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS";
    public final static String KEY_SHARED_PREFERENCES_USER_TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS";
    public final static String KEY_SHARED_PREFERENCES_FIRST_TIME = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME";
    public final static String KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN";
    public final static String KEY_SHARED_PREFERENCES_LAST_VERSION = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION";
    public final static String KEY_SHARED_PREFERENCES_FAVORITES = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITES";
    public final static String KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS";
    public final static String KEY_SHARED_PREFERENCES_UPVOTED = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED";
    public final static String KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS";

    public final static String KEY_NIGHTTIME_FROM_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_HOUR";
    public final static String KEY_NIGHTTIME_FROM_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_MINUTE";
    public final static String KEY_NIGHTTIME_TO_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_HOUR";
    public final static String KEY_NIGHTTIME_TO_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_MINUTE";

    public final static String URL_TOP = "https://hacker-news.firebaseio.com/v0/topstories.json";
    public final static String URL_NEW = "https://hacker-news.firebaseio.com/v0/newstories.json";
    public final static String URL_BEST = "https://hacker-news.firebaseio.com/v0/beststories.json";
    public final static String URL_ASK = "https://hacker-news.firebaseio.com/v0/askstories.json";
    public final static String URL_SHOW = "https://hacker-news.firebaseio.com/v0/showstories.json";
    public final static String URL_JOBS = "https://hacker-news.firebaseio.com/v0/jobstories.json";

    public static volatile AdHostBlocklist adservers = AdHostBlocklist.empty();
    private static final AtomicBoolean adserversLoading = new AtomicBoolean(false);

    public static void log(String s) {
        Log.d("HARMONIC_TAG", s);
    }

    public static void log(long i) {
        Log.d("HARMONIC_TAG", String.valueOf(i));
    }

    public static void log(int i) {
        Log.d("HARMONIC_TAG", String.valueOf(i));
    }

    public static void log(float i) {
        Log.d("HARMONIC_TAG", String.valueOf(i));
    }

    public static void log(boolean b) {
        Log.d("HARMONIC_TAG", String.valueOf(b));
    }

    public static void toast(String s, Context ctx) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show();
    }

    public static String getDomainName(String url) throws Exception {
        if (url.endsWith("#")) {
            url = url.substring(0, url.length()-1);
        }
        URI uri = new URI(url);
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    public static String formatDomainNameForDisplay(String domain, boolean includeTopLevelDomain) {
        if (includeTopLevelDomain || TextUtils.isEmpty(domain)) {
            return domain;
        }

        int lastDotIndex = domain.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return domain;
        }

        return domain.substring(0, lastDotIndex);
    }

    public static void loadAdservers(Resources resources) {
        if (!adservers.isEmpty() || !adserversLoading.compareAndSet(false, true)) {
            return;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    AdHostBlocklist blocklist = AdHostBlocklist.read(
                            resources.openRawResource(R.raw.adblockserverlist));
                    if (!blocklist.isEmpty()) {
                        Utils.adservers = blocklist;
                    }
                } catch (IOException e) {
                    Log.e("HARMONIC_TAG", "Failed to load ad host blocklist", e);
                } finally {
                    adserversLoading.set(false);
                }
            }
        };
        AsyncTask.execute(r);
    }

    private static void scheduleLegacyStoryCacheMigration(Context ctx) {
        if (ctx == null) {
            return;
        }

        synchronized (Utils.class) {
            if (legacyStoryCacheMigrationScheduled) {
                return;
            }
            legacyStoryCacheMigrationScheduled = true;
        }

        Context appContext = ctx.getApplicationContext();
        AsyncTask.execute(() -> migrateLegacyStoryCache(appContext));
    }

    private static void migrateLegacyStoryCache(Context ctx) {
        if (ctx == null) {
            return;
        }

        SharedPreferences sharedPreferences = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Map<String, ?> existingValues = sharedPreferences.getAll();
        Set<String> cachedStories = new HashSet<>();
        Object cachedStoriesValue = existingValues.get(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);
        if (cachedStoriesValue instanceof Set) {
            for (Object value : (Set<?>) cachedStoriesValue) {
                if (value instanceof String) {
                    cachedStories.add((String) value);
                }
            }
        }

        long now = System.currentTimeMillis();
        int migratedCount = 0;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (Map.Entry<String, ?> entry : existingValues.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_SHARED_PREFERENCES_CACHED_STORY) || !(entry.getValue() instanceof String)) {
                continue;
            }

            int id;
            try {
                id = Integer.parseInt(key.substring(KEY_SHARED_PREFERENCES_CACHED_STORY.length()));
            } catch (NumberFormatException e) {
                editor.remove(key);
                continue;
            }

            String data = (String) entry.getValue();
            if (!TextUtils.isEmpty(data) && !JSONParser.ALGOLIA_ERROR_STRING.equals(data)) {
                writeCachedStoryFiles(ctx, id, data);
                long existingTime = findCachedStoryIndexEntryTime(cachedStories, id);
                addCachedStoryIndexEntry(cachedStories, id, existingTime >= 0 ? existingTime : now - migratedCount);
                migratedCount++;
            }
            editor.remove(key);
        }

        if (migratedCount > 0) {
            evictOldCachedStories(ctx, cachedStories);
            editor.putStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS, cachedStories);
        }
        editor.apply();
    }

    public static void cacheStory(Context ctx, int id, String data) {
        if (ctx == null || id <= 0 || TextUtils.isEmpty(data) || JSONParser.ALGOLIA_ERROR_STRING.equals(data)) {
            return;
        }

        writeCachedStoryFiles(ctx, id, data);

        SharedPreferences sharedPreferences = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Set<String> cachedStories = SettingsUtils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);
        if (cachedStories == null) {
            cachedStories = new HashSet<>();
        }

        addCachedStoryIndexEntry(cachedStories, id, System.currentTimeMillis());
        evictOldCachedStories(ctx, cachedStories);

        sharedPreferences.edit()
                .remove(KEY_SHARED_PREFERENCES_CACHED_STORY + id)
                .putStringSet(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS, cachedStories)
                .apply();
    }

    public static String loadCachedStory(Context ctx, int id) {
        if (ctx == null || id <= 0) {
            return null;
        }

        String cachedStory = readStringFromFile(getCachedStoryFullFile(ctx, id));
        if (!TextUtils.isEmpty(cachedStory)) {
            return cachedStory;
        }

        // Backward compatibility for caches written before story data moved out of global prefs.
        cachedStory = SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORY + id);
        if (!TextUtils.isEmpty(cachedStory)) {
            cacheStory(ctx, id, cachedStory);
        }
        return cachedStory;
    }

    public static boolean loadCachedStorySummary(Context ctx, Story story) {
        if (ctx == null || story == null || story.id <= 0) {
            return false;
        }

        String summary = readStringFromFile(getCachedStorySummaryFile(ctx, story.id));
        if (TextUtils.isEmpty(summary)) {
            String fullStory = readStringFromFile(getCachedStoryFullFile(ctx, story.id));
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, story.id);
            if (!TextUtils.isEmpty(summary)) {
                writeStringToFile(getCachedStorySummaryFile(ctx, story.id), summary);
            }
        }

        return JSONParser.updateStoryWithCachedStorySummary(story, summary);
    }

    public static void cacheStoryPreviewState(Context ctx, Story story) {
        if (ctx == null
                || story == null
                || story.id <= 0
                || (!story.previewImageUrlLoaded
                && TextUtils.isEmpty(story.previewImageUrl)
                && !story.faviconTintColorLoaded)) {
            return;
        }

        Context appContext = ctx.getApplicationContext();
        Story previewState = new Story();
        previewState.id = story.id;
        previewState.previewImageUrl = story.previewImageUrl;
        previewState.previewImageUrlLoaded = story.previewImageUrlLoaded || !TextUtils.isEmpty(story.previewImageUrl);
        previewState.previewImageLoadFailed = story.previewImageLoadFailed;
        previewState.previewImageTintColor = story.previewImageTintColor;
        previewState.previewImageTintColorLoaded = story.previewImageTintColorLoaded;
        previewState.previewImageTintSourceUrl = story.previewImageTintSourceUrl;
        previewState.previewImageTintBaseColor = story.previewImageTintBaseColor;
        previewState.previewImageTintMode = story.previewImageTintMode;
        previewState.faviconTintColor = story.faviconTintColor;
        previewState.faviconTintColorLoaded = story.faviconTintColorLoaded;
        previewState.faviconTintSourceUrl = story.faviconTintSourceUrl;
        previewState.faviconTintBaseColor = story.faviconTintBaseColor;
        previewState.faviconTintMode = story.faviconTintMode;

        AsyncTask.execute(() -> writeCachedStoryPreviewState(appContext, previewState));
    }

    public static int getCachedPostCount(Context ctx) {
        if (ctx == null) {
            return 0;
        }

        return getCachedPostIds(ctx).size();
    }

    public static int clearPostCache(Context ctx) {
        if (ctx == null) {
            return 0;
        }

        Set<Integer> cachedPostIds = getCachedPostIds(ctx);
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_STORY)) {
                editor.remove(key);
            } else if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                editor.remove(key);
            }
        }

        editor.remove(KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS).apply();

        deleteFileOrDirectory(getStoryCacheDir(ctx));
        deleteFileOrDirectory(getArticleCacheDir(ctx));

        StoryPreviewImageLoader.clearDiskCache(ctx);

        return cachedPostIds.size();
    }

    private static Set<Integer> getCachedPostIds(Context ctx) {
        Set<Integer> cachedPostIds = new HashSet<>();

        Set<String> cachedStories = SettingsUtils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);
        if (cachedStories != null) {
            for (String cachedStory : cachedStories) {
                int id = getCachedStoryIndexEntryId(cachedStory);
                if (id > 0) {
                    cachedPostIds.add(id);
                }
            }
        }

        SharedPreferences sharedPreferences = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_STORY)) {
                addCachedPostId(cachedPostIds, key, KEY_SHARED_PREFERENCES_CACHED_STORY);
            } else if (key.startsWith(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL)) {
                addCachedPostId(cachedPostIds, key, KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL);
            }
        }

        File articleCacheDir = getArticleCacheDir(ctx);
        File[] cachedArticleFiles = articleCacheDir.listFiles();
        if (cachedArticleFiles != null) {
            for (File cachedArticleFile : cachedArticleFiles) {
                addCachedPostId(cachedPostIds, cachedArticleFile.getName(), "", ".html");
            }
        }

        addCachedPostIdsFromStoryCacheDir(cachedPostIds, getCachedStoryFullDir(ctx));
        addCachedPostIdsFromStoryCacheDir(cachedPostIds, getCachedStorySummaryDir(ctx));

        return cachedPostIds;
    }

    private static void addCachedStoryIndexEntry(Set<String> cachedStories, int id, long time) {
        removeCachedStoryIndexEntry(cachedStories, id);
        cachedStories.add(id + "-" + time);
    }

    private static void removeCachedStoryIndexEntry(Set<String> cachedStories, int id) {
        if (cachedStories == null) {
            return;
        }

        for (Iterator<String> iterator = cachedStories.iterator(); iterator.hasNext();) {
            String cached = iterator.next();
            int cachedId = getCachedStoryIndexEntryId(cached);
            if (cachedId <= 0 || cachedId == id) {
                iterator.remove();
            }
        }
    }

    private static void evictOldCachedStories(Context ctx, Set<String> cachedStories) {
        while (cachedStories.size() > MAX_CACHED_STORIES) {
            String oldestEntry = null;
            long oldestTime = -1;
            int oldestId = -1;

            for (String cachedStory : cachedStories) {
                int id = getCachedStoryIndexEntryId(cachedStory);
                long time = getCachedStoryIndexEntryTime(cachedStory);
                if (id <= 0 || time < 0) {
                    oldestEntry = cachedStory;
                    break;
                }
                if (oldestTime == -1 || time < oldestTime) {
                    oldestTime = time;
                    oldestId = id;
                    oldestEntry = cachedStory;
                }
            }

            if (oldestEntry == null) {
                break;
            }

            cachedStories.remove(oldestEntry);
            if (oldestId > 0) {
                deleteCachedStoryFiles(ctx, oldestId);
                deleteCachedArticleSnapshot(ctx, oldestId);
                ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_SHARED_PREFERENCES_CACHED_STORY + oldestId)
                        .apply();
            }
        }
    }

    private static int getCachedStoryIndexEntryId(String entry) {
        String[] idAndDate = entry == null ? new String[0] : entry.split("-");
        if (idAndDate.length != 2) {
            return -1;
        }
        try {
            return Integer.parseInt(idAndDate[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long getCachedStoryIndexEntryTime(String entry) {
        String[] idAndDate = entry == null ? new String[0] : entry.split("-");
        if (idAndDate.length != 2) {
            return -1;
        }
        try {
            return Long.parseLong(idAndDate[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long findCachedStoryIndexEntryTime(Set<String> cachedStories, int id) {
        if (cachedStories == null) {
            return -1;
        }

        for (String entry : cachedStories) {
            if (getCachedStoryIndexEntryId(entry) == id) {
                return getCachedStoryIndexEntryTime(entry);
            }
        }
        return -1;
    }

    private static void addCachedPostIdsFromStoryCacheDir(Set<Integer> cachedPostIds, File cacheDir) {
        File[] cachedStoryFiles = cacheDir.listFiles();
        if (cachedStoryFiles == null) {
            return;
        }

        for (File cachedStoryFile : cachedStoryFiles) {
            addCachedPostId(cachedPostIds, cachedStoryFile.getName(), "", STORY_CACHE_FILE_SUFFIX);
        }
    }

    private static void addCachedPostId(Set<Integer> cachedPostIds, String value, String prefix) {
        addCachedPostId(cachedPostIds, value, prefix, "");
    }

    private static void addCachedPostId(Set<Integer> cachedPostIds, String value, String prefix, String suffix) {
        if (!value.startsWith(prefix) || !value.endsWith(suffix)) {
            return;
        }

        int end = value.length() - suffix.length();
        try {
            cachedPostIds.add(Integer.parseInt(value.substring(prefix.length(), end)));
        } catch (NumberFormatException ignored) {}
    }

    private static void writeCachedStoryFiles(Context ctx, int id, String data) {
        writeStringToFile(getCachedStoryFullFile(ctx, id), data);

        String summary = JSONParser.compactAlgoliaStoryResponse(data, id);
        if (!TextUtils.isEmpty(summary)) {
            writeStringToFile(getCachedStorySummaryFile(ctx, id), summary);
        }
    }

    private static void writeCachedStoryPreviewState(Context ctx, Story previewState) {
        File summaryFile = getCachedStorySummaryFile(ctx, previewState.id);
        if (!summaryFile.exists()) {
            return;
        }

        String summary = readStringFromFile(summaryFile);
        if (TextUtils.isEmpty(summary)) {
            String fullStory = readStringFromFile(getCachedStoryFullFile(ctx, previewState.id));
            summary = JSONParser.compactAlgoliaStoryResponse(fullStory, previewState.id);
        }

        String updatedSummary = JSONParser.updateCachedStorySummaryPreviewState(summary, previewState);
        if (!TextUtils.isEmpty(updatedSummary) && !TextUtils.equals(summary, updatedSummary)) {
            writeStringToFile(summaryFile, updatedSummary);
        }
    }

    private static File getStoryCacheDir(Context ctx) {
        return new File(ctx.getFilesDir(), STORY_CACHE_DIR);
    }

    private static File getCachedStoryFullDir(Context ctx) {
        return new File(getStoryCacheDir(ctx), STORY_CACHE_FULL_DIR);
    }

    private static File getCachedStorySummaryDir(Context ctx) {
        return new File(getStoryCacheDir(ctx), STORY_CACHE_SUMMARY_DIR);
    }

    private static File getCachedStoryFullFile(Context ctx, int id) {
        return new File(getCachedStoryFullDir(ctx), id + STORY_CACHE_FILE_SUFFIX);
    }

    private static File getCachedStorySummaryFile(Context ctx, int id) {
        return new File(getCachedStorySummaryDir(ctx), id + STORY_CACHE_FILE_SUFFIX);
    }

    private static void deleteCachedStoryFiles(Context ctx, int id) {
        File fullFile = getCachedStoryFullFile(ctx, id);
        if (fullFile.exists() && !fullFile.delete()) {
            fullFile.deleteOnExit();
        }

        File summaryFile = getCachedStorySummaryFile(ctx, id);
        if (summaryFile.exists() && !summaryFile.delete()) {
            summaryFile.deleteOnExit();
        }
    }

    private static String readStringFromFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static boolean writeStringToFile(File file, String data) {
        if (file == null || TextUtils.isEmpty(data)) {
            return false;
        }

        FileOutputStream outputStream = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            outputStream = new FileOutputStream(file);
            outputStream.write(data.getBytes("UTF-8"));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static void cacheArticleSnapshot(Context ctx, int id, String url, String html) {
        if (ctx == null || id <= 0 || TextUtils.isEmpty(url) || TextUtils.isEmpty(html)) {
            return;
        }

        FileOutputStream outputStream = null;
        try {
            File articleCacheDir = getArticleCacheDir(ctx);
            if (!articleCacheDir.exists() && !articleCacheDir.mkdirs()) {
                return;
            }

            outputStream = new FileOutputStream(getArticleCacheFile(ctx, id));
            outputStream.write(html.getBytes("UTF-8"));
            SettingsUtils.saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id, url);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static String loadCachedArticleSnapshot(Context ctx, int id) {
        if (ctx == null || id <= 0) {
            return null;
        }

        File cacheFile = getArticleCacheFile(ctx, id);
        if (!cacheFile.exists()) {
            return null;
        }

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(cacheFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static String loadCachedArticleUrl(Context ctx, int id) {
        if (ctx == null || id <= 0) {
            return null;
        }
        return SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id);
    }

    public static void deleteCachedArticleSnapshot(Context ctx, int id) {
        if (ctx == null || id <= 0) {
            return;
        }

        File cacheFile = getArticleCacheFile(ctx, id);
        if (cacheFile.exists() && !cacheFile.delete()) {
            cacheFile.deleteOnExit();
        }
        ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit().remove(KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + id).apply();
    }

    private static File getArticleCacheDir(Context ctx) {
        return new File(ctx.getFilesDir(), "article_cache");
    }

    private static File getArticleCacheFile(Context ctx, int id) {
        return new File(getArticleCacheDir(ctx), id + ".html");
    }

    private static void deleteFileOrDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileOrDirectory(child);
                }
            }
        }

        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    public static boolean hasCachedStories(Context ctx) {
        Set<String> cached = SettingsUtils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);
        if (cached == null) {
            return false;
        }

        long limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
        for (String entry : cached) {
            int id = getCachedStoryIndexEntryId(entry);
            long time = getCachedStoryIndexEntryTime(entry);
            if (id > 0 && time >= limit && loadCachedStoryForStoriesList(ctx, id) != null) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<Story> loadCachedStories(Context ctx) {
        Set<String> cached = SettingsUtils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);
        ArrayList<Story> stories = new ArrayList<>();
        if (cached == null) {
            return stories;
        }

        long limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000;

        List<Pair<Long, Integer>> orderedIds = new ArrayList<>();

        for (String entry : cached) {
            int id = getCachedStoryIndexEntryId(entry);
            long time = getCachedStoryIndexEntryTime(entry);
            if (id <= 0 || time < 0) continue;
            if (time < limit) continue;

            orderedIds.add(new Pair<>(time, id));
        }

        //dont replace, is there for old API compatibility
        Collections.sort(orderedIds, (a, b) -> Long.compare(a.first, b.first));

        for (Pair<Long, Integer> pair : orderedIds) {
            Story story = loadCachedStoryForStoriesList(ctx, pair.second);
            if (story != null) {
                stories.add(story);
            }
        }

        return stories;
    }

    private static Story loadCachedStoryForStoriesList(Context ctx, int id) {
        Story story = new Story();
        story.id = id;
        boolean loaded = loadCachedStorySummary(ctx, story);
        if (!loaded) {
            String fullStory = loadCachedStory(ctx, id);
            String summary = JSONParser.compactAlgoliaStoryResponse(fullStory, id);
            loaded = !TextUtils.isEmpty(summary) && JSONParser.updateStoryWithCachedStorySummary(story, summary);
        }

        if (!loaded || story.isComment) {
            return null;
        }

        return story;
    }

    public static ArrayList<Bookmark> loadBookmarks(Context ctx, boolean sorted) {
        return loadBookmarks(sorted, SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_BOOKMARKS));
    }

    public static ArrayList<Bookmark> loadBookmarks(boolean sorted, String bookmarksString) {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */

        ArrayList<Bookmark> bookmarks = new ArrayList<>();

        if (bookmarksString == null || bookmarksString.isEmpty()) {
            return bookmarks;
        }

        String[] pairs = bookmarksString.split("-");
        for (String pair : pairs) {
            Bookmark b = new Bookmark();
            String[] info = pair.split("q");

            if (info.length == 2) {
                b.id = Integer.parseInt(info[0]);
                b.created = Long.parseLong(info[1]);
                bookmarks.add(b);
            }
        }

        if (sorted) {
            Collections.sort(bookmarks, (b1, b2) -> Long.compare(b2.created, b1.created));
        }

        return bookmarks;
    }

    public static boolean isBookmarked(Context ctx, int id) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(ctx, false);
        for (Bookmark b : bookmarks) {
            if (b.id == id) {
                return true;
            }
        }

        return false;
    }

    public static void saveBookmarks(Context ctx, ArrayList<Bookmark> bookmarks) {
        saveBookmarkList(ctx, KEY_SHARED_PREFERENCES_BOOKMARKS, bookmarks);
    }

    private static void saveBookmarkList(Context ctx, String key, ArrayList<Bookmark> bookmarks) {
        StringBuilder sb = new StringBuilder();
        int size = bookmarks.size();

        for (int i = 0; i < size; i++) {
            Bookmark b = bookmarks.get(i);
            sb.append(b.id);
            sb.append("q");
            sb.append(b.created);
            if (i != size - 1) {
                sb.append("-");
            }
        }

        SettingsUtils.saveStringToSharedPreferences(ctx, key, sb.toString());
    }

    public static void addBookmark(Context ctx, int id) {
        if (isBookmarked(ctx, id)) {
            return;
        }

        ArrayList<Bookmark> bookmarks = loadBookmarks(ctx, false);
        Bookmark b = new Bookmark();
        b.id = id;
        b.created = System.currentTimeMillis();
        bookmarks.add(b);
        saveBookmarks(ctx, bookmarks);
    }

    public static void removeBookmark(Context ctx, int id) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(ctx, false);

        for (Bookmark bookmark: bookmarks) {
            if (bookmark.id == id) {
                bookmarks.remove(bookmark);
                break;
            }
        }

        saveBookmarks(ctx, bookmarks);
    }

    public static ArrayList<Bookmark> loadFavorites(Context ctx, boolean sorted) {
        return loadSavedItemList(ctx, KEY_SHARED_PREFERENCES_FAVORITES, sorted);
    }

    public static ArrayList<Bookmark> loadUpvoted(Context ctx, boolean sorted) {
        return loadSavedItemList(ctx, KEY_SHARED_PREFERENCES_UPVOTED, sorted);
    }

    public static Set<Integer> loadFavoriteCommentIds(Context ctx) {
        return SettingsUtils.readIntSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS);
    }

    public static Set<Integer> loadUpvotedCommentIds(Context ctx) {
        return SettingsUtils.readIntSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS);
    }

    private static ArrayList<Bookmark> loadSavedItemList(Context ctx, String key, boolean sorted) {
        ArrayList<Bookmark> items = loadBookmarks(false, SettingsUtils.readStringFromSharedPreferences(ctx, key));
        if (sorted) {
            Collections.sort(items, (b1, b2) -> Integer.compare(b2.id, b1.id));
        }
        return items;
    }

    public static boolean isFavorited(Context ctx, int id) {
        ArrayList<Bookmark> favorites = loadFavorites(ctx, false);
        for (Bookmark favorite : favorites) {
            if (favorite.id == id) {
                return true;
            }
        }

        return false;
    }

    public static boolean isUpvoted(Context ctx, int id, boolean comment) {
        if (comment) {
            return loadUpvotedCommentIds(ctx).contains(id);
        }

        ArrayList<Bookmark> upvoted = loadUpvoted(ctx, false);
        for (Bookmark item : upvoted) {
            if (item.id == id) {
                return true;
            }
        }

        return false;
    }

    public static void saveFavorites(Context ctx, ArrayList<Bookmark> favorites) {
        saveBookmarkList(ctx, KEY_SHARED_PREFERENCES_FAVORITES, favorites);
    }

    public static void saveFavoriteIds(Context ctx, List<Integer> ids) {
        saveSavedItemIds(ctx, KEY_SHARED_PREFERENCES_FAVORITES, ids);
    }

    public static void saveFavoriteCommentIds(Context ctx, Set<Integer> ids) {
        SettingsUtils.saveIntSetToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS, ids);
    }

    public static void saveUpvotedIds(Context ctx, List<Integer> ids) {
        saveSavedItemIds(ctx, KEY_SHARED_PREFERENCES_UPVOTED, ids);
    }

    public static void saveUpvotedCommentIds(Context ctx, Set<Integer> ids) {
        SettingsUtils.saveIntSetToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS, ids);
    }

    private static void saveSavedItemIds(Context ctx, String key, List<Integer> ids) {
        ArrayList<Bookmark> items = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        long now = System.currentTimeMillis();

        for (int id : ids) {
            if (!seenIds.add(id)) {
                continue;
            }

            Bookmark item = new Bookmark();
            item.id = id;
            item.created = now - items.size();
            items.add(item);
        }

        saveBookmarkList(ctx, key, items);
    }

    public static void addFavorite(Context ctx, int id) {
        if (isFavorited(ctx, id)) {
            return;
        }

        ArrayList<Bookmark> favorites = loadFavorites(ctx, false);
        Bookmark favorite = new Bookmark();
        favorite.id = id;
        favorite.created = System.currentTimeMillis();
        favorites.add(favorite);
        saveFavorites(ctx, favorites);
    }

    public static void setFavorite(Context ctx, int id, boolean favorite) {
        if (favorite) {
            addFavorite(ctx, id);
        } else {
            removeFavorite(ctx, id);
        }
    }

    public static void removeFavorite(Context ctx, int id) {
        ArrayList<Bookmark> favorites = loadFavorites(ctx, false);

        for (Bookmark favorite: favorites) {
            if (favorite.id == id) {
                favorites.remove(favorite);
                break;
            }
        }

        saveFavorites(ctx, favorites);
    }

    public static void setUpvoted(Context ctx, int id, boolean comment, boolean upvoted) {
        if (comment) {
            Set<Integer> upvotedCommentIds = loadUpvotedCommentIds(ctx);
            if (upvoted) {
                upvotedCommentIds.add(id);
            } else {
                upvotedCommentIds.remove(id);
            }
            saveUpvotedCommentIds(ctx, upvotedCommentIds);
            return;
        }

        ArrayList<Bookmark> upvotedItems = loadUpvoted(ctx, false);
        for (Bookmark item : upvotedItems) {
            if (item.id == id) {
                if (!upvoted) {
                    upvotedItems.remove(item);
                    saveBookmarkList(ctx, KEY_SHARED_PREFERENCES_UPVOTED, upvotedItems);
                }
                return;
            }
        }

        if (upvoted) {
            Bookmark item = new Bookmark();
            item.id = id;
            item.created = System.currentTimeMillis();
            upvotedItems.add(item);
            saveBookmarkList(ctx, KEY_SHARED_PREFERENCES_UPVOTED, upvotedItems);
        }
    }

    public static String getThousandSeparatedString(int n) {
        BigDecimal bd = new BigDecimal(n);
        NumberFormat formatter = NumberFormat.getInstance(new Locale("en_US"));

        return formatter.format(bd.longValue());
    }

    public static ArrayList<String> getFilterWords(Context ctx) {
        return getCommaSeparatedPreference(ctx, "pref_filter");
    }

    public static ArrayList<String> getFilterDomains(Context ctx) {
        return getCommaSeparatedPreference(ctx, "pref_filter_domains");
    }

    public static Set<String> getFilteredUsers(Context ctx) {
        return getCommaSeparatedPreferenceSet(ctx, "pref_filter_users", true);
    }

    public static boolean removeFilteredUser(Context ctx, String username) {
        if (TextUtils.isEmpty(username)) return false;

        Set<String> users = getFilteredUsers(ctx);
        users.remove(username.toLowerCase().trim());
        saveCommaSeparatedPreferenceSet(ctx, "pref_filter_users", users);

        return true;
    }

    public static boolean addFilteredUser(Context ctx, String username) {
        if (TextUtils.isEmpty(username)) return false;

        Set<String> users = getFilteredUsers(ctx);
        users.add(username.toLowerCase().trim());
        saveCommaSeparatedPreferenceSet(ctx, "pref_filter_users", users);

        return true;
    }

    private static ArrayList<String> getCommaSeparatedPreference(Context ctx, String key) {
        return getCommaSeparatedPreference(ctx, key, false);
    }

    private static ArrayList<String> getCommaSeparatedPreference(Context ctx, String key, boolean lowercase) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String prefText = prefs.getString(key, null);

        ArrayList<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(prefText)) {
            if (lowercase) {
                prefText = prefText.toLowerCase();
            }
            for (String value : prefText.split(",")) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private static Set<String> getCommaSeparatedPreferenceSet(Context ctx, String key, boolean lowercase) {
        return new HashSet<>(getCommaSeparatedPreference(ctx, key, lowercase));
    }

    private static void saveCommaSeparatedPreferenceSet(Context ctx, String key, Set<String> values) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(key, joinCommaSeparated(values))
                .apply();
    }

    private static String joinCommaSeparated(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = values.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static Map<String, String> getUserTags(Context ctx) {
        String jsonString = SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_USER_TAGS);
        Map<String, String> map = new HashMap<>();
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                JSONObject obj = new JSONObject(jsonString);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = obj.optString(key, "");
                    map.put(key.toLowerCase().trim(), value);
                }
            } catch (JSONException e) {
                // Invalid JSON in prefs; just start fresh
                e.printStackTrace();
            }
        }
        return map;
    }

    public static String getUserTag(Context ctx, String username) {
        if (TextUtils.isEmpty(username)) return "";
        Map<String, String> map = getUserTags(ctx);
        String tag = map.get(username.toLowerCase().trim());
        return tag == null ? "" : tag;
    }

    public static void setUserTag(Context ctx, String username, String tag) {
        if (TextUtils.isEmpty(username)) return;
        // Load existing
        Map<String, String> map = getUserTags(ctx);
        String key = username.toLowerCase().trim();
        if (TextUtils.isEmpty(tag)) {
            map.remove(key);
        } else {
            map.put(key, tag.trim());
        }
        // Convert back to JSON
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, String> e : map.entrySet()) {
            try {
                obj.put(e.getKey(), e.getValue());
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
        SettingsUtils.saveStringToSharedPreferences(
                ctx,
                KEY_SHARED_PREFERENCES_USER_TAGS,
                obj.toString()
        );
    }

    public static boolean shouldShowWelcomeDialog(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        scheduleLegacyStoryCacheMigration(ctx);
        return !sharedPref.getBoolean(KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN, false);
    }

    public static boolean hasLegacyWelcomePreference(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        return sharedPref.contains(KEY_SHARED_PREFERENCES_FIRST_TIME);
    }

    public static void markWelcomeDialogShown(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        sharedPref.edit().putBoolean(KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN, true).apply();
    }

    public static boolean justUpdated(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        scheduleLegacyStoryCacheMigration(ctx);
        if (BuildConfig.VERSION_CODE > sharedPref.getInt(KEY_SHARED_PREFERENCES_LAST_VERSION, -1)) {
            sharedPref.edit().putInt(KEY_SHARED_PREFERENCES_LAST_VERSION, BuildConfig.VERSION_CODE).apply();
            return true;
        }
        return false;
    }

    public static String getTimeAgo(long time) {
        if (time < 1000000000000L) {
            // if timestamp given in seconds, convert to millis
            time *= 1000;
        }

        long now = System.currentTimeMillis();
        if (time > now || time <= 0) {
            return "?";
        }

        final long diff = now - time;
        if (diff < MINUTE_MILLIS) {
            return "just now";
        } else if (diff < 2 * MINUTE_MILLIS) {
            return "1m";
        } else if (diff < 50 * MINUTE_MILLIS) {
            return diff / MINUTE_MILLIS + "m";
        } else if (diff < 120 * MINUTE_MILLIS) {
            return "1h";
        } else if (diff < 24 * HOUR_MILLIS) {
            return diff / HOUR_MILLIS + "h";
        } else if (diff < 48 * HOUR_MILLIS) {
            return "1d";
        } else if (diff < 365 * DAY_MILLIS) {
            return diff / DAY_MILLIS + "d";
        } else if (diff < 2 * YEAR_MILLIS) {
            return "1y";
        } else {
            return diff / YEAR_MILLIS + "y";
        }
    }

    public static boolean isOnWiFi(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);

        return networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static void launchCustomTab(Context ctx, String url) {
        launchCustomTab(ctx, url, true);
    }

    public static void launchCustomTab(Context ctx, String url, boolean shareable) {
        if (url != null) {
            if (SettingsUtils.shouldUseExternalBrowser(ctx) || !isCustomTabSupported(ctx)) {
                launchInExternalBrowser(ctx, url);
            } else {
                try {
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    builder.setShareState(shareable ? CustomTabsIntent.SHARE_STATE_ON : CustomTabsIntent.SHARE_STATE_OFF);

                    CustomTabColorSchemeParams.Builder colorBuilder = new CustomTabColorSchemeParams.Builder();
                    colorBuilder.setToolbarColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                    builder.setDefaultColorSchemeParams(colorBuilder.build());

                    CustomTabsIntent customTabsIntent = builder.build();

                    customTabsIntent.launchUrl(ctx, Uri.parse(url));
                } catch (Exception e) {
                    e.printStackTrace();

                    try {
                        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                        builder.setShareState(shareable ? CustomTabsIntent.SHARE_STATE_ON : CustomTabsIntent.SHARE_STATE_OFF);

                        CustomTabColorSchemeParams.Builder colorBuilder = new CustomTabColorSchemeParams.Builder();
                        colorBuilder.setToolbarColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                        builder.setDefaultColorSchemeParams(colorBuilder.build());

                        CustomTabsIntent customTabsIntent = builder.build();

                        customTabsIntent.launchUrl(ctx, Uri.parse(URLUtil.guessUrl(url)));
                    } catch (Exception e1) {
                        try {
                            if (!url.startsWith("http://") && !url.startsWith("https://"))
                                url = "http://" + url;

                            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                            builder.setShareState(shareable ? CustomTabsIntent.SHARE_STATE_ON : CustomTabsIntent.SHARE_STATE_OFF);

                            CustomTabColorSchemeParams.Builder colorBuilder = new CustomTabColorSchemeParams.Builder();
                            colorBuilder.setToolbarColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                            builder.setDefaultColorSchemeParams(colorBuilder.build());

                            CustomTabsIntent customTabsIntent = builder.build();

                            customTabsIntent.launchUrl(ctx, Uri.parse(url));
                        } catch (Exception e2) {
                            launchInExternalBrowser(ctx, url);
                        }
                    }
                }
            }
        }
    }

    public static void launchInExternalBrowser(Context ctx, String url) {
        try {
            openExternalUrl(ctx, url);
        } catch (Exception e) {
            // failed for the first time, let's try to guess a fix to the url
            try {
                openExternalUrl(ctx, URLUtil.guessUrl(url));
            } catch (Exception e1) {
                // automated fix didn't work, let's try to do it manually
                try {
                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                        url = "http://" + url;
                    openExternalUrl(ctx, url);
                } catch (Exception e2) {
                    Toast.makeText(ctx, "Couldn't open link to: " + url, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static void openExternalUrl(Context ctx, String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        String packageName = getPackageForExternalUrl(ctx, browserIntent);
        if (packageName != null) {
            browserIntent.setPackage(packageName);
        }
        ctx.startActivity(browserIntent);
    }

    private static String getPackageForExternalUrl(Context ctx, Intent browserIntent) {
        String defaultBrowserPackageName = ContextExtensionsKt.defaultBrowserPackageName(ctx);
        if (defaultBrowserPackageName == null) {
            return null;
        }

        ResolveInfo resolveInfo = ctx.getPackageManager()
                .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);
        String resolvedPackageName = null;
        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            resolvedPackageName = resolveInfo.activityInfo.packageName;
        }

        // force browser only when VIEW resolves to Harmonic itself (self-loop) or a known bad resolver.
        if (ctx.getPackageName().equals(resolvedPackageName)
                || ContextExtensionsKt.isInvalidViewHandlerPackage(ctx, resolvedPackageName)) {
            return defaultBrowserPackageName;
        }

        return null;
    }

    public static boolean downloadPDF(Context context, String pdfUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(pdfUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Check if there's an app that can handle this intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    public static boolean isCustomTabSupported(Context context) {
        return !getCustomTabsPackages(context).isEmpty();
    }

    /**
     * Returns a list of packages that support Custom Tabs.
     */
    public static ArrayList<ResolveInfo> getCustomTabsPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        // Get default VIEW intent handler.
        Intent activityIntent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.fromParts("http", "", null));

        // Get all apps that can handle VIEW intents.
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        ArrayList<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info);
            }
        }

        return packagesSupportingCustomTabs;
    }

    public static int getColorViaAttr(Context ctx, int attr) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = ctx.getTheme();
        theme.resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static void writeInFile(Context ctx, Uri uri, String text) throws IOException {
        OutputStream outputStream;
        outputStream = ctx.getContentResolver().openOutputStream(uri);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));
        bw.write(text);
        bw.flush();
        bw.close();
    }

    public static String readFileContent(Context ctx, Uri uri) throws IOException {
        InputStream inputStream = ctx.getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String currentline;
        while ((currentline = reader.readLine()) != null) {
            stringBuilder.append(currentline);
        }
        inputStream.close();
        return stringBuilder.toString();
    }

    /**
     * Check if time represented as minutes since midnight is between two other times.
     * <p>
     * If {@code initialTime} is after {@code finalTime}, then {@code currentTime} must be between
     * last day's {@code initialTime} and this day's {@code finalTime} or this day's {@code initialTime}
     * and next day's {@code finalTime}
     */
    public static boolean isTimeBetweenTwoTimes(long initialTime, long finalTime, long currentTime) {
        if (finalTime < initialTime) {
            finalTime += TimeUnit.DAYS.toMinutes(1);
        }

        if (currentTime < initialTime) {
            currentTime += TimeUnit.DAYS.toMinutes(1);
        }

        return initialTime <= currentTime && currentTime < finalTime;
    }
    
    public static void setNighttimeHours(int fromHour, int fromMinute, int toHour, int toMinute, Context ctx) {
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_FROM_HOUR, fromHour + "");
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_FROM_MINUTE, fromMinute + "");
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_TO_HOUR, toHour + "");
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_TO_MINUTE, toMinute + "");
    }

    public static int[] getNighttimeHours(Context ctx) {
        return new int[] {
                Integer.parseInt(SettingsUtils.readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_FROM_HOUR, "21")),
                Integer.parseInt(SettingsUtils.readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_FROM_MINUTE, "0")),
                Integer.parseInt(SettingsUtils.readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_TO_HOUR, "6")),
                Integer.parseInt(SettingsUtils.readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_TO_MINUTE, "0"))
        };
    }

    public static boolean timeInSecondsMoreThanTwoWeeksAgo(int time) {
        return (System.currentTimeMillis() - ((long) time)*1000)/1000/60/60/24 > 14;
    }

    public static boolean timeInSecondsMoreThanTwoHoursAgo(int time) {
        return (System.currentTimeMillis() - ((long) time)*1000)/1000/60/60 > 2;
    }

    public static float pxFromDp(final Resources resources, final float dp) {
        return dp * resources.getDisplayMetrics().density;
    }

    public static int pxFromDpInt(final Resources resources, final float dp) {
        return Math.round(pxFromDp(resources, dp));
    }

    public static boolean isTablet(Resources res) {
        return res.getBoolean(R.bool.is_tablet);
    }

    public static void openLinkMaybeHN(Context context, String href) {
        if (context == null || TextUtils.isEmpty(href)) {
            return;
        }

        Uri uri = Uri.parse(href);

        // Validate the scheme (http or https)
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            // Validate the host and path
            if ("news.ycombinator.com".equalsIgnoreCase(uri.getHost()) && "/item".equals(uri.getPath())) {
                String sId = uri.getQueryParameter("id");

                // Check if id parameter is valid
                if (sId != null && !sId.isEmpty() && TextUtils.isDigitsOnly(sId)) {
                    int id = Integer.parseInt(sId);
                    int scrollToCommentId = -1;
                    String fragment = uri.getFragment();
                    if (fragment != null && !fragment.isEmpty() && TextUtils.isDigitsOnly(fragment)) {
                        scrollToCommentId = Integer.parseInt(fragment);
                    }
                    openCommentsActivity(id, scrollToCommentId, context);
                    return;
                }
            }
        }

        Utils.launchCustomTab(context, href);
    }

    public static Uri getHackerNewsItemUriFromText(String text) {
        if (text == null) return null;

        Matcher matcher = HN_ITEM_URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = trimTrailingUrlPunctuation(matcher.group());
            Uri uri = Uri.parse(url);
            if (isHackerNewsItemUri(uri)) {
                return uri;
            }
        }

        return null;
    }

    public static boolean isHackerNewsItemUri(Uri uri) {
        if (uri == null) return false;

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        if (!"news.ycombinator.com".equalsIgnoreCase(uri.getHost())) return false;
        if (!"/item".equals(uri.getPath())) return false;

        String sId = uri.getQueryParameter("id");
        return sId != null && !sId.isEmpty() && TextUtils.isDigitsOnly(sId);
    }

    private static String trimTrailingUrlPunctuation(String url) {
        while (!url.isEmpty()) {
            char last = url.charAt(url.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == ')' || last == ']') {
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        return url;
    }

    public static void openCommentsActivity(int id, int scrollToCommentId, Context context) {
        Uri.Builder builder = Uri.parse("https://news.ycombinator.com/item").buildUpon()
                .appendQueryParameter("id", String.valueOf(id));
        if (scrollToCommentId > 0) {
            builder.fragment(String.valueOf(scrollToCommentId));
        }
        Uri uri = builder.build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(context, CommentsActivity.class);
        context.startActivity(intent);
    }

    public static boolean canProvideSummary(Context ctx) {
        if (!isAiSummaryEnabled(ctx)) {
            return false;
        }
        String mode = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_ai_summary_mode", "cloud");
        if ("local".equals(mode)) {
            return SummaryManager.canAttemptLocalSummarization();
        }
        String apiKey = AiSummaryApiKeyStore.getApiKey(ctx);
        return !apiKey.isEmpty();
    }

    public static boolean isAiSummaryEnabled(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_ai_summary_enabled", isAiSummaryEnabledByDefault(ctx));
    }

    private static boolean isAiSummaryEnabledByDefault(Context ctx) {
        return SummaryManager.canAttemptLocalSummarization()
                || !AiSummaryApiKeyStore.getApiKey(ctx).isEmpty();
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static String linkify(String input) {
        if (input == null || input.isEmpty()) return input;

        // Existing <a>...</a> blocks: keep as-is
        Pattern aTag = Pattern.compile("(?is)<a\\b[^>]*>.*?</a>");

        // Accept http(s) with either // or HTML-escaped slashes (&#x2F; or &#47;)
        // Require a dot in the host. Stop at spaces or angle/quote chars.
        String slash = "(?:/{1}|(?:&#x2F;)|(?:&#47;))";
        Pattern url = Pattern.compile(
                "(https?:" + slash + slash + "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)"
        );

        String trailing = ".,;:!?)";
        StringBuilder out = new StringBuilder(input.length());
        Matcher a = aTag.matcher(input);
        int idx = 0;

        // Helper-like inline blocks only
        while (a.find()) {
            String segment = input.substring(idx, a.start());
            Matcher m = url.matcher(segment);
            StringBuffer sb = new StringBuffer(segment.length());

            while (m.find()) {
                String rep = getString(m, trailing);
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            out.append(sb);

            // Keep existing anchor untouched
            out.append(a.group());
            idx = a.end();
        }

        // Tail after last <a>
        String segment = input.substring(idx);
        Matcher m = url.matcher(segment);
        StringBuffer sb = new StringBuffer(segment.length());
        while (m.find()) {
            String rep = getString(m, trailing);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        out.append(sb);

        return out.toString();
    }

    @NonNull
    private static String getString(Matcher m, String trailing) {
        String u = m.group();

        // Trim common trailing punctuation
        int end = u.length();
        while (end > 0 && trailing.indexOf(u.charAt(end - 1)) >= 0) end--;

        // Balance unmatched ')'
        if (end > 0 && u.charAt(end - 1) == ')') {
            int opens = 0, closes = 0;
            for (int i = 0; i < end; i++) {
                char c = u.charAt(i);
                if (c == '(') opens++;
                else if (c == ')') closes++;
            }
            if (closes > opens) end--;
        }

        String core = u.substring(0, end);
        String rest = u.substring(end);

        // Normalize HTML-escaped slashes in the URL for href and text
        String normalized = core
                .replace("&#x2F;", "/")
                .replace("&#47;", "/");

        String rep = "<a href=\"" + normalized + "\">" + normalized + "</a>" + rest;
        return rep;
    }

    public static String expandShortenedAnchorText(String inputHtml) {
        if (inputHtml == null || inputHtml.isEmpty() || !inputHtml.contains("<a")) {
            return inputHtml;
        }

        Document document = Jsoup.parse(inputHtml, "", Parser.htmlParser());
        Elements links = document.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            String linkText = link.text();

            String decodedHref = Jsoup.parse(href).text();
            String decodedLinkText = Jsoup.parse(linkText).text();

            if (decodedLinkText.endsWith("...")) {
                String linkTextPrefix = decodedLinkText.substring(0, decodedLinkText.length() - 3);
                if (decodedHref.startsWith(linkTextPrefix)) {
                    link.text(decodedHref);
                }
            }
        }

        return document.body().html();
    }

}
