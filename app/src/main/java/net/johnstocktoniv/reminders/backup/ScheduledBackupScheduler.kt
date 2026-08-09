package net.johnstocktoniv.reminders.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import net.johnstocktoniv.reminders.alarm.AlarmScheduler
import net.johnstocktoniv.reminders.alarm.nextOccurrenceForExpression
import java.time.LocalDateTime
import java.time.ZoneId

// Mirrors AlarmScheduler, but for the single scheduled-backup alarm rather than one per reminder
// (there's only ever one, so a fixed request code is fine). A CRON schedule isn't a fixed
// interval AlarmManager can repeat on its own, so ScheduledBackupReceiver reschedules by calling
// schedule() again each time it fires — the same self-rescheduling pattern reminders use to
// advance to their next occurrence.
object ScheduledBackupScheduler {
    const val ACTION_SCHEDULED_BACKUP = "net.johnstocktoniv.reminders.action.SCHEDULED_BACKUP"
    const val MAX_RETAINED_BACKUPS = 7
    private const val REQUEST_CODE = 0

    // Only arms the next occurrence if scheduled backup is enabled and a destination is set;
    // called both when the user turns the feature on and every time the alarm itself fires (to
    // arm the *next* occurrence after this one completes).
    fun schedule(context: Context) {
        if (!ScheduledBackupPrefs.isEnabled(context)) return
        if (ScheduledBackupPrefs.getDestinationTreeUri(context) == null) return

        val cron = ScheduledBackupPrefs.getCronExpression(context)
        val next = nextOccurrenceForExpression(cron, LocalDateTime.now()) ?: return
        val triggerMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        // Same exact-vs-inexact fallback as AlarmScheduler, reusing the same permission reminders
        // already request at launch — inexact alarms are batched/delayed by the OS (more visibly
        // so the shorter the interval between them), which made backups fire inconsistently.
        if (AlarmScheduler.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    // Re-arms after app restart/reboot if the feature is enabled; a no-op otherwise. Safe to call
    // unconditionally from app launch and BootReceiver, same as AlarmScheduler.rearmAll.
    fun rearm(context: Context) = schedule(context)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduledBackupReceiver::class.java).apply {
            action = ACTION_SCHEDULED_BACKUP
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
