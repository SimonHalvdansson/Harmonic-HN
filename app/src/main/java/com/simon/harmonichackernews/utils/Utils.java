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

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.simon.harmonichackernews.BuildConfig;
import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Bookmark;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Utils {

    private static final long SECOND_MILLIS = 1000;
    private static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    private static final long YEAR_MILLIS = 365 * DAY_MILLIS;

    public final static String KEY_SHARED_PREFERENCES_CLICKED_IDS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CLICKED_IDS";
    public final static String KEY_SHARED_PREFERENCES_CACHED_STORY = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORY";
    public final static String KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS";
    public final static String GLOBAL_SHARED_PREFERENCES_KEY = "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY";

    public final static String KEY_SHARED_PREFERENCES_BOOKMARKS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS";
    public final static String KEY_SHARED_PREFERENCES_FIRST_TIME = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FIRST_TIME";
    public final static String KEY_SHARED_PREFERENCES_LAST_VERSION = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_LAST_VERSION";

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

    public static String adservers;

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

    public static void cacheStory(Context ctx, int id, String data) {
        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORY + id, data);

        Set<String> cachedStories = SettingsUtils.readStringSetFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS);

        if (cachedStories == null) {
            cachedStories = new HashSet<>();
        }
        // if there already exists a story with the same id, remove it from list of cached since 
        // we're only saving the latest one
        if (!cachedStories.isEmpty()) {
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
            // If we have a lot of stories, lets delete the oldest one
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

        SettingsUtils.saveStringSetToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORIES_STRINGS, cachedStories);
    }

    public static String loadCachedStory(Context ctx, int id) {
        return SettingsUtils.readStringFromSharedPreferences(ctx, KEY_SHARED_PREFERENCES_CACHED_STORY + id);
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

        SettingsUtils.saveStringToSharedPreferences(ctx, KEY_SHARED_PREFERENCES_BOOKMARKS, sb.toString());
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

        for (Bookmark bookmark: bookmarks) {
            if (bookmark.id == id) {
                bookmarks.remove(bookmark);
                break;
            }
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
        if (!TextUtils.isEmpty(prefText)) {
            for (String phrase : prefText.split(",")) {
                phrases.add(phrase.trim());
            }
        }
        return phrases;
    }
    public static ArrayList<String> getFilterDomains(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String prefText = prefs.getString("pref_filter_domains", null);

        ArrayList<String> phrases = new ArrayList<>();
        if (!TextUtils.isEmpty(prefText)) {
            for (String phrase : prefText.split(",")) {
                phrases.add(phrase.trim());
            }
        }
        return phrases;
    }

    public static boolean isFirstAppStart(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        if (sharedPref.getBoolean(KEY_SHARED_PREFERENCES_FIRST_TIME, true) && SettingsUtils.readIntSetFromSharedPreferences(ctx, Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS).isEmpty()) {
            sharedPref.edit().putBoolean(KEY_SHARED_PREFERENCES_FIRST_TIME, false).apply();
            return true;
        }
        return false;
    }

    public static boolean justUpdated(Context ctx) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
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
            return "1 min";
        } else if (diff < 50 * MINUTE_MILLIS) {
            return diff / MINUTE_MILLIS + " mins";
        } else if (diff < 120 * MINUTE_MILLIS) {
            return "1 hr";
        } else if (diff < 24 * HOUR_MILLIS) {
            return diff / HOUR_MILLIS + " hrs";
        } else if (diff < 48 * HOUR_MILLIS) {
            return "1 day";
        } else if (diff < 365 * DAY_MILLIS) {
            return diff / DAY_MILLIS + " days";
        } else if (diff < 2 * YEAR_MILLIS) {
            return "1 year";
        } else {
            return diff / YEAR_MILLIS + " years";
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
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            ctx.startActivity(browserIntent);
        } catch (Exception e) {
            // failed for the first time, let's try to guess a fix to the url
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(URLUtil.guessUrl(url)));
                ctx.startActivity(browserIntent);
            } catch (Exception e1) {
                // automated fix didn't work, let's try to do it manually
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
                    openCommentsActivity(id, context);
                    return;
                }
            }
        }

        Utils.launchCustomTab(context, href);
    }

    public static void openCommentsActivity(int id, Context context) {
        Uri uri = Uri.parse("https://news.ycombinator.com/item").buildUpon()
                .appendQueryParameter("id", String.valueOf(id))
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(context, CommentsActivity.class);
        context.startActivity(intent);
    }

}
