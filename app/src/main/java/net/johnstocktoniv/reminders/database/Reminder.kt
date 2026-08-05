package net.johnstocktoniv.reminders.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
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
val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

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
}