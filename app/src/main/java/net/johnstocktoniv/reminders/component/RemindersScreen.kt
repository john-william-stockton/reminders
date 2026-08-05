package net.johnstocktoniv.reminders.component

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.johnstocktoniv.reminders.R
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import java.time.LocalDate
import java.time.LocalTime

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
@Composable
fun RemindersScreen(
    reminders: List<ReminderWithSchedules>,
    defaultReminderTime: LocalTime,
    onSaveReminder: (Reminder, List<String>) -> Unit,
    onSaveSettings: (LocalTime) -> Unit,
    onToggleComplete: (ReminderWithSchedules) -> Unit,
    onDeleteReminder: (ReminderWithSchedules) -> Unit,
    onClearAll: (List<ReminderWithSchedules>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reminderDialogTarget by remember { mutableStateOf<ReminderDialogTarget?>(null) }
    var settingsDialogActive by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ReminderTab.TODAY) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val editingReminder = (reminderDialogTarget as? ReminderDialogTarget.Edit)?.reminder
    val visibleReminders = when (selectedTab) {
        // Only reminders actually due today; stable sort keeps incomplete above complete.
        ReminderTab.TODAY -> reminders.filter { it.reminder.date == LocalDate.now() }
            .sortedBy { it.reminder.complete }
        ReminderTab.INCOMPLETE -> reminders.filter { !it.reminder.complete }
        ReminderTab.COMPLETE -> reminders.filter { it.reminder.complete }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Delete completed reminders?") },
            text = { Text("This will permanently delete ${visibleReminders.size} completed reminder(s). This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    onClearAll(visibleReminders)
                    showClearAllConfirm = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    ReminderDialog(
        isOpen = reminderDialogTarget != null,
        defaultReminderTime = defaultReminderTime,
        onCancel = { reminderDialogTarget = null },
        onSave = { reminder, cronExpressions ->
            onSaveReminder(reminder, cronExpressions)
            reminderDialogTarget = null
        },
        reminderWithSchedules = editingReminder
    )
    SettingsDialog(
        isOpen = settingsDialogActive,
        defaultReminderTime = defaultReminderTime,
        onCancel = { settingsDialogActive = false },
        onSave = { time ->
            onSaveSettings(time)
            settingsDialogActive = false
        }
    )
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { settingsDialogActive = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { reminderDialogTarget = ReminderDialogTarget.Add },
                modifier = Modifier.padding(12.dp).size(75.6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Item", modifier = Modifier.size(36.dp))
            }
        },
        bottomBar = {
            NavigationBar {
                ReminderTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
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
            if (selectedTab == ReminderTab.COMPLETE && visibleReminders.isNotEmpty()) {
                Button(
                    onClick = { showClearAllConfirm = true },
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
                        onComplete = { onToggleComplete(reminderWithSchedules) },
                        onDelete = { onDeleteReminder(reminderWithSchedules) },
                        modifier = Modifier
                            .testTag("reminderItem:${reminderWithSchedules.reminder.id}")
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { reminderDialogTarget = ReminderDialogTarget.Edit(reminderWithSchedules) }
                                )
                            }
                    )
                }
            }
        }
    }
}
