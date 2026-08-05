package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import net.johnstocktoniv.reminders.alarm.nextOccurrence
import net.johnstocktoniv.reminders.alarm.parseCronOrNull
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.dateFormatter
import net.johnstocktoniv.reminders.database.parseDateOrNull
import net.johnstocktoniv.reminders.database.parseTimeOrNull
import net.johnstocktoniv.reminders.database.timeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun ReminderDialog(
    isOpen: Boolean,
    defaultReminderTime: LocalTime,
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
    var dateInput by remember(reminder) {
        mutableStateOf((reminder?.date ?: LocalDate.now()).format(dateFormatter))
    }
    var timeInput by remember(reminder) {
        mutableStateOf(reminder?.time?.format(timeFormatter).orEmpty())
    }
    val scheduleInputs = remember(reminderWithSchedules) {
        mutableStateListOf(*(reminderWithSchedules?.schedules?.map { it.cronExpression }.orEmpty().toTypedArray()))
    }
    // A reminder is either a fixed date/time, or CRON-recurring — never both, to keep it
    // unambiguous which one actually drives when it fires.
    var isRecurring by remember(reminderWithSchedules) {
        mutableStateOf(reminderWithSchedules?.schedules?.isNotEmpty() == true)
    }
    var showErrors by remember(reminder) { mutableStateOf(false) }

    val parsedDate = parseDateOrNull(dateInput)
    val parsedTime = timeInput.takeIf { it.isNotBlank() }?.let(::parseTimeOrNull)
    val scheduleValid = scheduleInputs.map { it.isBlank() || parseCronOrNull(it) != null }
    val nonBlankSchedules = scheduleInputs.filter { it.isNotBlank() }
    val computedNext = if (isRecurring && nonBlankSchedules.isNotEmpty()) {
        nextOccurrence(nonBlankSchedules.map { ReminderSchedule(reminderId = 0, cronExpression = it) }, LocalDateTime.now())
    } else {
        null
    }

    val titleValid = titleInput.isNotBlank()
    val dateValid = isRecurring || parsedDate != null
    val timeValid = isRecurring || timeInput.isBlank() || parsedTime != null
    val schedulesValid = scheduleValid.all { it } &&
        (!isRecurring || (nonBlankSchedules.isNotEmpty() && computedNext != null))

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (reminder == null) "Add Reminder" else "Edit Reminder") },
        text = {
            Column {
                TextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Title") },
                    isError = showErrors && !titleValid,
                    supportingText = {
                        if (showErrors && !titleValid) Text("Title is required")
                    },
                    singleLine = true
                )
                TextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Description (optional)") }
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isRecurring = false }) {
                    RadioButton(selected = !isRecurring, onClick = { isRecurring = false })
                    Text("One-time")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isRecurring = true }) {
                    RadioButton(selected = isRecurring, onClick = { isRecurring = true })
                    Text("Recurring (CRON)")
                }

                if (!isRecurring) {
                    TextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Date") },
                        isError = showErrors && !dateValid,
                        supportingText = {
                            if (showErrors && !dateValid) Text("Use the format shown when the dialog opens")
                        },
                        singleLine = true
                    )
                    TextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Time (optional)") },
                        isError = showErrors && !timeValid,
                        supportingText = {
                            when {
                                showErrors && !timeValid -> Text("Leave blank or match the time format")
                                timeInput.isBlank() -> Text("Defaults to ${defaultReminderTime.format(timeFormatter)}")
                            }
                        },
                        singleLine = true
                    )
                } else {
                    scheduleInputs.forEachIndexed { index, value ->
                        Row {
                            TextField(
                                value = value,
                                onValueChange = { scheduleInputs[index] = it },
                                label = { Text("e.g. 0 9 * * 1-5") },
                                isError = showErrors && !scheduleValid[index],
                                supportingText = {
                                    if (showErrors && !scheduleValid[index]) Text("Not a valid CRON expression")
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { scheduleInputs.removeAt(index) }) {
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
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!titleValid || !dateValid || !timeValid || !schedulesValid) {
                    showErrors = true
                    return@Button
                }
                val (date, time) = if (isRecurring) {
                    computedNext!!.toLocalDate() to computedNext.toLocalTime()
                } else {
                    parsedDate!! to parsedTime
                }
                onSave(
                    (reminder ?: Reminder()).copy(
                        title = titleInput.trim(),
                        description = descriptionInput.trim(),
                        date = date,
                        time = time
                    ),
                    if (isRecurring) nonBlankSchedules else emptyList()
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
