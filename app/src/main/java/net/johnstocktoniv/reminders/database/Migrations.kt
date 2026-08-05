package net.johnstocktoniv.reminders.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.LocalDate
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
