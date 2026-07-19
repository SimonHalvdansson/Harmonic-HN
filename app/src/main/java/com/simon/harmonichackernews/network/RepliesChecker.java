package com.simon.harmonichackernews.network;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RepliesChecker {

    public interface Callback {
        void onComplete(boolean success);
    }

    public interface DebugNotificationCallback {
        void onComplete(DebugNotificationResult result);
    }

    public enum DebugNotificationResult {
        SENT,
        NO_RECENT_REPLY,
        USER_NOT_FOUND,
        FAILED
    }

    public static final String CHANNEL_ID = "reply_notifications";

    private static final String KEY_USERNAME = "reply_notifications_username";
    private static final String KEY_LAST_SEEN_ITEM_ID = "reply_notifications_last_seen_item_id";
    private static final String NOTIFICATION_GROUP_KEY = "com.simon.harmonichackernews.REPLY_NOTIFICATIONS";
    private static final int GROUP_NOTIFICATION_ID = 98373;

    private static final int JOB_ID = 98372;
    private static final int MAX_SUBMISSIONS_PER_CHECK = 1000;
    private static final long CHECK_INTERVAL_MILLIS = 30L * 60L * 1000L;
    private static final long CHECK_FLEX_MILLIS = 5L * 60L * 1000L;
    private static final String HN_API_BASE = "https://hacker-news.firebaseio.com/v0/";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void enable(Context ctx, String username, Callback callback) {
        Context appContext = ctx.getApplicationContext();
        String normalizedUsername = normalizeUsername(username);
        if (TextUtils.isEmpty(normalizedUsername)) {
            postCallback(callback, false);
            return;
        }

        EXECUTOR.execute(() -> {
            boolean success = enableBlocking(appContext, normalizedUsername);
            postCallback(callback, success);
        });
    }

    public static void disable(Context ctx) {
        Context appContext = ctx.getApplicationContext();
        SettingsUtils.saveStringToSharedPreferences(appContext, KEY_USERNAME, null);
        SettingsUtils.saveStringToSharedPreferences(appContext, KEY_LAST_SEEN_ITEM_ID, "0");

        JobScheduler scheduler = (JobScheduler) appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    public static void checkNow(Context ctx, Callback callback) {
        Context appContext = ctx.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean success = checkNowBlocking(appContext);
            postCallback(callback, success);
        });
    }

    public static void sendLatestDebugNotification(Context ctx, String username, DebugNotificationCallback callback) {
        Context appContext = ctx.getApplicationContext();
        String normalizedUsername = normalizeUsername(username);
        if (TextUtils.isEmpty(normalizedUsername)) {
            postDebugCallback(callback, DebugNotificationResult.USER_NOT_FOUND);
            return;
        }

        EXECUTOR.execute(() -> {
            DebugNotificationResult result = sendLatestDebugNotificationBlocking(appContext, normalizedUsername);
            postDebugCallback(callback, result);
        });
    }

    public static boolean notificationsAreActive(Context ctx) {
        return !TextUtils.isEmpty(getConfiguredUsername(ctx));
    }

    public static String getConfiguredUsername(Context ctx) {
        return SettingsUtils.readStringFromSharedPreferences(ctx, KEY_USERNAME, "");
    }

    public static void createNotificationChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Replies",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Replies to the configured Hacker News user");

        NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static boolean enableBlocking(Context ctx, String username) {
        try {
            JSONObject user = getJsonObject(HN_API_BASE + "user/" + Uri.encode(username) + ".json");
            if (user == null || !username.equalsIgnoreCase(user.optString("id", ""))) {
                return false;
            }

            int maxItem = getInt(HN_API_BASE + "maxitem.json");
            if (maxItem <= 0) {
                return false;
            }

            createNotificationChannel(ctx);
            SettingsUtils.saveStringToSharedPreferences(ctx, KEY_USERNAME, user.optString("id", username));
            SettingsUtils.saveStringToSharedPreferences(ctx, KEY_LAST_SEEN_ITEM_ID, String.valueOf(maxItem));
            scheduleJob(ctx);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean checkNowBlocking(Context ctx) {
        String username = getConfiguredUsername(ctx);
        if (TextUtils.isEmpty(username)) {
            return true;
        }

        try {
            int previousLastSeenItemId = getLastSeenItemId(ctx);
            int currentMaxItemId = getInt(HN_API_BASE + "maxitem.json");
            if (currentMaxItemId <= 0) {
                return false;
            }

            if (previousLastSeenItemId <= 0) {
                SettingsUtils.saveStringToSharedPreferences(ctx, KEY_LAST_SEEN_ITEM_ID, String.valueOf(currentMaxItemId));
                return true;
            }

            JSONObject user = getJsonObject(HN_API_BASE + "user/" + Uri.encode(username) + ".json");
            if (user == null) {
                return false;
            }

            JSONArray submitted = user.optJSONArray("submitted");
            if (submitted == null || submitted.length() == 0) {
                SettingsUtils.saveStringToSharedPreferences(ctx, KEY_LAST_SEEN_ITEM_ID, String.valueOf(currentMaxItemId));
                return true;
            }

            List<Reply> replies = new ArrayList<>();
            int highestProcessedReplyId = previousLastSeenItemId;
            int checkedSubmissions = 0;

            for (int i = 0; i < submitted.length() && checkedSubmissions < MAX_SUBMISSIONS_PER_CHECK; i++) {
                int parentId = submitted.optInt(i, 0);
                if (parentId <= 0) {
                    continue;
                }

                JSONObject parent = getJsonObject(HN_API_BASE + "item/" + parentId + ".json");
                checkedSubmissions++;
                if (parent == null) {
                    continue;
                }

                int parentTime = parent.optInt("time", 0);
                if (parentTime > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(parentTime)) {
                    break;
                }

                JSONArray kids = parent.optJSONArray("kids");
                if (kids == null) {
                    continue;
                }

                for (int kidIndex = 0; kidIndex < kids.length(); kidIndex++) {
                    int kidId = kids.optInt(kidIndex, 0);
                    if (kidId <= previousLastSeenItemId) {
                        continue;
                    }

                    highestProcessedReplyId = Math.max(highestProcessedReplyId, kidId);
                    JSONObject replyObject = getJsonObject(HN_API_BASE + "item/" + kidId + ".json");
                    Reply reply = parseReply(replyObject, username, parentId);
                    if (reply != null) {
                        replies.add(reply);
                    }
                }
            }

            showNotifications(ctx, replies);

            int newWatermark = Math.max(currentMaxItemId, highestProcessedReplyId);
            SettingsUtils.saveStringToSharedPreferences(ctx, KEY_LAST_SEEN_ITEM_ID, String.valueOf(newWatermark));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static DebugNotificationResult sendLatestDebugNotificationBlocking(Context ctx, String username) {
        try {
            Reply reply = findLatestReplyForUser(username);
            if (reply == null) {
                JSONObject user = getJsonObject(HN_API_BASE + "user/" + Uri.encode(username) + ".json");
                return user == null ? DebugNotificationResult.USER_NOT_FOUND : DebugNotificationResult.NO_RECENT_REPLY;
            }

            createNotificationChannel(ctx);
            showNotification(ctx, reply);
            return DebugNotificationResult.SENT;
        } catch (Exception e) {
            e.printStackTrace();
            return DebugNotificationResult.FAILED;
        }
    }

    private static Reply findLatestReplyForUser(String username) throws Exception {
        JSONObject user = getJsonObject(HN_API_BASE + "user/" + Uri.encode(username) + ".json");
        if (user == null) {
            return null;
        }

        JSONArray submitted = user.optJSONArray("submitted");
        if (submitted == null) {
            return null;
        }

        Reply latestReply = null;
        int checkedSubmissions = 0;

        for (int i = 0; i < submitted.length() && checkedSubmissions < MAX_SUBMISSIONS_PER_CHECK; i++) {
            int parentId = submitted.optInt(i, 0);
            if (parentId <= 0) {
                continue;
            }

            JSONObject parent = getJsonObject(HN_API_BASE + "item/" + parentId + ".json");
            checkedSubmissions++;
            if (parent == null) {
                continue;
            }

            int parentTime = parent.optInt("time", 0);
            if (parentTime > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(parentTime)) {
                break;
            }

            JSONArray kids = parent.optJSONArray("kids");
            if (kids == null) {
                continue;
            }

            for (int kidIndex = 0; kidIndex < kids.length(); kidIndex++) {
                int kidId = kids.optInt(kidIndex, 0);
                if (kidId <= 0) {
                    continue;
                }

                JSONObject replyObject = getJsonObject(HN_API_BASE + "item/" + kidId + ".json");
                Reply reply = parseReply(replyObject, username, parentId);
                if (reply != null && (latestReply == null || reply.id > latestReply.id)) {
                    latestReply = reply;
                }
            }
        }

        return latestReply;
    }

    private static Reply parseReply(JSONObject replyObject, String username, int fallbackParentId) {
        if (replyObject == null || replyObject.optBoolean("deleted") || replyObject.optBoolean("dead")) {
            return null;
        }

        if (!"comment".equals(replyObject.optString("type"))) {
            return null;
        }

        String by = replyObject.optString("by", "");
        if (TextUtils.isEmpty(by) || username.equalsIgnoreCase(by)) {
            return null;
        }

        int time = replyObject.optInt("time", 0);
        if (time > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(time)) {
            return null;
        }

        int id = replyObject.optInt("id", 0);
        if (id <= 0) {
            return null;
        }

        return new Reply(
                id,
                replyObject.optInt("parent", fallbackParentId),
                by,
                htmlToPlainText(replyObject.optString("text", ""))
        );
    }

    private static void showNotification(Context ctx, Reply reply) {
        showNotification(ctx, reply, false);
    }

    private static void showNotifications(Context ctx, List<Reply> replies) {
        if (replies == null || replies.isEmpty()) {
            return;
        }

        if (replies.size() == 1) {
            showNotification(ctx, replies.get(0), false);
            return;
        }

        if (!canPostNotifications(ctx)) {
            return;
        }

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
            for (Reply reply : replies) {
                notificationManager.notify(reply.id, buildReplyNotification(ctx, reply, true).build());
            }

            Reply latestReply = replies.get(0);
            for (Reply reply : replies) {
                if (reply.id > latestReply.id) {
                    latestReply = reply;
                }
            }

            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
                    .setBigContentTitle(replies.size() + " new replies");
            for (Reply reply : replies) {
                style.addLine(reply.by + ": " + reply.text);
            }

            NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_comment)
                    .setLargeIcon(BitmapFactory.decodeResource(ctx.getResources(), R.mipmap.ic_launcher))
                    .setContentTitle(replies.size() + " new replies")
                    .setContentText("New Hacker News replies")
                    .setStyle(style)
                    .setContentIntent(createReplyPendingIntent(ctx, latestReply, GROUP_NOTIFICATION_ID))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setAutoCancel(true);

            notificationManager.notify(GROUP_NOTIFICATION_ID, summaryBuilder.build());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private static void showNotification(Context ctx, Reply reply, boolean grouped) {
        if (!canPostNotifications(ctx)) {
            return;
        }

        try {
            NotificationManagerCompat.from(ctx).notify(reply.id, buildReplyNotification(ctx, reply, grouped).build());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private static NotificationCompat.Builder buildReplyNotification(Context ctx, Reply reply, boolean grouped) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_comment)
                .setLargeIcon(BitmapFactory.decodeResource(ctx.getResources(), R.mipmap.ic_launcher))
                .setContentTitle("New reply from " + reply.by)
                .setContentText(reply.text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(reply.text))
                .setContentIntent(createReplyPendingIntent(ctx, reply, reply.id))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (grouped) {
            builder.setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
        }

        return builder;
    }

    private static boolean canPostNotifications(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    private static PendingIntent createReplyPendingIntent(Context ctx, Reply reply, int requestCode) {
        Uri uri = Uri.parse("https://news.ycombinator.com/item")
                .buildUpon()
                .appendQueryParameter("id", String.valueOf(reply.parentId > 0 ? reply.parentId : reply.id))
                .fragment(String.valueOf(reply.id))
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(ctx, CommentsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
                ctx,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void scheduleJob(Context ctx) {
        JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }

        JobInfo.Builder builder = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(ctx, ReplyNotificationJobService.class)
        )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true);

        builder.setPeriodic(CHECK_INTERVAL_MILLIS, CHECK_FLEX_MILLIS);

        scheduler.schedule(builder.build());
    }

    private static int getLastSeenItemId(Context ctx) {
        try {
            return Integer.parseInt(SettingsUtils.readStringFromSharedPreferences(ctx, KEY_LAST_SEEN_ITEM_ID, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim();
    }

    private static int getInt(String url) throws Exception {
        String response = getString(url);
        if (TextUtils.isEmpty(response)) {
            return 0;
        }
        return Integer.parseInt(response.trim());
    }

    private static JSONObject getJsonObject(String url) throws Exception {
        String response = getString(url);
        if (TextUtils.isEmpty(response) || "null".equals(response.trim())) {
            return null;
        }
        return new JSONObject(response);
    }

    private static String getString(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Harmonic-HN");

        int responseCode = connection.getResponseCode();
        InputStream inputStream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (inputStream == null) {
            connection.disconnect();
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String htmlToPlainText(String html) {
        if (TextUtils.isEmpty(html)) {
            return "Tap to view the reply.";
        }

        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();

        text = text.replaceAll("\\s+", " ").trim();
        if (TextUtils.isEmpty(text)) {
            return "Tap to view the reply.";
        }
        return text.length() > 240 ? String.format(Locale.US, "%s...", text.substring(0, 237)) : text;
    }

    private static void postCallback(Callback callback, boolean success) {
        if (callback == null) {
            return;
        }
        MAIN_HANDLER.post(() -> callback.onComplete(success));
    }

    private static void postDebugCallback(DebugNotificationCallback callback, DebugNotificationResult result) {
        if (callback == null) {
            return;
        }
        MAIN_HANDLER.post(() -> callback.onComplete(result));
    }

    private static class Reply {
        final int id;
        final int parentId;
        final String by;
        final String text;

        Reply(int id, int parentId, String by, String text) {
            this.id = id;
            this.parentId = parentId;
            this.by = by;
            this.text = text;
        }
    }
}
