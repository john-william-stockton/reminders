package net.johnstocktoniv.reminders.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.johnstocktoniv.reminders.testReminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
