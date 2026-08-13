package com.simon.harmonichackernews.network

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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import androidx.core.net.toUri

object RepliesChecker {
    const val CHANNEL_ID: String = "reply_notifications"

    private const val NOTIFICATION_GROUP_KEY = "com.simon.harmonichackernews.REPLY_NOTIFICATIONS"
    private const val GROUP_NOTIFICATION_ID = ReplyNotificationPresentation.SUMMARY_NOTIFICATION_ID

    private const val JOB_ID = 98372
    suspend fun enable(
        ctx: Context,
        username: String?,
    ): Boolean {
        val appContext = ctx.applicationContext
        return enableInternal(appContext, username)
    }

    fun disable(ctx: Context) {
        val appContext = ctx.applicationContext
        runtime(appContext).disable()
    }

    suspend fun checkNow(ctx: Context): Boolean = checkNowInternal(ctx.applicationContext)

    suspend fun sendLatestDebugNotification(
        ctx: Context,
        username: String?,
    ): DebugNotificationResult {
        val appContext = ctx.applicationContext
        return sendLatestDebugNotificationInternal(appContext, username)
    }

    fun notificationsAreActive(ctx: Context): Boolean {
        return runtime(ctx).isEnabled
    }

    fun getConfiguredUsername(ctx: Context): String {
        return runtime(ctx).configuredUsername
    }

    fun createNotificationChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Replies",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.setDescription("Replies to the configured Hacker News user")

        val notificationManager: NotificationManager? =
            ctx.getSystemService<NotificationManager?>(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private suspend fun enableInternal(
        ctx: Context,
        username: String?
    ): Boolean {
        return when (val result = runtime(ctx).enable(username)) {
            is ReplySubscriptionResult.Enabled -> true
            ReplySubscriptionResult.UserNotFound -> false
            is ReplySubscriptionResult.Failed -> {
                result.cause.printStackTrace()
                false
            }
        }
    }

    private suspend fun checkNowInternal(ctx: Context): Boolean {
        return when (val result = runtime(ctx).checkNow()) {
            ReplyCheckResult.Disabled -> true
            is ReplyCheckResult.Success -> true
            ReplyCheckResult.UserNotFound -> false
            is ReplyCheckResult.Failed -> {
                result.cause.printStackTrace()
                false
            }
        }
    }

    private suspend fun sendLatestDebugNotificationInternal(
        ctx: Context,
        username: String?
    ): DebugNotificationResult {
        return when (val result = runtime(ctx).publishLatest(username)) {
            is LatestReplyLookupResult.Found -> DebugNotificationResult.SENT
            LatestReplyLookupResult.NoRecentReply -> DebugNotificationResult.NO_RECENT_REPLY
            LatestReplyLookupResult.UserNotFound -> DebugNotificationResult.USER_NOT_FOUND
            is LatestReplyLookupResult.Failed -> {
                result.cause.printStackTrace()
                DebugNotificationResult.FAILED
            }
        }
    }

    private fun showNotifications(
        ctx: Context,
        batch: ReplyNotificationBatch,
    ) {
        if (batch.notifications.isEmpty()) return
        if (batch.summary == null) {
            RepliesChecker.showNotification(ctx, batch.notifications.single(), false)
            return
        }

        if (!RepliesChecker.canPostNotifications(ctx)) {
            return
        }

        try {
            val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(ctx)
            for (notification in batch.notifications) {
                notificationManager.notify(
                    notification.id,
                    RepliesChecker.buildReplyNotification(ctx, notification, true).build()
                )
            }
            val summary = checkNotNull(batch.summary)
            val style: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(summary.title)
            for (notification in batch.notifications) {
                style.addLine(notification.author.orEmpty() + ": " + notification.body)
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
                    .setContentTitle(summary.title)
                    .setContentText(summary.body)
                    .setStyle(style)
                    .setContentIntent(
                        RepliesChecker.createReplyPendingIntent(
                            ctx,
                            summary,
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
        notification: ReplyNotificationPayload,
        grouped: Boolean = false,
    ) {
        if (!RepliesChecker.canPostNotifications(ctx)) {
            return
        }

        try {
            NotificationManagerCompat.from(ctx).notify(
                notification.id,
                RepliesChecker.buildReplyNotification(ctx, notification, grouped).build()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun buildReplyNotification(
        ctx: Context,
        notification: ReplyNotificationPayload,
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
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setContentIntent(
                    RepliesChecker.createReplyPendingIntent(ctx, notification, notification.id)
                )
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
        notification: ReplyNotificationPayload,
        requestCode: Int
    ): PendingIntent? {
        val intent: Intent = Intent(Intent.ACTION_VIEW, notification.deepLink.toUri())
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

    private fun scheduleJob(ctx: Context, schedule: ReplyNotificationSchedule) {
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

        builder.setPeriodic(schedule.intervalMillis, schedule.flexMillis)

        scheduler.schedule(builder.build())
    }

    private fun useCase(ctx: Context): ReplyNotificationUseCase = ReplyNotificationUseCase(
        AndroidAppComposition.get(ctx).network.replyScanner,
        AndroidKeyValueStore.global(ctx.applicationContext),
    )

    private fun runtime(ctx: Context): ReplyNotificationRuntime {
        val appContext = ctx.applicationContext
        return ReplyNotificationRuntime(
            useCase(appContext),
            object : ReplyNotificationPlatform {
                override fun prepareNotifications() = createNotificationChannel(appContext)

                override fun scheduleChecks(schedule: ReplyNotificationSchedule) =
                    scheduleJob(appContext, schedule)

                override fun cancelChecks() {
                    val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                        as JobScheduler?
                    scheduler?.cancel(JOB_ID)
                }

                override fun publish(batch: ReplyNotificationBatch) =
                    showNotifications(appContext, batch)
            },
        )
    }

    enum class DebugNotificationResult {
        SENT,
        NO_RECENT_REPLY,
        USER_NOT_FOUND,
        FAILED
    }

}
