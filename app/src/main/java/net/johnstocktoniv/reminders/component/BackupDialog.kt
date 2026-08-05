package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun BackupDialog(
    isOpen: Boolean,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    if (!isOpen) return

    var showRestoreConfirm by remember(isOpen) { mutableStateOf(false) }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Are you sure?") },
            text = { Text("Restoring from a file replaces all current reminders. This can't be undone.") },
            confirmButton = {
                // Both buttons live in this single slot (rather than confirmButton +
                // dismissButton) so they can be laid out centered instead of Material3's
                // default end-aligned button row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    OutlinedButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") }
                    Button(
                        onClick = {
                            showRestoreConfirm = false
                            onImport()
                        },
                        modifier = Modifier.testTag("confirmRestoreButton")
                    ) {
                        Text("Restore")
                    }
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Backup") },
        text = {
            // Export and Restore are the two things this dialog actually offers, so they're
            // grouped together here rather than splitting Export into the footer and leaving
            // Restore as an unrelated-looking button above it.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Export all reminders to a YAML file, or restore from a previously exported file.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onExport, modifier = Modifier.testTag("exportButton")) {
                    Text("Export")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.testTag("importButton")
                ) {
                    Text("Restore From File…")
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}
