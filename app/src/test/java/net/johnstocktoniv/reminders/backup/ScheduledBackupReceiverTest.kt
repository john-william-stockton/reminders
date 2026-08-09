package net.johnstocktoniv.reminders.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduledBackupReceiverTest {
    @Test
    fun regexMatchesAFileNameProducedByTheTimestampFormatter() {
        val timestamp = LocalDateTime.of(2026, 8, 9, 14, 30, 5).format(backupTimestampFormatter)
        val fileName = "reminders-backup-$timestamp.yaml"

        assertTrue("expected \"$fileName\" to match the pruning regex", backupFileNameRegex.matches(fileName))
    }

    @Test
    fun regexDoesNotMatchUnrelatedFileNames() {
        assertFalse(backupFileNameRegex.matches("reminders-backup.yaml"))
        assertFalse(backupFileNameRegex.matches("some-other-file.yaml"))
        assertFalse(backupFileNameRegex.matches("reminders-backup-2026-08-09T143005.yaml.bak"))
    }
}
