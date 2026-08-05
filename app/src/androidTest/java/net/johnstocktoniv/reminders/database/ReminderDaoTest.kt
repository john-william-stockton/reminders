package net.johnstocktoniv.reminders.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.johnstocktoniv.reminders.testReminder
import net.johnstocktoniv.reminders.testReminderWithSchedules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

// findDuplicate() backs the completeAndAdvance() guard against spawning a second copy of a
// reminder it already spawned (complete -> incomplete -> complete on the same recurring
// reminder). See AlarmScheduler.completeAndAdvance.
@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {
    private lateinit var db: RemindersDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<RemindersDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        dao = db.reminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findDuplicate_matchesIdenticalIncompleteReminder() = runBlocking {
        val date = LocalDate.of(2027, 1, 1)
        val time = LocalTime.of(9, 0)
        dao.upsert(testReminder(title = "Water plants", description = "Ficus", date = date, time = time))

        val found = dao.findDuplicate("Water plants", "Ficus", date, time)

        assertEquals("Water plants", found?.title)
    }

    @Test
    fun findDuplicate_ignoresCompletedReminders() = runBlocking {
        val date = LocalDate.of(2027, 1, 1)
        val time = LocalTime.of(9, 0)
        dao.upsert(testReminder(title = "Water plants", date = date, time = time, complete = true))

        val found = dao.findDuplicate("Water plants", "", date, time)

        assertNull(found)
    }

    @Test
    fun findDuplicate_matchesNullTime() = runBlocking {
        val date = LocalDate.of(2027, 1, 1)
        dao.upsert(testReminder(title = "Water plants", date = date, time = null))

        val found = dao.findDuplicate("Water plants", "", date, null)

        assertEquals("Water plants", found?.title)
    }

    @Test
    fun findDuplicate_returnsNullWhenNoMatch() = runBlocking {
        val date = LocalDate.of(2027, 1, 1)
        dao.upsert(testReminder(title = "Water plants", date = date, time = LocalTime.of(9, 0)))

        val found = dao.findDuplicate("Water plants", "", date, LocalTime.of(10, 0))

        assertNull(found)
    }

    // restoreAll() backs the backup-restore flow (see ReminderBackup/Main's openBackupDocument):
    // it wipes the current database and replaces it wholesale with the given rows.
    @Test
    fun restoreAll_replacesExistingRemindersWithGivenRows() = runBlocking {
        dao.upsert(testReminder(title = "Old reminder", complete = true))

        val restored = listOf(
            testReminderWithSchedules(testReminder(id = 10, title = "Restored One")),
            testReminderWithSchedules(
                testReminder(id = 11, title = "Restored Two"),
                cronExpressions = listOf("0 9 * * *")
            )
        )
        dao.restoreAll(restored)

        val all = dao.readAll().first()
        assertEquals(setOf("Restored One", "Restored Two"), all.map { it.reminder.title }.toSet())
        assertTrue(all.none { it.reminder.title == "Old reminder" })
    }

    @Test
    fun restoreAll_preservesReminderIdsAndSchedules() = runBlocking {
        val restored = listOf(
            testReminderWithSchedules(
                testReminder(id = 42, title = "Water plants"),
                cronExpressions = listOf("0 9 * * *", "0 9 * * 3")
            )
        )
        dao.restoreAll(restored)

        val found = dao.getById(42)
        assertEquals("Water plants", found?.reminder?.title)
        assertEquals(setOf("0 9 * * *", "0 9 * * 3"), found?.schedules?.map { it.cronExpression }?.toSet())
    }

    @Test
    fun restoreAll_withEmptyListClearsTheDatabase() = runBlocking {
        dao.upsert(testReminder(title = "Will be cleared"))

        dao.restoreAll(emptyList())

        assertTrue(dao.readAll().first().isEmpty())
    }
}
