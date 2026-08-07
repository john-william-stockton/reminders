package net.johnstocktoniv.reminders.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

object DatabaseProvider {
    @Volatile private var instance: RemindersDatabase? = null

    fun get(context: Context): RemindersDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder<RemindersDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath("reminders.db").absolutePath
        )
            .setDriver(AndroidSQLiteDriver())        // required in Room 3
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, migration3To4(context.applicationContext))
            .build()
            .also { instance = it }
    }

    fun dao(context: Context): ReminderDao = get(context).reminderDao()
}