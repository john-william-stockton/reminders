package net.johnstocktoniv.reminders.alarm

import net.johnstocktoniv.reminders.database.ReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CronScheduleTest {
    private fun schedule(cron: String) = ReminderSchedule(reminderId = 1L, cronExpression = cron)

    @Test
    fun singleScheduleComputesNextMatchingTime() {
        val after = LocalDateTime.of(2026, 8, 4, 8, 0) // Tuesday
        val next = nextOccurrence(listOf(schedule("0 9 * * *")), after)
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 0), next)
    }

    @Test
    fun overlappingSchedulesCollapseToASingleNextOccurrence() {
        val after = LocalDateTime.of(2026, 8, 4, 8, 0) // Tuesday
        // Both schedules match the same instant: every day at 9am, and every Tuesday at 9am.
        val schedules = listOf(schedule("0 9 * * *"), schedule("0 9 * * 2"))
        val next = nextOccurrence(schedules, after)
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 0), next)
    }

    @Test
    fun earliestScheduleWinsWhenSchedulesDiffer() {
        val after = LocalDateTime.of(2026, 8, 4, 0, 0)
        val schedules = listOf(schedule("0 18 * * *"), schedule("0 9 * * *"))
        val next = nextOccurrence(schedules, after)
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 0), next)
    }

    @Test
    fun invalidCronExpressionsAreIgnoredNotFatal() {
        val after = LocalDateTime.of(2026, 8, 4, 8, 0)
        val schedules = listOf(schedule("not a cron"), schedule("0 9 * * *"))
        val next = nextOccurrence(schedules, after)
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 0), next)
    }

    @Test
    fun nextOccurrenceIsNullWhenNoScheduleParses() {
        val next = nextOccurrence(listOf(schedule("not a cron")), LocalDateTime.now())
        assertNull(next)
    }

    @Test
    fun parseCronOrNullRejectsInvalidExpressions() {
        assertNull(parseCronOrNull("not a cron"))
        assertNotNull(parseCronOrNull("0 9 * * *"))
    }

    @Test
    fun pinnedYearMatchesOnlyThatYear() {
        val after = LocalDateTime.of(2026, 1, 1, 0, 0)
        val next = nextOccurrence(listOf(schedule("30 9 25 12 * 2026")), after)
        assertEquals(LocalDateTime.of(2026, 12, 25, 9, 30), next)
    }

    @Test
    fun pinnedYearAlreadyPassedHasNoNextOccurrence() {
        val after = LocalDateTime.of(2026, 1, 1, 0, 0)
        val next = nextOccurrence(listOf(schedule("30 9 25 12 * 2025")), after)
        assertNull(next)
    }

    @Test
    fun yearFieldIsOptionalAndDefaultsToMatchingEveryYear() {
        assertNotNull(parseCronOrNull("0 9 * * *"))
        assertNotNull(parseCronOrNull("0 9 * * * 2026"))
    }

    @Test
    fun plainRepeatingScheduleIsRecurring() {
        val own = LocalDateTime.of(2026, 8, 4, 9, 0)
        assertTrue(isRecurring(listOf(schedule("0 9 * * *")), own))
    }

    @Test
    fun pinnedYearScheduleIsNotRecurringAfterItsOwnInstant() {
        val own = LocalDateTime.of(2026, 12, 25, 9, 30)
        assertFalse(isRecurring(listOf(schedule("30 9 25 12 * 2026")), own))
    }

    @Test
    fun nextOccurrenceForExpressionComputesNextMatchingTime() {
        val after = LocalDateTime.of(2026, 8, 4, 8, 0) // Tuesday
        val next = nextOccurrenceForExpression("0 */8 * * *", after)
        assertEquals(LocalDateTime.of(2026, 8, 4, 16, 0), next)
    }

    @Test
    fun nextOccurrenceForExpressionIsNullForInvalidExpression() {
        assertNull(nextOccurrenceForExpression("not a cron", LocalDateTime.now()))
    }
}
