package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val MIN_MINUTES = 1L
private const val MAX_MINUTES = 180L

private fun parseMinutesOrNull(input: String): Long? =
    input.toLongOrNull()?.takeIf { it in MIN_MINUTES..MAX_MINUTES }

// One-shot duration picker for a single snooze — nothing here is persisted, so it always opens
// pre-filled with the caller's default rather than remembering a prior override.
@Composable
fun SnoozeDurationDialog(
    isOpen: Boolean,
    defaultMinutes: Long,
    onCancel: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    if (!isOpen) return

    var minutesInput by remember(isOpen) { mutableStateOf(defaultMinutes.toString()) }
    val minutesValid = parseMinutesOrNull(minutesInput) != null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Snooze Duration") },
        text = {
            TextField(
                value = minutesInput,
                onValueChange = { minutesInput = it },
                label = { Text("Minutes") },
                isError = minutesInput.isNotBlank() && !minutesValid,
                supportingText = {
                    if (minutesInput.isNotBlank() && !minutesValid) {
                        Text("Enter a whole number between $MIN_MINUTES and $MAX_MINUTES")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("snoozeMinutesField")
            )
        },
        confirmButton = {
            // Both buttons live in this single slot (rather than confirmButton + dismissButton)
            // so they can be laid out centered instead of Material3's default end-aligned row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = { parseMinutesOrNull(minutesInput)?.let(onConfirm) },
                    enabled = minutesValid,
                    modifier = Modifier.testTag("confirmSnoozeDurationButton")
                ) {
                    Text("Snooze")
                }
            }
        }
    )
}
