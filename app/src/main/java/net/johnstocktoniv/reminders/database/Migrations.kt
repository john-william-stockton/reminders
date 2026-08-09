package net.johnstocktoniv.reminders.database

import android.content.Context
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Early builds stored `date` using this display format (e.g. "Tuesday, August 4, 2026") instead
// of ISO. That format is neither parseable by DateConverters nor sortable as text, so this
// migration rewrites any such legacy rows to the ISO storage format. Frozen here rather than
// reusing the live UI dateFormatter, since a future display-format change must not silently
// change what this migration recognizes as legacy data.
private val legacyDisplayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

val MIGRATION_1_2 = Migration(1, 2) { connection: SQLiteConnection ->
    val legacyDates = mutableListOf<Pair<Long, String>>()
    connection.prepare("SELECT id, date FROM reminders").use { statement ->
        while (statement.step()) {
            val date = statement.getText(1)
            if (runCatching { LocalDate.parse(date, storageDateFormatter) }.isFailure) {
                legacyDates += statement.getLong(0) to date
            }
        }
    }

    for ((id, date) in legacyDates) {
        val fixed = LocalDate.parse(date, legacyDisplayDateFormatter).format(storageDateFormatter)
        connection.prepare("UPDATE reminders SET date = ? WHERE id = ?").use { statement ->
            statement.bindText(1, fixed)
            statement.bindLong(2, id)
            statement.step()
        }
    }
}

// Purely additive: introduces reminder_schedules for recurring (CRON) reminders. No existing
// data needs transforming.
val MIGRATION_2_3 = Migration(2, 3) { connection: SQLiteConnection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS `reminder_schedules` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`reminderId` INTEGER NOT NULL, " +
            "`cronExpression` TEXT NOT NULL, " +
            "FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_reminder_schedules_reminderId` ON `reminder_schedules` (`reminderId`)"
    )
}

// Date/time input was removed in favor of always requiring a CRON schedule (one-off reminders
// are now expressed as a schedule pinned to a single instant via the optional YEAR field — see
// CronSchedule.kt), so every reminder needs at least one reminder_schedules row. This backfills
// one for any reminder that predates that change: a "minute hour day month * year" schedule
// computed from that row's own date/time columns, falling back to the (now-removed) default-
// reminder-time setting's last saved value for a null time, read directly from its SharedPreferences
// file rather than through SettingsRepository since that object no longer exists.
fun migration3To4(context: Context) = Migration(3, 4) { connection: SQLiteConnection ->
    val prefs = context.applicationContext.getSharedPreferences("reminders_settings", Context.MODE_PRIVATE)
    val defaultMinutes = prefs.getInt("default_reminder_time_minutes", -1)
    val defaultTime = if (defaultMinutes < 0) LocalTime.of(8, 0) else LocalTime.of(defaultMinutes / 60, defaultMinutes % 60)

    val toBackfill = mutableListOf<Pair<Long, String>>()
    connection.prepare(
        "SELECT id, date, time FROM reminders WHERE id NOT IN (SELECT DISTINCT reminderId FROM reminder_schedules)"
    ).use { statement ->
        while (statement.step()) {
            val id = statement.getLong(0)
            val date = LocalDate.parse(statement.getText(1), storageDateFormatter)
            val time = if (statement.isNull(2)) defaultTime else LocalTime.parse(statement.getText(2), storageTimeFormatter)
            val cron = "${time.minute} ${time.hour} ${date.dayOfMonth} ${date.monthValue} * ${date.year}"
            toBackfill += id to cron
        }
    }

    for ((id, cron) in toBackfill) {
        connection.prepare("INSERT INTO reminder_schedules (reminderId, cronExpression) VALUES (?, ?)").use { statement ->
            statement.bindLong(1, id)
            statement.bindText(2, cron)
            statement.step()
        }
    }
}

// Replaces the old complete: Boolean with a status: TEXT column (OPEN/COMPLETE/MISSED — see
// ReminderStatus) so a reminder can be explicitly marked Missed, not just complete/incomplete.
// SQLite's ALTER TABLE can't retype/drop a column directly pre-3.35, but the bundled driver here
// is recent enough to support DROP COLUMN, so this adds+backfills+drops in one pass rather than
// doing a full table rebuild.
val MIGRATION_4_5 = Migration(4, 5) { connection: SQLiteConnection ->
    connection.execSQL("ALTER TABLE reminders ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'")
    connection.execSQL("UPDATE reminders SET status = CASE WHEN complete = 1 THEN 'COMPLETE' ELSE 'OPEN' END")
    connection.execSQL("ALTER TABLE reminders DROP COLUMN complete")
}

// Purely additive: introduces the completion-streak counter (see AlarmScheduler.nextStreak).
// Existing rows default to 0 — an unknown/reset streak rather than guessing at their history.
val MIGRATION_5_6 = Migration(5, 6) { connection: SQLiteConnection ->
    connection.execSQL("ALTER TABLE reminders ADD COLUMN streak INTEGER NOT NULL DEFAULT 0")
}
