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
import androidx.core.net.toUri
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R

/** Android scheduler and notification renderer for the application-scoped common runtime. */
internal class AndroidReplyNotificationPlatform(context: Context) : ReplyNotificationPlatform {
    private val appContext = context.applicationContext

    override fun prepareNotifications() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Replies",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Replies to the configured Hacker News user" }
        appContext.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    override fun scheduleChecks(schedule: ReplyNotificationSchedule) {
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)
            as? JobScheduler ?: return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(appContext, ReplyNotificationJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(schedule.intervalMillis, schedule.flexMillis)
            .build()
        scheduler.schedule(job)
    }

    override fun cancelChecks() {
        (appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)
            ?.cancel(JOB_ID)
    }

    override fun publish(batch: ReplyNotificationBatch) {
        if (batch.notifications.isEmpty() || !canPostNotifications()) return
        if (batch.summary == null) {
            publishOne(batch.notifications.single(), grouped = false)
            return
        }
        try {
            val manager = NotificationManagerCompat.from(appContext)
            batch.notifications.forEach { notification ->
                manager.notify(notification.id, buildNotification(notification, true).build())
            }
            val summary = checkNotNull(batch.summary)
            val style = NotificationCompat.InboxStyle().setBigContentTitle(summary.title)
            batch.notifications.forEach { notification ->
                style.addLine(notification.author.orEmpty() + ": " + notification.body)
            }
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_comment)
                .setLargeIcon(
                    BitmapFactory.decodeResource(appContext.resources, R.mipmap.ic_launcher),
                )
                .setContentTitle(summary.title)
                .setContentText(summary.body)
                .setStyle(style)
                .setContentIntent(pendingIntent(summary, GROUP_NOTIFICATION_ID))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setAutoCancel(true)
                .build()
            manager.notify(GROUP_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the common runtime request and native publish.
        }
    }

    private fun publishOne(notification: ReplyNotificationPayload, grouped: Boolean) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(appContext).notify(
                notification.id,
                buildNotification(notification, grouped).build(),
            )
        } catch (_: SecurityException) {
            // Permission can be revoked at any point.
        }
    }

    private fun buildNotification(
        notification: ReplyNotificationPayload,
        grouped: Boolean,
    ): NotificationCompat.Builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_comment)
        .setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.mipmap.ic_launcher))
        .setContentTitle(notification.title)
        .setContentText(notification.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
        .setContentIntent(pendingIntent(notification, notification.id))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .also { builder ->
            if (grouped) {
                builder.setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            }
        }

    private fun pendingIntent(
        notification: ReplyNotificationPayload,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getActivity(
        appContext,
        requestCode,
        Intent(Intent.ACTION_VIEW, notification.deepLink.toUri()).apply {
            setClass(appContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "reply_notifications"
        const val NOTIFICATION_GROUP_KEY =
            "com.simon.harmonichackernews.REPLY_NOTIFICATIONS"
        const val GROUP_NOTIFICATION_ID = ReplyNotificationPresentation.SUMMARY_NOTIFICATION_ID
        const val JOB_ID = 98372
    }
}
