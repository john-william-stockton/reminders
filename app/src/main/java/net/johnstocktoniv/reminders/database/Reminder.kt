package net.johnstocktoniv.reminders.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Note: "yyyy" (calendar year), not "YYYY" (week-based year) — the latter can roll over a day
// early/late around the new year and silently show the wrong year.
val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy")
val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun parseDateOrNull(input: String): LocalDate? = runCatching { LocalDate.parse(input, dateFormatter) }.getOrNull()
fun parseTimeOrNull(input: String): LocalTime? = runCatching { LocalTime.parse(input, timeFormatter) }.getOrNull()

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val time: LocalTime? = null,
    val complete: Boolean = false
) {
    fun effectiveTime(default: LocalTime): LocalTime = time ?: default
}

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(reminder: Reminder): Long

    @Upsert
    suspend fun upsertSchedules(schedules: List<ReminderSchedule>)

    @Query("DELETE FROM reminder_schedules WHERE reminderId = :reminderId")
    suspend fun deleteSchedulesFor(reminderId: Long)

    @Transaction
    @Query("SELECT * FROM reminders ORDER BY date, time")
    fun readAll(): Flow<List<ReminderWithSchedules>>

    @Transaction
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderWithSchedules?

    @Delete
    suspend fun delete(reminder: Reminder)

    // Used only to guard against completeAndAdvance() spawning a second copy of a reminder it
    // already spawned (e.g. complete -> incomplete -> complete on the same recurring reminder).
    // Not used to block manual creation through the UI, which should still allow duplicates.
    @Query(
        "SELECT * FROM reminders WHERE title = :title AND description = :description " +
            "AND date = :date AND time IS :time AND complete = 0 LIMIT 1"
    )
    suspend fun findDuplicate(title: String, description: String, date: LocalDate, time: LocalTime?): Reminder?

    // Replaces a reminder's full schedule set wholesale, matching how the rest of the app treats
    // saves as whole-object replacement (e.g. the reminder dialog's save flow).
    @Transaction
    suspend fun saveWithSchedules(reminder: Reminder, cronExpressions: List<String>): Long {
        // Upsert returns the new row id on insert, but -1L on the update path (see
        // EntityUpsertAdapter.upsertAndReturnId) — for an existing reminder its real id is
        // already known, so use that instead of trusting the return value.
        val newRowId = upsert(reminder)
        val id = if (reminder.id != 0L) reminder.id else newRowId
        deleteSchedulesFor(id)
        if (cronExpressions.isNotEmpty()) {
            upsertSchedules(cronExpressions.map { ReminderSchedule(reminderId = id, cronExpression = it) })
        }
        return id
    }

    @Insert
    suspend fun insertReminder(reminder: Reminder)

    @Insert
    suspend fun insertSchedules(schedules: List<ReminderSchedule>)

    // Cascades to reminder_schedules via its ON DELETE CASCADE foreign key.
    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()

    // Wipes the database and replaces it wholesale with the given rows, for backup restore.
    // Reminder ids are preserved from the backup (rather than left to autogenerate) so restored
    // reminders keep the same AlarmManager request codes (keyed off reminderId) they had when
    // exported; schedule ids are left to autogenerate since nothing references them by value.
    @Transaction
    suspend fun restoreAll(reminders: List<ReminderWithSchedules>) {
        deleteAllReminders()
        reminders.forEach { (reminder, schedules) ->
            insertReminder(reminder)
            if (schedules.isNotEmpty()) {
                insertSchedules(schedules.map { it.copy(id = 0) })
            }
        }
    }
}