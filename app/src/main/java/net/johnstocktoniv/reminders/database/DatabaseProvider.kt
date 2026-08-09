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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                migration3To4(context.applicationContext),
                MIGRATION_4_5,
                MIGRATION_5_6
            )
            .build()
            .also { instance = it }
    }

    fun dao(context: Context): ReminderDao = get(context).reminderDao()

    // Test-only seam: lets an instrumented test that launches the real Main Activity swap in an
    // in-memory database before Main.onCreate() runs, so the test never touches the real on-disk
    // reminders.db — the same safety ReminderDaoTest already gets by building its own in-memory
    // Room instance directly, extended to cover Main's own Activity wiring too.
    fun overrideForTesting(db: RemindersDatabase) {
        instance = db
    }

    fun clearOverrideForTesting() {
        instance = null
    }
}