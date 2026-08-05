package net.johnstocktoniv.reminders.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.settings.SettingsRepository
import java.time.LocalDateTime
import java.time.ZoneId

data class AlarmExtras(val reminderId: Long, val title: String, val description: String)

object AlarmScheduler {
    const val ACTION_ALARM = "net.johnstocktoniv.reminders.action.ALARM"
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DESCRIPTION = "description"

    fun extrasFrom(intent: Intent): AlarmExtras = AlarmExtras(
        reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L),
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
        description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
    )

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun hasPostNotificationsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    // Alarms scheduled via AlarmManager survive app restarts, but the OS cancels them if the
    // app is force-stopped. Call this on each app launch and after boot to recover from that.
    suspend fun rearmAll(context: Context) {
        DatabaseProvider.dao(context).readAll().first().forEach { schedule(context, it.reminder) }
    }

    // Marks the current occurrence complete and, if it has CRON schedules attached, spawns a new
    // Reminder row for the next occurrence (copying the same schedules onto it) and schedules its
    // alarm. Shared by both completion entry points (the alarm screen and the main list) so the
    // spawn behavior can't drift between them.
    suspend fun completeAndAdvance(context: Context, current: ReminderWithSchedules) {
        val dao = DatabaseProvider.dao(context)
        dao.upsert(current.reminder.copy(complete = true))
        cancel(context, current.reminder.id)
        if (current.schedules.isEmpty()) return

        val effectiveTime = current.reminder.effectiveTime(SettingsRepository.getDefaultReminderTime(context))
        val after = maxOf(LocalDateTime.now(), current.reminder.date.atTime(effectiveTime))
        val next = nextOccurrence(current.schedules, after) ?: return

        val spawned = Reminder(
            title = current.reminder.title,
            description = current.reminder.description,
            date = next.toLocalDate(),
            time = next.toLocalTime()
        )
        val newId = dao.saveWithSchedules(spawned, current.schedules.map { it.cronExpression })
        schedule(context, spawned.copy(id = newId))
    }

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder.id)

        if (reminder.complete) return
        val time = reminder.effectiveTime(SettingsRepository.getDefaultReminderTime(context))

        val triggerMillis = reminder.date.atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, reminder.id) {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DESCRIPTION, reminder.description)
        }

        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(
        context: Context,
        reminderId: Long,
        configure: Intent.() -> Unit = {}
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            configure()
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
