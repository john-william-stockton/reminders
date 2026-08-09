package net.johnstocktoniv.reminders

import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderStatus
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import java.time.LocalDate
import java.time.LocalTime

// A once-a-year cron so "next occurrence" assertions can't flake by crossing their own trigger
// boundary between a test computing "now" and the composable under test computing "now" a few
// milliseconds later.
const val STABLE_FUTURE_CRON = "0 0 1 1 *"

fun testReminder(
    id: Long = 0,
    title: String = "Test Reminder",
    description: String = "",
    date: LocalDate = LocalDate.now(),
    time: LocalTime? = null,
    status: ReminderStatus = ReminderStatus.OPEN,
): Reminder = Reminder(
    id = id,
    title = title,
    description = description,
    date = date,
    time = time,
    status = status
)

fun testReminderWithSchedules(
    reminder: Reminder = testReminder(),
    cronExpressions: List<String> = emptyList(),
): ReminderWithSchedules = ReminderWithSchedules(
    reminder = reminder,
    schedules = cronExpressions.mapIndexed { index, expression ->
        ReminderSchedule(id = index + 1L, reminderId = reminder.id, cronExpression = expression)
    }
)
