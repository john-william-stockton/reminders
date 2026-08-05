package net.johnstocktoniv.reminders.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [Reminder::class, ReminderSchedule::class], version = 3)
@ColumnTypeConverters(DateConverters::class)
abstract class RemindersDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
}