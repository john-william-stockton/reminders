package net.johnstocktoniv.reminders.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.johnstocktoniv.reminders.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return

        val extras = AlarmScheduler.extrasFrom(intent)
        if (extras.reminderId == -1L) return

        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "reminders:alarm:${extras.reminderId}"
            )
        wakeLock.acquire(10_000)

        try {
            ensureChannel(context)

            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, extras.reminderId)
                putExtra(AlarmScheduler.EXTRA_TITLE, extras.title)
                putExtra(AlarmScheduler.EXTRA_DESCRIPTION, extras.description)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                extras.reminderId.toInt(),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // With SYSTEM_ALERT_WINDOW granted (requested in Main.requestAlarmPermissionsIfNeeded),
            // the direct startActivity() below reliably shows the alarm screen on its own, so the
            // full-screen-intent notification would only add a redundant heads-up banner on top of
            // it. Without that permission, startActivity() is a no-op due to background-activity-
            // start restrictions, so the notification is the only way to auto-launch on a locked
            // device or fall back to a heads-up banner while unlocked — post it only in that case.
            if (!Settings.canDrawOverlays(context)) {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_alarm)
                    .setContentTitle(extras.title.ifBlank { "Reminder" })
                    .setContentText(extras.description)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .build()

                // Kept inline (rather than a shared permission-check helper) so Android Lint's
                // MissingPermission check can see the guard right at the notify() call site.
                val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (canPostNotifications) {
                    NotificationManagerCompat.from(context).notify(extras.reminderId.toInt(), notification)
                }
            }

            runCatching { context.startActivity(fullScreenIntent) }
        } finally {
            wakeLock.release()
        }
    }

    private fun ensureChannel(context: Context) {
        if (channelEnsured) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Reminder alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alerts for reminders"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        channelEnsured = true
    }

    companion object {
        const val CHANNEL_ID = "reminder_alarms"
        @Volatile private var channelEnsured = false
    }
}
