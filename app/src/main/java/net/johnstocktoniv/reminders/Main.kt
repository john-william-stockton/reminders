package net.johnstocktoniv.reminders

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.alarm.AlarmScheduler
import net.johnstocktoniv.reminders.component.RemindersScreen
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.ReminderDao
import net.johnstocktoniv.reminders.settings.SettingsRepository
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme

class Main : ComponentActivity() {
    private lateinit var dao: ReminderDao

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash theme (Theme.App.Starting, set in the manifest) only needs to apply for
        // the instant the splash is visible; switch back to the app's real theme immediately so
        // the rest of the activity's views don't inherit the splash's window background.
        setTheme(R.style.Theme_Reminders)
        dao = DatabaseProvider.dao(applicationContext)
        val remindersFlow = dao.readAll()
        val defaultReminderTimeFlow = SettingsRepository.defaultReminderTime(applicationContext)
        enableEdgeToEdge()
        requestAlarmPermissionsIfNeeded()

        lifecycleScope.launch { AlarmScheduler.rearmAll(applicationContext) }

        setContent {
            RemindersTheme {
                val reminders by remindersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                val defaultReminderTime by defaultReminderTimeFlow.collectAsStateWithLifecycle()
                RemindersScreen(
                    reminders = reminders,
                    defaultReminderTime = defaultReminderTime,
                    onSaveReminder = { reminder, cronExpressions ->
                        lifecycleScope.launch {
                            val isNew = reminder.id == 0L
                            val newId = dao.saveWithSchedules(reminder, cronExpressions)
                            val saved = if (isNew) reminder.copy(id = newId) else reminder
                            AlarmScheduler.schedule(applicationContext, saved)
                        }
                    },
                    onSaveSettings = { time ->
                        SettingsRepository.setDefaultReminderTime(applicationContext, time)
                        // Reminders without an explicit time use the default; re-arm them so
                        // their alarms move to the new default time.
                        reminders.map { it.reminder }.filter { it.time == null }
                            .forEach { reminder -> AlarmScheduler.schedule(applicationContext, reminder) }
                    },
                    onToggleComplete = { reminderWithSchedules ->
                        val reminder = reminderWithSchedules.reminder
                        if (!reminder.complete) {
                            lifecycleScope.launch {
                                AlarmScheduler.completeAndAdvance(applicationContext, reminderWithSchedules)
                            }
                        } else {
                            val updated = reminder.copy(complete = false)
                            lifecycleScope.launch { dao.upsert(updated) }
                            AlarmScheduler.schedule(applicationContext, updated)
                        }
                    },
                    onDeleteReminder = { reminderWithSchedules ->
                        lifecycleScope.launch { dao.delete(reminderWithSchedules.reminder) }
                        AlarmScheduler.cancel(applicationContext, reminderWithSchedules.reminder.id)
                    },
                    onClearAll = { toDelete ->
                        lifecycleScope.launch { toDelete.forEach { dao.delete(it.reminder) } }
                        toDelete.forEach { AlarmScheduler.cancel(applicationContext, it.reminder.id) }
                    }
                )
            }
        }
    }

    private fun requestAlarmPermissionsIfNeeded() {
        if (!AlarmScheduler.hasPostNotificationsPermission(this)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!AlarmScheduler.canScheduleExactAlarms(this)) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }

        // Android only auto-launches a full-screen alarm when the device is locked; while
        // unlocked it otherwise falls back to a heads-up notification. SYSTEM_ALERT_WINDOW is
        // one of the documented exemptions from that background-activity-start restriction, so
        // granting it lets AlarmReceiver's direct startActivity() reliably show the alarm screen
        // even while the phone is unlocked and in use.
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            )
        }
    }
}
