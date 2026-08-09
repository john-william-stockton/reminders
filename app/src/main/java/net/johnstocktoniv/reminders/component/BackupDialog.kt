package net.johnstocktoniv.reminders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.johnstocktoniv.reminders.alarm.parseCronOrNull

@Composable
fun BackupDialog(
    isOpen: Boolean,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    scheduledBackupEnabled: Boolean = false,
    scheduledBackupCron: String = "0 */8 * * *",
    scheduledBackupDestinationSet: Boolean = false,
    onToggleScheduledBackup: (Boolean) -> Unit = {},
    onScheduledBackupCronChange: (String) -> Unit = {},
    onChooseScheduledBackupFolder: () -> Unit = {},
) {
    if (!isOpen) return

    var showRestoreConfirm by remember(isOpen) { mutableStateOf(false) }
    // Local edit buffer so typing an in-progress (possibly invalid) expression doesn't get
    // clobbered by scheduledBackupCron's own recomposition — committed upward via
    // onScheduledBackupCronChange only once it parses, same spirit as the switch only enabling
    // once a destination is actually chosen.
    var cronInput by remember(isOpen, scheduledBackupCron) { mutableStateOf(scheduledBackupCron) }
    val cronValid = parseCronOrNull(cronInput) != null

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Scheduled Backup")
                    Switch(
                        checked = scheduledBackupEnabled,
                        onCheckedChange = { checked ->
                            // Turning it on with no folder chosen yet asks for one first — the
                            // switch itself only flips once onToggleScheduledBackup is actually
                            // invoked (by the caller, after a folder is picked), same as it
                            // reverts to off if that picker is cancelled.
                            if (checked && !scheduledBackupDestinationSet) {
                                onChooseScheduledBackupFolder()
                            } else {
                                onToggleScheduledBackup(checked)
                            }
                        },
                        modifier = Modifier.testTag("scheduledBackupSwitch")
                    )
                }
                TextField(
                    value = cronInput,
                    onValueChange = {
                        cronInput = it
                        if (parseCronOrNull(it) != null) onScheduledBackupCronChange(it)
                    },
                    label = { Text("Backup CRON schedule") },
                    isError = cronInput.isNotBlank() && !cronValid,
                    supportingText = {
                        if (cronInput.isNotBlank() && !cronValid) Text("Not a valid CRON expression")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scheduledBackupCronField")
                )
                TextButton(
                    onClick = onChooseScheduledBackupFolder,
                    modifier = Modifier.testTag("chooseScheduledBackupFolderButton")
                ) {
                    Text(if (scheduledBackupDestinationSet) "Change Backup Folder…" else "Choose Backup Folder…")
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
