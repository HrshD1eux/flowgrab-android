package hrshd1eux.flowgrab.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import hrshd1eux.flowgrab.App.Companion.context
import hrshd1eux.flowgrab.NotificationActionReceiver
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.ACTION_CANCEL_TASK
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.ACTION_ERROR_REPORT
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.ACTION_KEY
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.ERROR_REPORT_KEY
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.NOTIFICATION_ID_KEY
import hrshd1eux.flowgrab.NotificationActionReceiver.Companion.TASK_ID_KEY
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.util.PreferenceUtil.getBoolean

private const val TAG = "NotificationUtil"

@SuppressLint("StaticFieldLeak")
object NotificationUtil {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private const val PROGRESS_MAX = 100
    private const val PROGRESS_INITIAL = 0
    private const val CHANNEL_ID = "download_notification"
    private const val SERVICE_CHANNEL_ID = "download_service"
    private const val NOTIFICATION_GROUP_ID = "flowgrab.download.notification"
    private const val DEFAULT_NOTIFICATION_ID = 100
    const val SERVICE_NOTIFICATION_ID = 123
    private lateinit var serviceNotification: Notification

    //    private var builder =
    //        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_flowgrab)
    private val commandNotificationBuilder =
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_flowgrab)
    private val lastNotificationTimeMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private const val NOTIFICATION_THROTTLE_MS = 400L

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channelGroup =
            NotificationChannelGroup(NOTIFICATION_GROUP_ID, context.getString(R.string.download))
        val channel =
            NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                group = NOTIFICATION_GROUP_ID
            }
        val serviceChannel =
            NotificationChannel(SERVICE_CHANNEL_ID, name, importance).apply {
                description = context.getString(R.string.service_title)
                group = NOTIFICATION_GROUP_ID
            }
        notificationManager.createNotificationChannelGroup(channelGroup)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun notifyProgress(
        title: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        progress: Int = PROGRESS_INITIAL,
        taskId: String? = null,
        text: String? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val now = System.currentTimeMillis()
        val lastTime = lastNotificationTimeMap[notificationId] ?: 0L
        if (progress in 1..99 && now - lastTime < NOTIFICATION_THROTTLE_MS) {
            return
        }
        lastNotificationTimeMap[notificationId] = now

        val pendingIntent =
            taskId?.let {
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(TASK_ID_KEY, taskId)
                    .putExtra(NOTIFICATION_ID_KEY, notificationId)
                    .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)
                    .run {
                        PendingIntent.getBroadcast(
                            context.applicationContext,
                            notificationId,
                            this,
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
            }

        val progressText = if (progress in 0..100) "$progress%  ${text.orEmpty()}".trim() else text

        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flowgrab)
            .setContentTitle(title)
            .setContentText(progressText)
            .setProgress(PROGRESS_MAX, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progressText))
            .run {
                pendingIntent?.let {
                    addAction(R.drawable.outline_cancel_24, context.getString(R.string.cancel), it)
                }
                notificationManager.notify(notificationId, build())
            }
    }

    fun finishNotification(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
        intent: PendingIntent? = null,
    ) {
        Log.d(TAG, "finishNotification: ")
        lastNotificationTimeMap.remove(notificationId)
        notificationManager.cancel(notificationId)
        if (!NOTIFICATION.getBoolean()) return

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flowgrab)
                .setContentText(text)
                .setOngoing(false)
                .setAutoCancel(true)
        title?.let { builder.setContentTitle(title) }
        intent?.let { builder.setContentIntent(intent) }
        notificationManager.notify(notificationId, builder.build())
    }

    fun finishNotificationForCustomCommands(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
    ) {
        //        notificationManager.cancel(notificationId)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flowgrab)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(null)
        title?.let { builder.setContentTitle(title) }

        notificationManager.notify(notificationId, builder.build())
    }

    fun makeServiceNotification(intent: PendingIntent, text: String? = null): Notification {
        serviceNotification =
            NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flowgrab)
                .setContentTitle(context.getString(R.string.service_title))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(intent)
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        return serviceNotification
    }

    fun updateServiceNotificationForPlaylist(index: Int, itemCount: Int) {
        serviceNotification =
            NotificationCompat.Builder(context, serviceNotification)
                .setContentTitle(context.getString(R.string.service_title) + " ($index/$itemCount)")
                .build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, serviceNotification)
    }

    fun cancelNotification(notificationId: Int) {
        lastNotificationTimeMap.remove(notificationId)
        notificationManager.cancel(notificationId)
    }

    const val UPDATE_NOTIFICATION_ID = 999

    fun notifyUpdateProgress(version: String, progress: Int) {
        val now = System.currentTimeMillis()
        val lastTime = lastNotificationTimeMap[UPDATE_NOTIFICATION_ID] ?: 0L
        if (progress in 1..99 && now - lastTime < NOTIFICATION_THROTTLE_MS) {
            return
        }
        lastNotificationTimeMap[UPDATE_NOTIFICATION_ID] = now

        val progressText = if (progress >= 0) "$progress%" else context.getString(R.string.downloading_indicator_text)
        val title = context.getString(R.string.downloading_update, version)

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flowgrab)
                .setContentTitle(title)
                .setContentText(progressText)
                .setProgress(PROGRESS_MAX, if (progress >= 0) progress else 0, progress < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)

        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
    }

    fun notifyUpdateFinished(version: String, intent: PendingIntent?) {
        lastNotificationTimeMap.remove(UPDATE_NOTIFICATION_ID)
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flowgrab)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.tap_to_install_update, version))
                .setOngoing(false)
                .setAutoCancel(true)

        intent?.let { builder.setContentIntent(it) }
        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
    }

    fun cancelUpdateNotification() {
        lastNotificationTimeMap.remove(UPDATE_NOTIFICATION_ID)
        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

    fun notifyError(
        title: String,
        textId: Int = R.string.download_error_msg,
        notificationId: Int,
        report: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent()
                .setClass(context, NotificationActionReceiver::class.java)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ERROR_REPORT_KEY, report)
                .putExtra(ACTION_KEY, ACTION_ERROR_REPORT)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT,
            )
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flowgrab)
            .setContentTitle(title)
            .setContentText(context.getString(textId))
            .setOngoing(false)
            .addAction(
                R.drawable.outline_content_copy_24,
                context.getString(R.string.copy_error_report),
                pendingIntent,
            )
            .run {
                notificationManager.cancel(notificationId)
                notificationManager.notify(notificationId, build())
            }
    }

    fun makeNotificationForCustomCommand(
        notificationId: Int,
        taskId: String,
        progress: Int,
        text: String? = null,
        templateName: String,
        taskUrl: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent(context.applicationContext, NotificationActionReceiver::class.java)
                .putExtra(TASK_ID_KEY, taskId)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                notificationId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flowgrab)
            .setContentTitle(
                "[${templateName}_${taskUrl}] " +
                    context.getString(R.string.execute_command_notification)
            )
            .setContentText(text)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, progress == -1)
            .addAction(
                R.drawable.outline_cancel_24,
                context.getString(R.string.cancel),
                pendingIntent,
            )
            .run { notificationManager.notify(notificationId, build()) }
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT <= 24) true
        else notificationManager.areNotificationsEnabled()
    }
}
