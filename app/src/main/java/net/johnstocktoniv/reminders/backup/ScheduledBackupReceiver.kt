package net.johnstocktoniv.reminders.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.R
import net.johnstocktoniv.reminders.database.DatabaseProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Sortable (lexicographic order matches chronological order), and distinctive enough that
// pruneOldBackups() only ever touches files this app created — not other files the user may keep
// in the same folder.
// internal (not private) so ScheduledBackupReceiverTest can verify the regex actually matches
// what the formatter produces — a mismatch here previously meant pruneOldBackups() silently
// matched nothing and never deleted anything.
internal val backupTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
internal val backupFileNameRegex = Regex("""^reminders-backup-\d{4}-\d{2}-\d{2}T\d{6}\.yaml$""")

class ScheduledBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledBackupScheduler.ACTION_SCHEDULED_BACKUP) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                writeBackupAndPrune(appContext)
            } finally {
                // Always rearm the next occurrence, even if this run's write failed (e.g. the
                // user revoked folder access) — the schedule shouldn't silently stop.
                ScheduledBackupScheduler.schedule(appContext)
                pendingResult.finish()
            }
        }
    }

    private suspend fun writeBackupAndPrune(context: Context) {
        // No destination configured means the feature was never actually enabled through the
        // UI's normal flow (schedule() only arms once one is set) — nothing to report either way.
        val treeUriString = ScheduledBackupPrefs.getDestinationTreeUri(context) ?: return
        val treeUri = Uri.parse(treeUriString)
        val resolver = context.contentResolver
        val parentDocumentUri =
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

        val fileName = "reminders-backup-${LocalDateTime.now().format(backupTimestampFormatter)}.yaml"
        val newFileUri = runCatching {
            DocumentsContract.createDocument(resolver, parentDocumentUri, "application/x-yaml", fileName)
        }.getOrNull()
        if (newFileUri == null) {
            notifyBackupResult(context, success = false)
            return
        }

        val yaml = ReminderBackup.toYaml(DatabaseProvider.dao(context).readAll().first())
        val wrote = runCatching {
            resolver.openOutputStream(newFileUri)?.use { it.write(yaml.toByteArray()) } != null
        }.getOrDefault(false)
        if (!wrote) {
            notifyBackupResult(context, success = false)
            return
        }

        pruneOldBackups(context, treeUri)
        notifyBackupResult(context, success = true, fileName = fileName)
    }

    private fun notifyBackupResult(context: Context, success: Boolean, fileName: String? = null) {
        ensureNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_backup)
            .setContentTitle(if (success) "Backup complete" else "Backup failed")
            .setContentText(
                if (success) "Saved $fileName" else "Couldn't write the scheduled backup — check the chosen folder is still accessible"
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        // Kept inline (rather than a shared permission-check helper) so Android Lint's
        // MissingPermission check can see the guard right at the notify() call site.
        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (canPostNotifications) {
            // Fixed id: each run replaces the previous notification rather than piling up a new
            // one every occurrence (most visible with a short testing CRON like "* * * * *").
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Scheduled backups",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Confirms when a scheduled reminder backup completes or fails"
        }
        manager.createNotificationChannel(channel)
    }

    private fun pruneOldBackups(context: Context, treeUri: Uri) {
        val resolver = context.contentResolver
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

        val backups = mutableListOf<Pair<String, Uri>>()
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (backupFileNameRegex.matches(name)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                        backups.add(name to documentUri)
                    }
                }
            }
        }

        backups.sortedByDescending { it.first }
            .drop(ScheduledBackupScheduler.MAX_RETAINED_BACKUPS)
            .forEach { (_, uri) -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "scheduled_backup"
        private const val NOTIFICATION_ID = 1001
    }
}
