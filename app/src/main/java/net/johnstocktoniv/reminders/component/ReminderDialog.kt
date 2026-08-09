package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.johnstocktoniv.reminders.alarm.isRecurring
import net.johnstocktoniv.reminders.alarm.nextOccurrence
import net.johnstocktoniv.reminders.alarm.parseCronOrNull
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderStatus
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.dateFormatter
import net.johnstocktoniv.reminders.database.timeFormatter
import java.time.LocalDateTime

@Composable
fun ReminderDialog(
    isOpen: Boolean,
    onCancel: () -> Unit,
    onSave: (Reminder, List<String>) -> Unit,
    reminderWithSchedules: ReminderWithSchedules? = null,
    onEditOccurrence: (ReminderWithSchedules, Reminder) -> Unit = { _, _ -> },
    onDelete: () -> Unit = {},
) {
    if (!isOpen) return

    val reminder = reminderWithSchedules?.reminder

    var titleInput by remember(reminder) {
        mutableStateOf(reminder?.title.orEmpty())
    }
    var descriptionInput by remember(reminder) {
        mutableStateOf(reminder?.description.orEmpty())
    }
    var showSeriesChoice by remember(reminder) { mutableStateOf(false) }
    var showDeleteConfirm by remember(reminder) { mutableStateOf(false) }
    // Seeds one blank row for a brand-new reminder so there's always a visible field to type
    // into, rather than an empty list the user has to know to press "Add schedule" to populate.
    val scheduleInputs = remember(reminderWithSchedules) {
        val schedules = reminderWithSchedules?.schedules?.map { it.cronExpression }.orEmpty()
        mutableStateListOf(*(schedules.ifEmpty { listOf("") }.toTypedArray()))
    }
    var showErrors by remember(reminder) { mutableStateOf(false) }

    val scheduleValid = scheduleInputs.map { it.isBlank() || parseCronOrNull(it) != null }
    val nonBlankSchedules = scheduleInputs.filter { it.isNotBlank() }
    // Every reminder is a CRON schedule now — a plain "min hour dom month dow" repeats, and
    // appending a year pins it to a single instant instead (see CronSchedule.kt), which is how a
    // one-off reminder is expressed without a separate date/time input. nextOccurrence() always
    // searches forward from now, so a schedule with no remaining future match (e.g. a pinned year
    // that's already passed) computes null here and schedulesValid below blocks saving it —
    // reminders can't be created in the past.
    val computedNext = if (nonBlankSchedules.isNotEmpty()) {
        nextOccurrence(nonBlankSchedules.map { ReminderSchedule(reminderId = 0, cronExpression = it) }, LocalDateTime.now())
    } else {
        null
    }

    val titleValid = titleInput.isNotBlank()
    val schedulesValid = scheduleValid.all { it } && nonBlankSchedules.isNotEmpty() && computedNext != null

    // Only an *existing, still-incomplete* reminder whose own schedule would fire again after its
    // own occurrence is genuinely part of a series — a brand-new reminder, a completed one, or a
    // one-off pinned to a single instant has no "series" to distinguish an edit from, so editing
    // those just saves normally with no prompt.
    val hasFutureAfterOwn = reminderWithSchedules != null &&
        reminderWithSchedules.reminder.status != ReminderStatus.COMPLETE &&
        isRecurring(
            reminderWithSchedules.schedules,
            reminderWithSchedules.reminder.date.atTime(reminderWithSchedules.reminder.effectiveTime())
        )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Are you sure?") },
            text = { Text("This reminder will be permanently deleted. This can't be undone.") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("confirmDeleteButton")
                    ) {
                        Text("Delete")
                    }
                }
            }
        )
        return
    }

    if (showSeriesChoice) {
        AlertDialog(
            onDismissRequest = { showSeriesChoice = false },
            title = { Text("Apply changes to?") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "This reminder repeats. Choose whether these changes apply to just this " +
                            "occurrence or the whole series going forward."
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showSeriesChoice = false
                            onSave(
                                reminderWithSchedules!!.reminder.copy(
                                    title = titleInput.trim(),
                                    description = descriptionInput.trim(),
                                    date = computedNext!!.toLocalDate(),
                                    time = computedNext.toLocalTime()
                                ),
                                nonBlankSchedules
                            )
                        },
                        modifier = Modifier.testTag("wholeSeriesButton")
                    ) {
                        Text("Whole Series")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showSeriesChoice = false
                            onEditOccurrence(
                                reminderWithSchedules!!,
                                reminderWithSchedules.reminder.copy(
                                    title = titleInput.trim(),
                                    description = descriptionInput.trim()
                                )
                            )
                        },
                        modifier = Modifier.testTag("thisOccurrenceOnlyButton")
                    ) {
                        Text("This Occurrence Only")
                    }
                }
            },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { showSeriesChoice = false }) { Text("Cancel") }
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (reminder == null) "Add Reminder" else "Edit Reminder") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Title") },
                    isError = showErrors && !titleValid,
                    supportingText = {
                        if (showErrors && !titleValid) Text("Title is required")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("titleField")
                )
                TextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("descriptionField")
                )

                scheduleInputs.forEachIndexed { index, value ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = value,
                            onValueChange = { scheduleInputs[index] = it },
                            label = { Text("e.g. 0 9 * * 1-5, or 30 9 25 12 * 2026 for a one-off") },
                            isError = showErrors && !scheduleValid[index],
                            supportingText = {
                                if (showErrors && !scheduleValid[index]) Text("Not a valid CRON expression")
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("cronField:$index")
                        )
                        IconButton(
                            onClick = { scheduleInputs.removeAt(index) },
                            modifier = Modifier.testTag("removeCronButton:$index")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove schedule")
                        }
                    }
                }
                TextButton(onClick = { scheduleInputs.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add schedule")
                }
                if (showErrors && nonBlankSchedules.isEmpty()) {
                    Text("Add at least one CRON schedule")
                }
                Text(
                    "Next occurrence: " + (computedNext?.let { "${it.toLocalDate().format(dateFormatter)} at ${it.toLocalTime().format(timeFormatter)}" } ?: "—")
                )
            }
        },
        confirmButton = {
            // All three actions centered in one row (rather than Save/Delete in confirmButton and
            // Cancel stranded in dismissButton) so Delete doesn't strand apart from Save/Cancel.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (reminder != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("deleteButton")
                    ) {
                        Text("Delete")
                    }
                }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    if (!titleValid || !schedulesValid) {
                        showErrors = true
                        return@Button
                    }
                    if (hasFutureAfterOwn) {
                        showSeriesChoice = true
                        return@Button
                    }
                    onSave(
                        (reminder ?: Reminder()).copy(
                            title = titleInput.trim(),
                            description = descriptionInput.trim(),
                            date = computedNext!!.toLocalDate(),
                            time = computedNext.toLocalTime()
                        ),
                        nonBlankSchedules
                    )
                }) {
                    Text("Save")
                }
            }
        }
    )
}
