package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.johnstocktoniv.reminders.database.parseTimeOrNull
import net.johnstocktoniv.reminders.database.timeFormatter
import java.time.LocalTime

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    defaultReminderTime: LocalTime,
    onCancel: () -> Unit,
    onSave: (LocalTime) -> Unit,
) {
    if (!isOpen) return

    var timeInput by remember(defaultReminderTime) {
        mutableStateOf(defaultReminderTime.format(timeFormatter))
    }
    var showError by remember(defaultReminderTime) { mutableStateOf(false) }

    val parsedTime = parseTimeOrNull(timeInput)
    val timeValid = parsedTime != null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Default time used for reminders that don't have a specific time set.")
                TextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Default Time") },
                    isError = showError && !timeValid,
                    supportingText = {
                        if (showError && !timeValid) Text("Use the format shown when the dialog opens")
                    },
                    singleLine = true,
                    modifier = Modifier.testTag("defaultTimeField")
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!timeValid) {
                    showError = true
                    return@Button
                }
                onSave(parsedTime)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
