package com.simon.harmonichackernews.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import com.simon.harmonichackernews.serialization.JsonArray as JSONArray
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

object RepliesChecker {
    const val CHANNEL_ID: String = "reply_notifications"

    private const val KEY_USERNAME = "reply_notifications_username"
    private const val KEY_LAST_SEEN_ITEM_ID = "reply_notifications_last_seen_item_id"
    private const val NOTIFICATION_GROUP_KEY = "com.simon.harmonichackernews.REPLY_NOTIFICATIONS"
    private const val GROUP_NOTIFICATION_ID = 98373

    private const val JOB_ID = 98372
    private const val MAX_SUBMISSIONS_PER_CHECK = 1000
    private val CHECK_INTERVAL_MILLIS = 30L * 60L * 1000L
    private val CHECK_FLEX_MILLIS = 5L * 60L * 1000L
    private const val HN_API_BASE = "https://hacker-news.firebaseio.com/v0/"

    private val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())

    fun enable(
        ctx: Context,
        username: String?,
        callback: Callback?
    ) {
        val appContext = ctx.applicationContext
        val normalizedUsername = normalizeUsername(username)
        if (TextUtils.isEmpty(normalizedUsername)) {
            postCallback(callback, false)
            return
        }

        EXECUTOR.execute {
            val success = enableBlocking(appContext, normalizedUsername)
            postCallback(callback, success)
        }
    }

    fun disable(ctx: Context) {
        val appContext = ctx.applicationContext
        SettingsUtils.saveStringToSharedPreferences(appContext, RepliesChecker.KEY_USERNAME, null)
        SettingsUtils.saveStringToSharedPreferences(
            appContext,
            RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
            "0"
        )

        val scheduler: JobScheduler? =
            appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
        if (scheduler != null) {
            scheduler.cancel(RepliesChecker.JOB_ID)
        }
    }

    fun checkNow(ctx: Context, callback: Callback?) {
        val appContext = ctx.applicationContext
        EXECUTOR.execute {
            val success = checkNowBlocking(appContext)
            postCallback(callback, success)
        }
    }

    fun sendLatestDebugNotification(
        ctx: Context,
        username: String?,
        callback: DebugNotificationCallback?
    ) {
        val appContext = ctx.applicationContext
        val normalizedUsername = RepliesChecker.normalizeUsername(username)
        if (TextUtils.isEmpty(normalizedUsername)) {
            RepliesChecker.postDebugCallback(callback, DebugNotificationResult.USER_NOT_FOUND)
            return
        }

        EXECUTOR.execute {
            val result =
                RepliesChecker.sendLatestDebugNotificationBlocking(appContext, normalizedUsername)
            RepliesChecker.postDebugCallback(callback, result)
        }
    }

    fun notificationsAreActive(ctx: Context): Boolean {
        return !TextUtils.isEmpty(RepliesChecker.getConfiguredUsername(ctx))
    }

    fun getConfiguredUsername(ctx: Context): String {
        return SettingsUtils.readStringFromSharedPreferences(ctx, RepliesChecker.KEY_USERNAME, "").orEmpty()
    }

    fun createNotificationChannel(ctx: Context) {
        val channel: NotificationChannel = NotificationChannel(
            RepliesChecker.CHANNEL_ID,
            "Replies",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.setDescription("Replies to the configured Hacker News user")

        val notificationManager: NotificationManager? =
            ctx.getSystemService<NotificationManager?>(NotificationManager::class.java)
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun enableBlocking(
        ctx: Context,
        username: String
    ): Boolean {
        try {
            val user: JSONObject? = RepliesChecker.getJsonObject(
                RepliesChecker.HN_API_BASE + "user/" + Uri.encode(username) + ".json"
            )
            if (user == null || !username.equals(user.optString("id", ""), ignoreCase = true)) {
                return false
            }

            val maxItem = RepliesChecker.getInt(RepliesChecker.HN_API_BASE + "maxitem.json")
            if (maxItem <= 0) {
                return false
            }

            RepliesChecker.createNotificationChannel(ctx)
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_USERNAME,
                user.optString("id", username)
            )
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                maxItem.toString()
            )
            RepliesChecker.scheduleJob(ctx)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun checkNowBlocking(ctx: Context): Boolean {
        val username = RepliesChecker.getConfiguredUsername(ctx)
        if (TextUtils.isEmpty(username)) {
            return true
        }

        try {
            val previousLastSeenItemId = RepliesChecker.getLastSeenItemId(ctx)
            val currentMaxItemId =
                RepliesChecker.getInt(RepliesChecker.HN_API_BASE + "maxitem.json")
            if (currentMaxItemId <= 0) {
                return false
            }

            if (previousLastSeenItemId <= 0) {
                SettingsUtils.saveStringToSharedPreferences(
                    ctx,
                    RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                    currentMaxItemId.toString()
                )
                return true
            }

            val user: JSONObject? = RepliesChecker.getJsonObject(
                RepliesChecker.HN_API_BASE + "user/" + Uri.encode(username) + ".json"
            )
            if (user == null) {
                return false
            }

            val submitted: JSONArray? = user.optJSONArray("submitted")
            if (submitted == null || submitted.length() == 0) {
                SettingsUtils.saveStringToSharedPreferences(
                    ctx,
                    RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                    currentMaxItemId.toString()
                )
                return true
            }

            val replies = mutableListOf<Reply>()
            var highestProcessedReplyId = previousLastSeenItemId
            var checkedSubmissions = 0

            var i = 0
            while (i < submitted.length() && checkedSubmissions < RepliesChecker.MAX_SUBMISSIONS_PER_CHECK) {
                val parentId: Int = submitted.optInt(i, 0)
                if (parentId <= 0) {
                    i++
                    continue
                }

                val parent: JSONObject? =
                    RepliesChecker.getJsonObject(RepliesChecker.HN_API_BASE + "item/" + parentId + ".json")
                checkedSubmissions++
                if (parent == null) {
                    i++
                    continue
                }

                val parentTime: Int = parent.optInt("time", 0)
                if (parentTime > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(
                        parentTime
                    )
                ) {
                    break
                }

                val kids: JSONArray? = parent.optJSONArray("kids")
                if (kids == null) {
                    i++
                    continue
                }

                for (kidIndex in 0..<kids.length()) {
                    val kidId: Int = kids.optInt(kidIndex, 0)
                    if (kidId <= previousLastSeenItemId) {
                        continue
                    }

                    highestProcessedReplyId = max(highestProcessedReplyId, kidId)
                    val replyObject: JSONObject? =
                        RepliesChecker.getJsonObject(RepliesChecker.HN_API_BASE + "item/" + kidId + ".json")
                    val reply = RepliesChecker.parseReply(replyObject, username, parentId)
                    if (reply != null) {
                        replies.add(reply)
                    }
                }
                i++
            }

            RepliesChecker.showNotifications(ctx, replies)

            val newWatermark = max(currentMaxItemId, highestProcessedReplyId)
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                newWatermark.toString()
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun sendLatestDebugNotificationBlocking(
        ctx: Context,
        username: String
    ): DebugNotificationResult {
        try {
            val reply = RepliesChecker.findLatestReplyForUser(username)
            if (reply == null) {
                val user: JSONObject? = RepliesChecker.getJsonObject(
                    RepliesChecker.HN_API_BASE + "user/" + Uri.encode(username) + ".json"
                )
                return if (user == null) DebugNotificationResult.USER_NOT_FOUND else DebugNotificationResult.NO_RECENT_REPLY
            }

            RepliesChecker.createNotificationChannel(ctx)
            showNotification(ctx, reply)
            return DebugNotificationResult.SENT
        } catch (e: Exception) {
            e.printStackTrace()
            return DebugNotificationResult.FAILED
        }
    }

    @Throws(Exception::class)
    private fun findLatestReplyForUser(username: String): Reply? {
        val user: JSONObject? = RepliesChecker.getJsonObject(
            RepliesChecker.HN_API_BASE + "user/" + Uri.encode(username) + ".json"
        )
        if (user == null) {
            return null
        }

        val submitted: JSONArray? = user.optJSONArray("submitted")
        if (submitted == null) {
            return null
        }

        var latestReply: Reply? = null
        var checkedSubmissions = 0

        var i = 0
        while (i < submitted.length() && checkedSubmissions < RepliesChecker.MAX_SUBMISSIONS_PER_CHECK) {
            val parentId: Int = submitted.optInt(i, 0)
            if (parentId <= 0) {
                i++
                continue
            }

            val parent: JSONObject? =
                RepliesChecker.getJsonObject(RepliesChecker.HN_API_BASE + "item/" + parentId + ".json")
            checkedSubmissions++
            if (parent == null) {
                i++
                continue
            }

            val parentTime: Int = parent.optInt("time", 0)
            if (parentTime > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(
                    parentTime
                )
            ) {
                break
            }

            val kids: JSONArray? = parent.optJSONArray("kids")
            if (kids == null) {
                i++
                continue
            }

            for (kidIndex in 0..<kids.length()) {
                val kidId: Int = kids.optInt(kidIndex, 0)
                if (kidId <= 0) {
                    continue
                }

                val replyObject: JSONObject? =
                    RepliesChecker.getJsonObject(RepliesChecker.HN_API_BASE + "item/" + kidId + ".json")
                val reply = RepliesChecker.parseReply(replyObject, username, parentId)
                if (reply != null && (latestReply == null || reply.id > latestReply.id)) {
                    latestReply = reply
                }
            }
            i++
        }

        return latestReply
    }

    private fun parseReply(
        replyObject: JSONObject?,
        username: String,
        fallbackParentId: Int
    ): Reply? {
        if (replyObject == null || replyObject.optBoolean("deleted") || replyObject.optBoolean("dead")) {
            return null
        }

        if ("comment" != replyObject.optString("type")) {
            return null
        }

        val by: String = replyObject.optString("by", "")
        if (TextUtils.isEmpty(by) || username.equals(by, ignoreCase = true)) {
            return null
        }

        val time: Int = replyObject.optInt("time", 0)
        if (time > 0 && Utils.timeInSecondsMoreThanTwoWeeksAgo(
                time
            )
        ) {
            return null
        }

        val id: Int = replyObject.optInt("id", 0)
        if (id <= 0) {
            return null
        }

        return Reply(
            id,
            replyObject.optInt("parent", fallbackParentId),
            by,
            RepliesChecker.htmlToPlainText(replyObject.optString("text", ""))
        )
    }

    private fun showNotifications(
        ctx: Context,
        replies: MutableList<Reply>?
    ) {
        if (replies == null || replies.isEmpty()) {
            return
        }

        if (replies.size == 1) {
            RepliesChecker.showNotification(ctx, replies.get(0), false)
            return
        }

        if (!RepliesChecker.canPostNotifications(ctx)) {
            return
        }

        try {
            val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(ctx)
            for (reply in replies) {
                notificationManager.notify(
                    reply.id,
                    RepliesChecker.buildReplyNotification(ctx, reply, true).build()
                )
            }

            var latestReply = replies.get(0)
            for (reply in replies) {
                if (reply.id > latestReply.id) {
                    latestReply = reply
                }
            }

            val style: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(replies.size.toString() + " new replies")
            for (reply in replies) {
                style.addLine(reply.by + ": " + reply.text)
            }

            val summaryBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(ctx, RepliesChecker.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_comment)
                    .setLargeIcon(
                        BitmapFactory.decodeResource(
                            ctx.getResources(),
                            R.mipmap.ic_launcher
                        )
                    )
                    .setContentTitle(replies.size.toString() + " new replies")
                    .setContentText("New Hacker News replies")
                    .setStyle(style)
                    .setContentIntent(
                        RepliesChecker.createReplyPendingIntent(
                            ctx,
                            latestReply,
                            RepliesChecker.GROUP_NOTIFICATION_ID
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setGroup(RepliesChecker.NOTIFICATION_GROUP_KEY)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setAutoCancel(true)

            notificationManager.notify(RepliesChecker.GROUP_NOTIFICATION_ID, summaryBuilder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun showNotification(
        ctx: Context,
        reply: Reply,
        grouped: Boolean = false
    ) {
        if (!RepliesChecker.canPostNotifications(ctx)) {
            return
        }

        try {
            NotificationManagerCompat.from(ctx).notify(
                reply.id,
                RepliesChecker.buildReplyNotification(ctx, reply, grouped).build()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun buildReplyNotification(
        ctx: Context,
        reply: Reply,
        grouped: Boolean
    ): NotificationCompat.Builder {
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(ctx, RepliesChecker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_comment)
                .setLargeIcon(
                    BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher
                    )
                )
                .setContentTitle("New reply from " + reply.by)
                .setContentText(reply.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reply.text))
                .setContentIntent(RepliesChecker.createReplyPendingIntent(ctx, reply, reply.id))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

        if (grouped) {
            builder.setGroup(RepliesChecker.NOTIFICATION_GROUP_KEY)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        return builder
    }

    private fun canPostNotifications(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    private fun createReplyPendingIntent(
        ctx: Context,
        reply: Reply,
        requestCode: Int
    ): PendingIntent? {
        val uri = Uri.parse("https://news.ycombinator.com/item")
            .buildUpon()
            .appendQueryParameter(
                "id",
                (if (reply.parentId > 0) reply.parentId else reply.id).toString()
            )
            .fragment(reply.id.toString())
            .build()

        val intent: Intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(ctx, MainActivity::class.java)
        intent.setFlags(
            (Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )

        return PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleJob(ctx: Context) {
        val scheduler: JobScheduler? =
            ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
        if (scheduler == null) {
            return
        }

        val builder: JobInfo.Builder = JobInfo.Builder(
            RepliesChecker.JOB_ID,
            ComponentName(ctx, ReplyNotificationJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)

        builder.setPeriodic(RepliesChecker.CHECK_INTERVAL_MILLIS, RepliesChecker.CHECK_FLEX_MILLIS)

        scheduler.schedule(builder.build())
    }

    private fun getLastSeenItemId(ctx: Context): Int {
        try {
            return SettingsUtils.readStringFromSharedPreferences(
                ctx,
                RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                "0"
            ).orEmpty().toInt()
        } catch (e: NumberFormatException) {
            return 0
        }
    }

    private fun normalizeUsername(username: String?): String =
        username.orEmpty().trim { it <= ' ' }

    @Throws(Exception::class)
    private fun getInt(url: String?): Int {
        val response = RepliesChecker.getString(url)
        if (TextUtils.isEmpty(response)) {
            return 0
        }
        return response.trim { it <= ' ' }.toInt()
    }

    @Throws(Exception::class)
    private fun getJsonObject(url: String?): JSONObject? {
        val response = RepliesChecker.getString(url)
        if (TextUtils.isEmpty(response) || "null" == response.trim { it <= ' ' }) {
            return null
        }
        return JSONObject(response)
    }

    @Throws(Exception::class)
    private fun getString(urlString: String?): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Harmonic-HN")

        val responseCode = connection.responseCode
        val inputStream = if (responseCode >= 200 && responseCode < 300)
            connection.inputStream
        else
            connection.errorStream

        if (inputStream == null) {
            connection.disconnect()
            return ""
        }

        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun htmlToPlainText(html: String?): String {
        if (TextUtils.isEmpty(html)) {
            return "Tap to view the reply."
        }

        var text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

        text = text.replace("\\s+".toRegex(), " ").trim { it <= ' ' }
        if (TextUtils.isEmpty(text)) {
            return "Tap to view the reply."
        }
        return if (text.length > 240) text.substring(0, 237) + "..." else text
    }

    private fun postCallback(callback: Callback?, success: Boolean) {
        if (callback == null) {
            return
        }
        MAIN_HANDLER.post { callback.onComplete(success) }
    }

    private fun postDebugCallback(
        callback: DebugNotificationCallback?,
        result: DebugNotificationResult
    ) {
        if (callback == null) {
            return
        }
        MAIN_HANDLER.post { callback.onComplete(result) }
    }

    fun interface Callback {
        fun onComplete(success: Boolean)
    }

    fun interface DebugNotificationCallback {
        fun onComplete(result: DebugNotificationResult)
    }

    enum class DebugNotificationResult {
        SENT,
        NO_RECENT_REPLY,
        USER_NOT_FOUND,
        FAILED
    }

    private class Reply(
        val id: Int,
        val parentId: Int,
        val by: String?,
        val text: String?
    )
}
