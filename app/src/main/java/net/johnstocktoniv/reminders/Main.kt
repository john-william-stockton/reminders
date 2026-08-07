package net.johnstocktoniv.reminders

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.alarm.AlarmScheduler
import net.johnstocktoniv.reminders.backup.ReminderBackup
import net.johnstocktoniv.reminders.component.RemindersScreen
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.ReminderDao
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme

class Main : ComponentActivity() {
    private lateinit var dao: ReminderDao

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val createBackupDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-yaml")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val yaml = ReminderBackup.toYaml(dao.readAll().first())
                contentResolver.openOutputStream(uri)?.use { it.write(yaml.toByteArray()) }
                Toast.makeText(this@Main, "Reminders exported", Toast.LENGTH_SHORT).show()
            }
        }

    private val openBackupDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val yaml = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (yaml == null) {
                Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            ReminderBackup.fromYaml(yaml).fold(
                onSuccess = { restored ->
                    lifecycleScope.launch {
                        dao.readAll().first().forEach { AlarmScheduler.cancel(applicationContext, it.reminder.id) }
                        dao.restoreAll(restored)
                        AlarmScheduler.rearmAll(applicationContext)
                        Toast.makeText(this@Main, "Reminders restored", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = {
                    Toast.makeText(this, "That file isn't a valid backup", Toast.LENGTH_SHORT).show()
                }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash theme (Theme.App.Starting, set in the manifest) only needs to apply for
        // the instant the splash is visible; switch back to the app's real theme immediately so
        // the rest of the activity's views don't inherit the splash's window background.
        setTheme(R.style.Theme_Reminders)
        dao = DatabaseProvider.dao(applicationContext)
        val remindersFlow = dao.readAll()
        enableEdgeToEdge()
        requestAlarmPermissionsIfNeeded()

        lifecycleScope.launch { AlarmScheduler.rearmAll(applicationContext) }

        setContent {
            RemindersTheme {
                val reminders by remindersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                RemindersScreen(
                    reminders = reminders,
                    onSaveReminder = { reminder, cronExpressions ->
                        val isNew = reminder.id == 0L
                        val newId = dao.saveWithSchedules(reminder, cronExpressions)
                        val saved = if (isNew) reminder.copy(id = newId) else reminder
                        AlarmScheduler.schedule(applicationContext, saved)
                        // saveWithSchedules() only guarantees the write has committed, not that
                        // this composition's `reminders` state (what a reopened edit dialog reads
                        // its initial fields from) has caught up: readAll()'s Flow re-queries
                        // asynchronously off Room's own invalidation dispatch, so a second,
                        // independent collection of it (e.g. remindersFlow.first { ... }) can
                        // settle from a fresh query before *this* collector's
                        // collectAsStateWithLifecycle-backed state actually recomposes with the
                        // new value. Waiting on `reminders` itself via snapshotFlow ties the wait
                        // to that exact state instead.
                        snapshotFlow { reminders }.first { list ->
                            list.find { it.reminder.id == saved.id }?.schedules?.map { it.cronExpression } == cronExpressions
                        }
                    },
                    onEditOccurrence = { original, edited ->
                        AlarmScheduler.editOccurrence(applicationContext, original, edited)
                        // Same reasoning as onSaveReminder above: wait for the reactive `reminders`
                        // state to actually reflect the edit before returning, not just the write.
                        snapshotFlow { reminders }.first { list ->
                            list.any {
                                it.reminder.id == edited.id &&
                                    it.reminder.title == edited.title &&
                                    it.reminder.description == edited.description
                            }
                        }
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
                    },
                    onExportBackup = { createBackupDocument.launch("reminders-backup.yaml") },
                    onImportBackup = { openBackupDocument.launch(arrayOf("*/*")) }
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
