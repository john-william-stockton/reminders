package net.johnstocktoniv.reminders.backup

import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderStatus
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
        status: ReminderStatus = ReminderStatus.OPEN,
        streak: Int = 0,
        cronExpressions: List<String> = emptyList(),
    ): ReminderWithSchedules = ReminderWithSchedules(
        reminder = Reminder(
            id = id,
            title = title,
            date = date,
            description = description,
            time = time,
            status = status,
            streak = streak
        ),
        schedules = cronExpressions.map { ReminderSchedule(reminderId = id, cronExpression = it) }
    )

    @Test
    fun roundTripsASingleReminderWithoutSchedules() {
        val original = listOf(reminderWithSchedules(id = 1, title = "Water plants", description = "Ficus"))

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored.reminders)
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

        assertEquals(original, restored.reminders)
    }

    @Test
    fun roundTripsAReminderWithNullTime() {
        val original = listOf(reminderWithSchedules(id = 3, title = "No specific time", time = null))

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored.reminders)
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

        assertEquals(original, restored.reminders)
    }

    @Test
    fun roundTripsAnEmptyReminderList() {
        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(emptyList())).getOrThrow()

        assertTrue(restored.reminders.isEmpty())
    }

    @Test
    fun roundTripsMultipleReminders() {
        val original = listOf(
            reminderWithSchedules(id = 1, title = "First", status = ReminderStatus.COMPLETE),
            reminderWithSchedules(id = 2, title = "Second", cronExpressions = listOf("0 9 * * *"))
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored.reminders)
    }

    @Test
    fun roundTripsAMissedReminder() {
        val original = listOf(reminderWithSchedules(id = 5, title = "Missed One", status = ReminderStatus.MISSED))

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored.reminders)
    }

    @Test
    fun roundTripsAPositiveAndNegativeStreak() {
        val original = listOf(
            reminderWithSchedules(id = 6, title = "On a streak", streak = 5),
            reminderWithSchedules(id = 7, title = "Fell off", streak = -3)
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(original)).getOrThrow()

        assertEquals(original, restored.reminders)
    }

    // Absent in backups exported before streak tracking existed — stays 0 rather than a parse
    // failure.
    @Test
    fun fromYamlDefaultsMissingStreakFieldToZero() {
        val legacyYaml = """
            reminders:
              - id: 1
                title: "No Streak Field"
                date: 2026-08-05
                time: 09:00:00
                description: []
                status: OPEN
                schedules: []
        """.trimIndent()

        val restored = ReminderBackup.fromYaml(legacyYaml).getOrThrow()

        assertEquals(0, restored.reminders.single().reminder.streak)
    }

    // Backward compatibility with backups exported before the Missed state existed, which wrote
    // `complete: true/false` instead of `status: ...`.
    @Test
    fun fromYamlParsesLegacyCompleteField() {
        val legacyYaml = """
            reminders:
              - id: 1
                title: "Legacy Complete"
                date: 2026-08-05
                time: 09:00:00
                description: []
                complete: true
                schedules: []
        """.trimIndent()

        val restored = ReminderBackup.fromYaml(legacyYaml).getOrThrow()

        assertEquals(ReminderStatus.COMPLETE, restored.reminders.single().reminder.status)
    }

    @Test
    fun fromYamlParsesLegacyIncompleteField() {
        val legacyYaml = """
            reminders:
              - id: 1
                title: "Legacy Incomplete"
                date: 2026-08-05
                time: 09:00:00
                description: []
                complete: false
                schedules: []
        """.trimIndent()

        val restored = ReminderBackup.fromYaml(legacyYaml).getOrThrow()

        assertEquals(ReminderStatus.OPEN, restored.reminders.single().reminder.status)
    }

    @Test
    fun roundTripsScheduledBackupSettingsWithADestination() {
        val settings = ScheduledBackupSettings(
            enabled = true,
            cronExpression = "0 */8 * * *",
            destinationTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        )

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(emptyList(), settings)).getOrThrow()

        assertEquals(settings, restored.scheduledBackupSettings)
    }

    @Test
    fun roundTripsScheduledBackupSettingsWithNoDestinationChosenYet() {
        val settings = ScheduledBackupSettings(enabled = false, cronExpression = "0 */8 * * *", destinationTreeUri = null)

        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(emptyList(), settings)).getOrThrow()

        assertEquals(settings, restored.scheduledBackupSettings)
    }

    // Both the omitted-parameter and legacy-file cases should behave the same way: no
    // scheduledBackup: section at all, rather than defaulting to some guessed settings.
    @Test
    fun toYamlOmitsScheduledBackupSectionWhenSettingsNotProvided() {
        val restored = ReminderBackup.fromYaml(ReminderBackup.toYaml(emptyList())).getOrThrow()

        assertEquals(null, restored.scheduledBackupSettings)
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
