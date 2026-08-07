package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import net.johnstocktoniv.reminders.alarm.nextOccurrence
import net.johnstocktoniv.reminders.alarm.parseCronOrNull
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
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
) {
    if (!isOpen) return

    val reminder = reminderWithSchedules?.reminder

    var titleInput by remember(reminder) {
        mutableStateOf(reminder?.title.orEmpty())
    }
    var descriptionInput by remember(reminder) {
        mutableStateOf(reminder?.description.orEmpty())
    }
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
            Button(onClick = {
                if (!titleValid || !schedulesValid) {
                    showErrors = true
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
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
