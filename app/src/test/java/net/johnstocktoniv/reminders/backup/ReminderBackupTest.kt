package net.johnstocktoniv.reminders.backup

import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReminderBackupTest {
    private fun reminderWithSchedules(
        id: Long,
        title: String,
        description: String = "",
        date: LocalDate = LocalDate.of(2026, 8, 5),
        time: LocalTime? = LocalTime.of(9, 0),
        complete: Boolean = false,
        cronExpressions: List<String> = emptyList(),
    ): ReminderWithSchedules = ReminderWithSchedules(
        reminder = Reminder(id = id, title = title, date = date, description = description, time = time, complete = complete),
        schedules = cronExpressions.map { ReminderSchedule(reminderId = id, cronExpression = it) }
    )

    @Test
    fun roundTripsASingleReminderWithoutSchedules() {
        val original = listOf(reminderWithSchedules(id = 1, title = "Water plants", description = "Ficus"))

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun roundTripsARecurringReminderWithMultipleSchedules() {
        val original = listOf(
            reminderWithSchedules(
                id = 2,
                title = "Take out trash",
                cronExpressions = listOf("0 8 * * 1", "0 8 * * 4")
            )
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun roundTripsAReminderWithNullTime() {
        val original = listOf(reminderWithSchedules(id = 3, title = "No specific time", time = null))

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun roundTripsTitlesAndDescriptionsContainingQuotesAndNewlines() {
        val original = listOf(
            reminderWithSchedules(
                id = 4,
                title = "Say \"hi\" to Bob",
                description = "Line one\nLine two: with a colon"
            )
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun roundTripsAnEmptyReminderList() {
        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(emptyList())).getOrThrow()

        assertTrue(restored.isEmpty())
    }

    @Test
    fun roundTripsMultipleReminders() {
        val original = listOf(
            reminderWithSchedules(id = 1, title = "First", complete = true),
            reminderWithSchedules(id = 2, title = "Second", cronExpressions = listOf("0 9 * * *"))
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun malformedYamlFailsInsteadOfThrowing() {
        val result = ReminderBackup.fromYaml("not: valid\n  - garbage")

        assertTrue(result.isFailure)
    }

    @Test
    fun fromYamlOfGarbageStringFailsGracefully() {
        val result = ReminderBackup.fromYaml("this is not yaml at all")

        assertTrue(result.isFailure)
    }
}
