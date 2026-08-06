package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.R
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import java.time.LocalDate
import java.time.LocalTime

// Fixed peek height for each reminder behind the top of the collapsed completed-reminders stack
// (Today tab). A static offset (rather than a percentage of each item's own measured height)
// keeps the stack's layout math simple and its peek height consistent regardless of how tall an
// individual reminder's card happens to be (e.g. due to a long description).
private val CompletedStackOffset = 16.dp

// Slight shadow on each collapsed stack card, drawn with the same corner radius as the card
// itself (see ReminderListItem's clip shape) so it reads as a subtle drop shadow rather than a
// rectangular halo peeking out past the rounded corners.
private val CompletedStackCardShape = RoundedCornerShape(16.dp)
private val CompletedStackShadowElevation = 3.dp

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
    onSaveReminder: suspend (Reminder, List<String>) -> Unit,
    onSaveSettings: (LocalTime) -> Unit,
    onToggleComplete: (ReminderWithSchedules) -> Unit,
    onDeleteReminder: (ReminderWithSchedules) -> Unit,
    onClearAll: (List<ReminderWithSchedules>) -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var reminderDialogTarget by remember { mutableStateOf<ReminderDialogTarget?>(null) }
    var settingsDialogActive by remember { mutableStateOf(false) }
    var backupDialogActive by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ReminderTab.TODAY) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    // Today tab only: completed reminders default to a collapsed card stack; tapping any
    // of them toggles the whole stack open/closed.
    var completedCollapsed by remember { mutableStateOf(true) }

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
            // Awaits the write before closing: closing immediately let a fast reopen of the same
            // reminder race the DB write, showing stale schedules if it hadn't landed yet (e.g.
            // right after adding a CRON schedule and immediately reopening to confirm it saved).
            coroutineScope.launch {
                onSaveReminder(reminder, cronExpressions)
                reminderDialogTarget = null
            }
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
    BackupDialog(
        isOpen = backupDialogActive,
        onCancel = { backupDialogActive = false },
        onExport = {
            onExportBackup()
            backupDialogActive = false
        },
        onImport = {
            onImportBackup()
            backupDialogActive = false
        }
    )
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { backupDialogActive = true }) {
                        Icon(Icons.Filled.Backup, contentDescription = "Backup")
                    }
                    IconButton(onClick = { settingsDialogActive = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                // Same light gray/lavender tone as a reminder card (see ReminderListItem) so the
                // title bar reads as a distinct section rather than blending into the plain
                // background behind it — the look the leftover native ActionBar used to have.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            // On the Complete tab the FAB doubles as the "clear all" trigger (red-tinted trash
            // icon) instead of opening the add-reminder dialog; hidden entirely when there's
            // nothing to clear, mirroring the old standalone button's visibility.
            val isClearAll = selectedTab == ReminderTab.COMPLETE
            if (!isClearAll || visibleReminders.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (isClearAll) showClearAllConfirm = true
                        else reminderDialogTarget = ReminderDialogTarget.Add
                    },
                    containerColor = if (isClearAll) MaterialTheme.colorScheme.errorContainer
                        else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (isClearAll) MaterialTheme.colorScheme.onErrorContainer
                        else contentColorFor(FloatingActionButtonDefaults.containerColor),
                    modifier = Modifier.padding(12.dp).size(75.6.dp)
                ) {
                    if (isClearAll) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear All", modifier = Modifier.size(36.dp))
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = "Add Item", modifier = Modifier.size(36.dp))
                    }
                }
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
            LazyColumn(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Each item already carries 4dp of its own top padding (see ReminderListItem),
                // but that reads as flush against the app bar; this adds a clearer gap.
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                if (selectedTab == ReminderTab.TODAY) {
                    val incompleteToday = visibleReminders.filter { !it.reminder.complete }
                    val completedToday = visibleReminders.filter { it.reminder.complete }
                    items(incompleteToday, key = { it.reminder.id }) { reminderWithSchedules ->
                        ReminderRow(
                            reminderWithSchedules,
                            defaultReminderTime = defaultReminderTime,
                            onComplete = { onToggleComplete(reminderWithSchedules) },
                            onDelete = { onDeleteReminder(reminderWithSchedules) },
                            onLongPress = { reminderDialogTarget = ReminderDialogTarget.Edit(reminderWithSchedules) }
                        )
                    }
                    if (completedToday.isNotEmpty()) {
                        item(key = "completed-stack") {
                            CompletedReminderStack(
                                reminders = completedToday,
                                collapsed = completedCollapsed,
                                defaultReminderTime = defaultReminderTime,
                                onComplete = onToggleComplete,
                                onDelete = onDeleteReminder,
                                onEdit = { reminderDialogTarget = ReminderDialogTarget.Edit(it) },
                                onToggleCollapsed = { completedCollapsed = !completedCollapsed }
                            )
                        }
                    }
                } else {
                    items(visibleReminders, key = { it.reminder.id }) { reminderWithSchedules ->
                        ReminderRow(
                            reminderWithSchedules,
                            defaultReminderTime = defaultReminderTime,
                            onComplete = { onToggleComplete(reminderWithSchedules) },
                            onDelete = { onDeleteReminder(reminderWithSchedules) },
                            onLongPress = { reminderDialogTarget = ReminderDialogTarget.Edit(reminderWithSchedules) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminderWithSchedules: ReminderWithSchedules,
    defaultReminderTime: LocalTime,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    onTap: (() -> Unit)? = null,
    showDescription: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ReminderListItem(
        reminderWithSchedules,
        defaultReminderTime = defaultReminderTime,
        onComplete = onComplete,
        onDelete = onDelete,
        showDescription = showDescription,
        modifier = modifier
            .testTag("reminderItem:${reminderWithSchedules.reminder.id}")
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = onTap?.let { callback -> { callback() } }
                )
            }
    )
}

// Today tab only. Collapsed: completed reminders render as a card stack, each one behind and
// peeking CompletedStackOffset lower than the one above (only the frontmost is fully visible/
// interactive) so they take up minimal space. Expanded: they render as a normal vertical list,
// same as any other tab. Tapping any completed reminder toggles between the two.
@Composable
private fun CompletedReminderStack(
    reminders: List<ReminderWithSchedules>,
    collapsed: Boolean,
    defaultReminderTime: LocalTime,
    onComplete: (ReminderWithSchedules) -> Unit,
    onDelete: (ReminderWithSchedules) -> Unit,
    onEdit: (ReminderWithSchedules) -> Unit,
    onToggleCollapsed: () -> Unit,
) {
    if (!collapsed || reminders.size <= 1) {
        Column {
            reminders.forEach { reminderWithSchedules ->
                ReminderRow(
                    reminderWithSchedules,
                    defaultReminderTime = defaultReminderTime,
                    onComplete = { onComplete(reminderWithSchedules) },
                    onDelete = { onDelete(reminderWithSchedules) },
                    onLongPress = { onEdit(reminderWithSchedules) },
                    onTap = onToggleCollapsed
                )
            }
        }
        return
    }

    Layout(
        content = {
            reminders.forEachIndexed { index, reminderWithSchedules ->
                ReminderRow(
                    reminderWithSchedules,
                    defaultReminderTime = defaultReminderTime,
                    onComplete = { onComplete(reminderWithSchedules) },
                    onDelete = { onDelete(reminderWithSchedules) },
                    onLongPress = { onEdit(reminderWithSchedules) },
                    onTap = onToggleCollapsed,
                    showDescription = false,
                    modifier = Modifier
                        .zIndex((reminders.size - index).toFloat())
                        .shadow(CompletedStackShadowElevation, CompletedStackCardShape)
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val offsetPx = CompletedStackOffset.roundToPx()
        val width = placeables.maxOf { it.width }
        val height = placeables.indices.maxOf { index -> index * offsetPx + placeables[index].height }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = 0, y = index * offsetPx)
            }
        }
    }
}
