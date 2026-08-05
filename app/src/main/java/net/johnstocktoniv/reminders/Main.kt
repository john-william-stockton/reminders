package net.johnstocktoniv.reminders

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.alarm.AlarmScheduler
import net.johnstocktoniv.reminders.component.ReminderDialog
import net.johnstocktoniv.reminders.component.ReminderListItem
import net.johnstocktoniv.reminders.component.SettingsDialog
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.ReminderDao
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.settings.SettingsRepository
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import java.time.LocalDate
import androidx.core.net.toUri

private sealed interface ReminderDialogTarget {
    data object Add : ReminderDialogTarget
    data class Edit(val reminder: ReminderWithSchedules) : ReminderDialogTarget
}

private enum class ReminderTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    INCOMPLETE("Incomplete", Icons.AutoMirrored.Filled.List),
    COMPLETE("Complete", Icons.Filled.CheckCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
class Main : ComponentActivity() {
    private lateinit var dao: ReminderDao
    private var reminderDialogTarget: MutableState<ReminderDialogTarget?> = mutableStateOf(null)
    private var settingsDialogActive: MutableState<Boolean> = mutableStateOf(false)
    private var selectedTab: MutableState<ReminderTab> = mutableStateOf(ReminderTab.TODAY)
    private var showClearAllConfirm: MutableState<Boolean> = mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val editingReminder = (reminderDialogTarget.value as? ReminderDialogTarget.Edit)?.reminder
                val visibleReminders = when (selectedTab.value) {
                    // Only reminders actually due today; stable sort keeps incomplete above complete.
                    ReminderTab.TODAY -> reminders.filter { it.reminder.date == LocalDate.now() }
                                                  .sortedBy { it.reminder.complete }
                    ReminderTab.INCOMPLETE -> reminders.filter { !it.reminder.complete }
                    ReminderTab.COMPLETE -> reminders.filter { it.reminder.complete }
                }

                if (showClearAllConfirm.value) {
                    AlertDialog(
                        onDismissRequest = { showClearAllConfirm.value = false },
                        title = { Text("Delete completed reminders?") },
                        text = { Text("This will permanently delete ${visibleReminders.size} completed reminder(s). This can't be undone.") },
                        confirmButton = {
                            Button(onClick = {
                                lifecycleScope.launch { visibleReminders.forEach { dao.delete(it.reminder) } }
                                visibleReminders.forEach {
                                    AlarmScheduler.cancel(
                                        applicationContext,
                                        it.reminder.id
                                    )
                                }
                                showClearAllConfirm.value = false
                            }) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showClearAllConfirm.value = false }) { Text("Cancel") }
                        }
                    )
                }

                ReminderDialog(
                    isOpen = reminderDialogTarget.value != null,
                    defaultReminderTime = defaultReminderTime,
                    onCancel = { reminderDialogTarget.value = null },
                    onSave = { reminder, cronExpressions ->
                        lifecycleScope.launch {
                            val isNew = reminder.id == 0L
                            val newId = dao.saveWithSchedules(reminder, cronExpressions)
                            val saved = if (isNew) reminder.copy(id = newId) else reminder
                            AlarmScheduler.schedule(applicationContext, saved)
                        }
                        reminderDialogTarget.value = null
                    },
                    reminderWithSchedules = editingReminder
                )
                SettingsDialog(
                    isOpen = settingsDialogActive.value,
                    defaultReminderTime = defaultReminderTime,
                    onCancel = { settingsDialogActive.value = false },
                    onSave = { time ->
                        SettingsRepository.setDefaultReminderTime(applicationContext, time)
                        settingsDialogActive.value = false
                        // Reminders without an explicit time use the default; re-arm them so
                        // their alarms move to the new default time.
                        reminders.map { it.reminder }.filter { it.time == null }
                            .forEach { reminder -> AlarmScheduler.schedule(applicationContext, reminder) }
                    }
                )
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                IconButton(onClick = { settingsDialogActive.value = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { reminderDialogTarget.value = ReminderDialogTarget.Add },
                            modifier = Modifier.padding(12.dp).size(75.6.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Item", modifier = Modifier.size(36.dp))
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            ReminderTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab.value == tab,
                                    onClick = { selectedTab.value = tab },
                                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(36.dp)) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (selectedTab.value == ReminderTab.COMPLETE && visibleReminders.isNotEmpty()) {
                            Button(
                                onClick = { showClearAllConfirm.value = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Text("Clear All")
                            }
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(visibleReminders, key = { it.reminder.id }) { reminderWithSchedules ->
                                ReminderListItem(
                                    reminderWithSchedules,
                                    defaultReminderTime = defaultReminderTime,
                                    onComplete = {
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
                                    onDelete = {
                                        lifecycleScope.launch { dao.delete(reminderWithSchedules.reminder) }
                                        AlarmScheduler.cancel(applicationContext, reminderWithSchedules.reminder.id)
                                    },
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { reminderDialogTarget.value = ReminderDialogTarget.Edit(reminderWithSchedules) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
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
