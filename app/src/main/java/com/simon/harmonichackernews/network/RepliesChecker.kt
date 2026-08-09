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
import android.text.Html
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.CancellationException
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
    private const val HTTP_TIMEOUT_MILLIS = 15_000L
    private val CHECK_INTERVAL_MILLIS = 30L * 60L * 1000L
    private val CHECK_FLEX_MILLIS = 5L * 60L * 1000L
    private const val HN_API_BASE = "https://hacker-news.firebaseio.com/v0/"

    private val HTTP_CLIENT: KtorHttpClient by lazy {
        NetworkComponent.httpClientInstance.newBuilder()
            .readTimeoutMillis(HTTP_TIMEOUT_MILLIS)
            .build()
    }

    suspend fun enable(
        ctx: Context,
        username: String?,
    ): Boolean {
        val appContext = ctx.applicationContext
        val normalizedUsername = normalizeUsername(username)
        if (TextUtils.isEmpty(normalizedUsername)) {
            return false
        }
        return enableInternal(appContext, normalizedUsername)
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

    suspend fun checkNow(ctx: Context): Boolean = checkNowInternal(ctx.applicationContext)

    suspend fun sendLatestDebugNotification(
        ctx: Context,
        username: String?,
    ): DebugNotificationResult {
        val appContext = ctx.applicationContext
        val normalizedUsername = RepliesChecker.normalizeUsername(username)
        if (TextUtils.isEmpty(normalizedUsername)) {
            return DebugNotificationResult.USER_NOT_FOUND
        }
        return RepliesChecker.sendLatestDebugNotificationInternal(appContext, normalizedUsername)
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

    private suspend fun enableInternal(
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
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun checkNowInternal(ctx: Context): Boolean {
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
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun sendLatestDebugNotificationInternal(
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
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            e.printStackTrace()
            return DebugNotificationResult.FAILED
        }
    }

    @Throws(Exception::class)
    private suspend fun findLatestReplyForUser(username: String): Reply? {
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
    private suspend fun getInt(url: String?): Int {
        val response = RepliesChecker.getString(url)
        if (TextUtils.isEmpty(response)) {
            return 0
        }
        return response.trim { it <= ' ' }.toInt()
    }

    @Throws(Exception::class)
    private suspend fun getJsonObject(url: String?): JSONObject? {
        val response = RepliesChecker.getString(url)
        if (TextUtils.isEmpty(response) || "null" == response.trim { it <= ' ' }) {
            return null
        }
        return JSONObject(response)
    }

    @Throws(Exception::class)
    private suspend fun getString(urlString: String?): String {
        val request = HttpRequest.Builder()
            .url(requireNotNull(urlString) { "Reply check URL is required" })
            .get()
            .build()
        return HTTP_CLIENT.newCall(request).await().use { response ->
            response.body.string()
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
