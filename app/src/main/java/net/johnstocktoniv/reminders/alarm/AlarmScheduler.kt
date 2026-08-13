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
import net.johnstocktoniv.reminders.database.ReminderStatus
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

data class AlarmExtras(val reminderId: Long, val title: String, val description: String)

object AlarmScheduler {
    const val ACTION_ALARM = "net.johnstocktoniv.reminders.action.ALARM"
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DESCRIPTION = "description"
    const val SNOOZE_MINUTES = 2L

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
    suspend fun completeAndAdvance(context: Context, current: ReminderWithSchedules) =
        advanceTo(context, current, ReminderStatus.COMPLETE)

    // Marks the current occurrence missed (a manual action distinct from the display-only
    // "Overdue" flag — see ReminderStatus) and advances the series exactly like completing it
    // would: a missed occurrence of a recurring reminder shouldn't block tomorrow's.
    suspend fun missAndAdvance(context: Context, current: ReminderWithSchedules) =
        advanceTo(context, current, ReminderStatus.MISSED)

    private suspend fun advanceTo(context: Context, current: ReminderWithSchedules, status: ReminderStatus) {
        val dao = DatabaseProvider.dao(context)
        val newStreak = nextStreak(current.reminder.streak, status)
        dao.upsert(current.reminder.copy(status = status, streak = newStreak))
        cancel(context, current.reminder.id)
        spawnNextOccurrence(context, current, newStreak)
    }

    // A completion resets a negative (missed) streak to 0 before adding 1; a miss resets a
    // positive (completed) streak to 0 before subtracting 1 — so alternating complete/miss never
    // lets the "wrong direction" count linger into the new streak.
    private fun nextStreak(current: Int, status: ReminderStatus): Int = when (status) {
        ReminderStatus.COMPLETE -> (if (current < 0) 0 else current) + 1
        ReminderStatus.MISSED -> (if (current > 0) 0 else current) - 1
        ReminderStatus.OPEN -> current
    }

    // Detaches a single occurrence from its series: the series continues from the *original*
    // (pre-edit) schedule exactly as if this occurrence had been completed normally, while the
    // occurrence itself keeps the user's title/description edits but is stripped down to a
    // one-off schedule pinned to its own already-fixed date/time — editing an instance shouldn't
    // retroactively change the recurring pattern going forward. Contrast with a "whole series"
    // edit, which is just an ordinary save (Main.kt's onSaveReminder): future spawns already read
    // the live row at completion time, so that path needs no special handling here.
    suspend fun editOccurrence(context: Context, original: ReminderWithSchedules, edited: Reminder) {
        spawnNextOccurrence(context, original)
        val dao = DatabaseProvider.dao(context)
        val time = edited.effectiveTime()
        val pinnedCron = "${time.minute} ${time.hour} ${edited.date.dayOfMonth} ${edited.date.monthValue} * ${edited.date.year}"
        dao.saveWithSchedules(edited, listOf(pinnedCron))
        schedule(context, edited)
    }

    // streak defaults to the current row's own value — used as-is by editOccurrence (which isn't
    // a complete/miss transition, so the streak shouldn't change), while advanceTo() passes the
    // already-updated post-transition value explicitly.
    private suspend fun spawnNextOccurrence(
        context: Context,
        current: ReminderWithSchedules,
        streak: Int = current.reminder.streak
    ) {
        if (current.schedules.isEmpty()) return
        val dao = DatabaseProvider.dao(context)

        val effectiveTime = current.reminder.effectiveTime()
        val after = maxOf(LocalDateTime.now(), current.reminder.date.atTime(effectiveTime))
        val next = nextOccurrence(current.schedules, after) ?: return

        // Guards against re-spawning a duplicate next-occurrence row when a reminder is marked
        // complete, then incomplete, then complete again — the first completion already spawned
        // this occurrence. Only guards this internal spawn path; manual creation via the UI is
        // unaffected and can still produce duplicates if the user wants them.
        val alreadySpawned = dao.findDuplicate(
            current.reminder.title,
            current.reminder.description,
            next.toLocalDate(),
            next.toLocalTime()
        )
        if (alreadySpawned != null) return

        val spawned = Reminder(
            title = current.reminder.title,
            description = current.reminder.description,
            date = next.toLocalDate(),
            time = next.toLocalTime(),
            streak = streak
        )
        val newId = dao.saveWithSchedules(spawned, current.schedules.map { it.cronExpression })
        schedule(context, spawned.copy(id = newId))
    }

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder.id)

        if (reminder.status != ReminderStatus.OPEN) return
        val time = reminder.effectiveTime()

        val triggerMillis = reminder.date.atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) return

        armAlarm(context, reminder, triggerMillis)
    }

    // Re-arms the alarm `minutes` from now without touching the reminder row itself — its
    // date/time (and therefore whether it's still shown as Overdue) stays exactly as it was.
    // Snoozing is purely "ring again shortly", not "this is now due later." `minutes` defaults to
    // SNOOZE_MINUTES but callers can pass a one-off override (see AlarmScreen's long-press) that
    // isn't persisted anywhere — it only applies to this single snooze.
    fun snooze(context: Context, reminder: Reminder, minutes: Long = SNOOZE_MINUTES) {
        cancel(context, reminder.id)
        if (reminder.status != ReminderStatus.OPEN) return

        val triggerMillis = System.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis()
        armAlarm(context, reminder, triggerMillis)
    }

    private fun armAlarm(context: Context, reminder: Reminder, triggerMillis: Long) {
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
