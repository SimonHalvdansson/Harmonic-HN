package com.simon.harmonichackernews.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.Story;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

public class Utils {

    public final static String KEY_SHARED_PREFERENCES_CLICKED_IDS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CLICKED_IDS";
    public final static String KEY_SHARED_PREFERENCES_CACHED_STORY = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORY";
    public final static String KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS";
    public final static String KEY_SHARED_PREFERENCES_BOOKMARKS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS";
    public final static String KEY_SHARED_PREFERENCES_FIRST_TIME = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME";
    public final static String GLOBAL_SHARED_PREFERENCES_KEY = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY";

    public final static String KEY_NIGHTTIME_FROM_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_HOUR";
    public final static String KEY_NIGHTTIME_FROM_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_MINUTE";
    public final static String KEY_NIGHTTIME_TO_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_HOUR";
    public final static String KEY_NIGHTTIME_TO_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_MINUTE";

    private static final long SECOND_MILLIS = 1000;
    private static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    private static final long YEAR_MILLIS = 365 * DAY_MILLIS;

    public final static String URL_TOP = "https://hacker-news.firebaseio.com/v0/topstories.json";
    public final static String URL_NEW = "https://hacker-news.firebaseio.com/v0/newstories.json";
    public final static String URL_BEST = "https://hacker-news.firebaseio.com/v0/beststories.json";
    public final static String URL_ASK = "https://hacker-news.firebaseio.com/v0/askstories.json";
    public final static String URL_SHOW = "https://hacker-news.firebaseio.com/v0/showstories.json";
    public final static String URL_JOBS = "https://hacker-news.firebaseio.com/v0/jobstories.json";

    public static String adservers;

    public static void log(String s) {
        Log.d("TAG", s);
    }

    public static void log(long i) {
        Log.d("TAG", "" + i);
    }

    public static void log(int i) {
        Log.d("TAG", "" + i);
    }

    public static void log(boolean b) {
        Log.d("TAG", "" + b);
    }

    public static String getDomainName(String url) throws Exception {
        if (url.endsWith("#")) {
            url = url.substring(0, url.length()-1);
        }
        URI uri = new URI(url);
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    public static void loadAdservers(Resources resources) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                String strLine2;
                StringBuilder adserversBuilder = new StringBuilder();

                InputStream fis2 = resources.openRawResource(R.raw.adblockserverlist);
                BufferedReader br2 = new BufferedReader(new InputStreamReader(fis2));
                if (fis2 != null) {
                    try {
                        while ((strLine2 = br2.readLine()) != null) {
                            adserversBuilder.append(strLine2);
                            adserversBuilder.append("\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Utils.adservers = String.valueOf(adserversBuilder);
            }
        };
        AsyncTask.execute(r);
    }

    public static Set<Integer> readIntSetFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Set<String> emptyBackup = new HashSet<>();
        Set<String> stringSet = sharedPref.getStringSet(key, emptyBackup);

        if (stringSet != null) {
            Set<Integer> intSet = new HashSet<>(stringSet.size());
            for (String string : stringSet) {
                intSet.add(Integer.parseInt(string));
            }
            return intSet;
        }
        return null;
    }

    public static void saveIntSetToSharedPreferences(Context ctx, String key, Set<Integer> set) {
        Set<String> stringSet = new HashSet<>(set.size());

        for (Integer integer : set) {
            stringSet.add(integer.toString());
        }

        saveStringSetToSharedPreferences(ctx, key, stringSet);
    }

    public static Set<String> readStringSetFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Set<String> emptyBackup = new HashSet<>();
        return sharedPref.getStringSet(key, emptyBackup);
    }

    public static void saveStringSetToSharedPreferences(Context ctx, String key, Set<String> set) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putStringSet(key, set).apply();
    }

    public static void saveStringToSharedPreferences(Context ctx, String key, String text) {
        saveStringToSharedPreferences(ctx, key, text, false);
    }

    public static void saveStringToSharedPreferences(Context ctx, String key, String text, boolean sync) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        if (sync) {
            editor.putString(key, text).apply();
        } else {
            editor.putString(key, text).commit();
        }
    }

    public static String readStringFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(key, null);
    }

    public static String readStringFromSharedPreferences(Context ctx, String key, String fallback) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(key, fallback);
    }

    public static void cacheStory(Context ctx, int id, String data) {
        saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORY + id, data);

        Set<String> cachedStories = Utils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);

        if (cachedStories == null) {
            cachedStories = new HashSet<>();
        }
        //if there already exists a story with the same id, remove it from list of cached since we're only saving the latest one
        if (cachedStories.size() > 0) {
            for (Iterator<String> iterator = cachedStories.iterator(); iterator.hasNext();) {
                String cached = iterator.next();
                String[] idAndDate = cached.split("-");
                if (Integer.parseInt(idAndDate[0]) == id) {
                    iterator.remove();
                }
            }
        }
        cachedStories.add(id + "-" + System.currentTimeMillis());

        if (cachedStories.size() > 100) {
            //If we have a lot of stories, lets delete the oldest one
            long oldestTime = -1;
            int oldestId = -1;
            for (String cachedStory : cachedStories) {
                String[] idAndDate = cachedStory.split("-");
                if (oldestTime == -1 || Long.parseLong(idAndDate[1]) < oldestTime) {
                    oldestTime = Long.parseLong(idAndDate[1]);
                    oldestId = Integer.parseInt(idAndDate[0]);
                }
            }
            
            cachedStories.remove(oldestId + "-" + oldestTime);

            ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit().remove(KEY_SHARED_PREFERENCES_CACHED_STORY + oldestId).apply();
        }

        Utils.saveStringSetToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS, cachedStories);
    }

    public static String loadCachedStory(Context ctx, int id) {
        return readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORY + id);
    }

    public static ArrayList<Bookmark> loadBookmarks(Context ctx, boolean sorted) {
        return loadBookmarks(sorted, readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_BOOKMARKS));
    }

    public static ArrayList<Bookmark> loadBookmarks(boolean sorted, String bookmarksString) {
        /* Format is {{ID}}q{{TIME}}-{{ID}}q{{TIME}}... */

        ArrayList<Bookmark> bookmarks = new ArrayList<>();

        if (bookmarksString == null || bookmarksString.length() == 0) {
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

        saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_BOOKMARKS, sb.toString(), true);
    }

    public static void addBookmark(Context ctx, int id) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(ctx, false);
        Bookmark b = new Bookmark();
        b.id = id;
        b.created = System.currentTimeMillis();
        bookmarks.add(b);
        saveBookmarks(ctx, bookmarks);
    }

    public static void removeBookmark(Context ctx, int id) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(ctx, false);

        int badIndex = -1;

        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).id == id) {
                badIndex = i;
            }
        }

        if (badIndex != -1) {
            bookmarks.remove(badIndex);
        }

        saveBookmarks(ctx, bookmarks);
    }

    public static String getThousandSeparatedString(int n) {
        BigDecimal bd = new BigDecimal(n);
        NumberFormat formatter = NumberFormat.getInstance(new Locale("en_US"));

        return formatter.format(bd.longValue());
    }

    public static ArrayList<String> getFilterWords(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String prefText = prefs.getString("pref_filter", null);

        ArrayList<String> phrases = new ArrayList<>();
        if (prefText != null && !prefText.equals("")) {
            for (String phrase : prefText.split(",")) {
                phrases.add(phrase.trim());
            }
        }
        return phrases;
    }

    public static boolean isFirstAppStart(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        if (sharedPref.getBoolean(KEY_SHARED_PREFERENCES_FIRST_TIME, true) && Utils.readIntSetFromSharedPreferences(ctx, Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS).size() == 0) {
            sharedPref.edit().putBoolean(KEY_SHARED_PREFERENCES_FIRST_TIME, false).apply();
            return true;
        }
        return false;
    }

    public static boolean shouldShowPoints(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_show_points", true);
    }

    public static boolean shouldUseCompactView(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_compact_view", false);
    }

    public static boolean shouldShowThumbnails(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_thumbnails", true);
    }

    public static boolean shouldCollapseParent(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_collapse_parent", false);
    }

    public static boolean shouldShowIndex(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_show_index", false);
    }

    public static boolean shouldShowNavigationButtons(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_scroll_navigation", false);
    }

    public static boolean shouldHideJobs(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_hide_jobs", false);
    }

    public static int getPreferredHotness(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return Integer.parseInt(prefs.getString("pref_hotness", "-1"));
    }

    public static String getPreferredFont(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_font", "productsans");
    }

    public static boolean shouldUseExternalBrowser(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_external_browser", false);
    }

    public static boolean shouldUseMonochromeCommentDepthIndicators(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_monochrome_comment_depth", false);
    }

    public static boolean shouldUseIntegratedWebView(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_webview", true);
    }

    public static String shouldPreloadWebView(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_preload_webview", "never");
    }

    public static boolean shouldMatchWebViewTheme(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_webview_match_theme", false);
    }

    public static boolean shouldBlockAds(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_webview_adblock", true);
    }

    public static boolean shouldDisableWebviewSwipeBack(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_webview_disable_swipeback", true);
    }

    public static boolean shouldDisableCommentsSwipeBack(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_comments_disable_swipeback", false);
    }

    public static boolean shouldShowTopLevelDepthIndicator(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_top_level_thread_indicators", false);
    }

    public static boolean shouldAlwaysOpenComments(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_always_open_comments", false);
    }

    public static boolean shouldShowWebviewExpandButton(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_webview_show_expand", true);
    }

    public static boolean shouldUseCompactHeader(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_compact_header", false);
    }

    public static boolean shouldUseLeftAlign(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_left_align", false);
    }

    public static boolean shouldUseTransparentStatusBar(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_transparent_status_bar", false);
    }

    public static boolean shouldUseSpecialNighttimeTheme(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_special_nighttime", false);
    }

    public static boolean shouldUseAlgolia(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean("pref_algolia_api", true);
    }

    public static int getPreferredCommentTextSize(Context ctx) {
        String sizeString =  PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_comment_text_size", "15");
        if (sizeString == null) {
            sizeString = "15";
        }
        return Integer.parseInt(sizeString);
    }

    public static String getTimeAgo(long time) {
        return getTimeAgo(time, false);
    }

    public static String getTimeAgo(long time, boolean explicit) {
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
            return explicit ? "1 minute ago" : "1 min";
        } else if (diff < 50 * MINUTE_MILLIS) {
            return diff / MINUTE_MILLIS + (explicit ? " minutes ago" : " mins");
        } else if (diff < 120 * MINUTE_MILLIS) {
            return explicit ? "1 hour ago" : "1 hr";
        } else if (diff < 24 * HOUR_MILLIS) {
            return diff / HOUR_MILLIS + (explicit ? " hours ago" : " hrs");
        } else if (diff < 48 * HOUR_MILLIS) {
            return explicit ? "yesterday" : "1 day";
        } else if (diff < 365 * DAY_MILLIS) {
            return diff / DAY_MILLIS + (explicit ? " days ago" : " days");
        } else if (diff < 2 * YEAR_MILLIS) {
            return explicit ? "1 year ago" : "1 year";
        } else {
            return diff / YEAR_MILLIS + (explicit ? " years ago" : " years");
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
            if (Utils.shouldUseExternalBrowser(ctx) || !isCustomTabSupported(ctx)) {
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
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            ctx.startActivity(browserIntent);
        } catch (Exception e) {
            //failed for the first time, let's try to guess a fix to the url
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(URLUtil.guessUrl(url)));
                ctx.startActivity(browserIntent);
            } catch (Exception e1) {
                //automated fix didn't work, let's try to do it manually
                try {
                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                        url = "http://" + url;
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    ctx.startActivity(browserIntent);
                } catch (Exception e2) {
                    Toast.makeText(ctx, "Couldn't open link to: " + url, Toast.LENGTH_SHORT).show();
                }
            }
        }

    }

    public static boolean isCustomTabSupported(Context context) {
        return getCustomTabsPackages(context).size() > 0;
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

    public static int[] JSONArrayToIntArray(JSONArray jsonArray){
        int[] intArray = new int[jsonArray.length()];
        for (int i = 0; i < intArray.length; ++i) {
            intArray[i] = jsonArray.optInt(i);
        }
        return intArray;
    }

    public static int getColorViaAttr(Context ctx, int attr) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = ctx.getTheme();
        theme.resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static String thousandSeparated(int n) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(' ');

        DecimalFormat formatter = new DecimalFormat("###,###.##", symbols);
        return formatter.format(new BigDecimal(n).longValue());
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

    @SuppressLint("SimpleDateFormat")
    public static boolean isTimeBetweenTwoTimes(String initialTime, String finalTime, String currentTime) throws ParseException {
        //Start Time
        //all times are from java.util.Date
        Date inTime = new SimpleDateFormat("HH:mm").parse(initialTime);
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(inTime);

        //Current Time
        Date checkTime = new SimpleDateFormat("HH:mm").parse(currentTime);
        Calendar calendar3 = Calendar.getInstance();
        calendar3.setTime(checkTime);

        //End Time
        Date finTime = new SimpleDateFormat("HH:mm").parse(finalTime);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(finTime);

        if (finalTime.compareTo(initialTime) < 0) {
            calendar2.add(Calendar.DATE, 1);
        }

        if (currentTime.compareTo(initialTime) < 0) {
            calendar3.add(Calendar.DATE, 1);
        }

        java.util.Date actualTime = calendar3.getTime();
        return (actualTime.after(calendar1.getTime()) || actualTime.compareTo(calendar1.getTime()) == 0) && actualTime.before(calendar2.getTime());
    }
    
    public static void setNighttimeHours(int fromHour, int fromMinute, int toHour, int toMinute, Context ctx) {
        saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_FROM_HOUR, fromHour + "", true);
        saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_FROM_MINUTE, fromMinute + "", true);
        saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_TO_HOUR, toHour + "", true);
        saveStringToSharedPreferences(ctx, KEY_NIGHTTIME_TO_MINUTE, toMinute + "", true);
    }

    public static int[] getNighttimeHours(Context ctx) {
        return new int[] {
                Integer.parseInt(readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_FROM_HOUR, "21")),
                Integer.parseInt(readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_FROM_MINUTE, "0")),
                Integer.parseInt(readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_TO_HOUR, "6")),
                Integer.parseInt(readStringFromSharedPreferences(ctx, KEY_NIGHTTIME_TO_MINUTE, "0"))
        };
    }

    public static boolean timeInSecondsMoreThanTwoWeeksAgo(int time) {
        return (System.currentTimeMillis() - ((long) time)*1000)/1000/60/60/24 > 14;
    }

    public static float pxFromDp(final Resources resources, final float dp) {
        return dp * resources.getDisplayMetrics().density;
    }

    public static int pxFromDpInt(final Resources resources, final float dp) {
        return Math.round(pxFromDp(resources, dp));
    }

    public static boolean isTablet(Context ctx) {
        return ctx.getResources().getBoolean(R.bool.is_tablet);
    }

    public static int getStatusBarHeight(Resources res) {
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return  res.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static int getNavigationBarHeight(Activity activity) {
        Resources res = activity.getResources();

        int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

}