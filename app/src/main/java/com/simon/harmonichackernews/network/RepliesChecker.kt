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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils
import kotlinx.coroutines.CancellationException

object RepliesChecker {
    const val CHANNEL_ID: String = "reply_notifications"

    private const val KEY_USERNAME = "reply_notifications_username"
    private const val KEY_LAST_SEEN_ITEM_ID = "reply_notifications_last_seen_item_id"
    private const val NOTIFICATION_GROUP_KEY = "com.simon.harmonichackernews.REPLY_NOTIFICATIONS"
    private const val GROUP_NOTIFICATION_ID = 98373

    private const val JOB_ID = 98372
    private val CHECK_INTERVAL_MILLIS = 30L * 60L * 1000L
    private val CHECK_FLEX_MILLIS = 5L * 60L * 1000L

    suspend fun enable(
        ctx: Context,
        username: String?,
    ): Boolean {
        val appContext = ctx.applicationContext
        val normalizedUsername = ReplyText.normalizeUsername(username)
        if (normalizedUsername.isEmpty()) {
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
        val normalizedUsername = ReplyText.normalizeUsername(username)
        if (normalizedUsername.isEmpty()) {
            return DebugNotificationResult.USER_NOT_FOUND
        }
        return RepliesChecker.sendLatestDebugNotificationInternal(appContext, normalizedUsername)
    }

    fun notificationsAreActive(ctx: Context): Boolean {
        return RepliesChecker.getConfiguredUsername(ctx).isNotBlank()
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
            val baseline = NetworkComponent.replyScanner.initialize(username) ?: return false

            RepliesChecker.createNotificationChannel(ctx)
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_USERNAME,
                baseline.username,
            )
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                baseline.lastSeenItemId.toString(),
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
        if (username.isBlank()) {
            return true
        }

        try {
            val result = NetworkComponent.replyScanner.scan(
                username,
                RepliesChecker.getLastSeenItemId(ctx),
            )
            if (!result.userFound) return false
            RepliesChecker.showNotifications(ctx, result.replies)
            SettingsUtils.saveStringToSharedPreferences(
                ctx,
                RepliesChecker.KEY_LAST_SEEN_ITEM_ID,
                result.lastSeenItemId.toString(),
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
            val result = NetworkComponent.replyScanner.findLatestReply(username)
            val reply = result.reply ?: return if (result.userFound) {
                DebugNotificationResult.NO_RECENT_REPLY
            } else {
                DebugNotificationResult.USER_NOT_FOUND
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

    private fun showNotifications(
        ctx: Context,
        replies: List<HackerNewsReply>,
    ) {
        if (replies.isEmpty()) {
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
        reply: HackerNewsReply,
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
        reply: HackerNewsReply,
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
        reply: HackerNewsReply,
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

    enum class DebugNotificationResult {
        SENT,
        NO_RECENT_REPLY,
        USER_NOT_FOUND,
        FAILED
    }

}
