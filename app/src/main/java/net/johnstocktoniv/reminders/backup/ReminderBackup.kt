package net.johnstocktoniv.reminders.backup

import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderStatus
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.storageDateFormatter
import net.johnstocktoniv.reminders.database.storageTimeFormatter
import java.time.LocalDate
import java.time.LocalTime

// Hand-rolled YAML (de)serialization, deliberately scoped to exactly the shape this app persists
// rather than a general-purpose YAML implementation — fromYaml() only needs to round-trip what
// toYaml() emits (including a human hand-editing a value in place).
object ReminderBackup {
    data class BackupContents(
        val reminders: List<ReminderWithSchedules>,
        val scheduledBackupSettings: ScheduledBackupSettings?
    )

    // scheduledBackupSettings is optional purely so callers that only care about reminders (e.g.
    // tests) don't have to thread it through — omitting it just skips the scheduledBackup: section.
    fun toYaml(reminders: List<ReminderWithSchedules>, scheduledBackupSettings: ScheduledBackupSettings? = null): String = buildString {
        if (scheduledBackupSettings != null) {
            appendLine("scheduledBackup:")
            appendLine("  enabled: ${scheduledBackupSettings.enabled}")
            appendLine("  cron: ${quote(scheduledBackupSettings.cronExpression)}")
            appendLine(
                "  destinationUri: " +
                    (scheduledBackupSettings.destinationTreeUri?.let { quote(it) } ?: "null")
            )
        }
        appendLine("reminders:")
        if (reminders.isEmpty()) {
            append("  []")
            return@buildString
        }
        reminders.sortedBy { it.reminder.id }.forEach { (reminder, schedules) ->
            appendLine("  - id: ${reminder.id}")
            appendLine("    title: ${quote(reminder.title)}")
            appendLine("    date: ${reminder.date.format(storageDateFormatter)}")
            appendLine("    time: ${reminder.time?.format(storageTimeFormatter) ?: "null"}")
            if (reminder.description.isEmpty()) {
                appendLine("    description: []")
            } else {
                appendLine("    description:")
                reminder.description.split("\n").forEach { appendLine("      - ${quote(it)}") }
            }
            appendLine("    status: ${reminder.status}")
            if (schedules.isEmpty()) {
                appendLine("    schedules: []")
            } else {
                appendLine("    schedules:")
                schedules.forEach { appendLine("      - ${quote(it.cronExpression)}") }
            }
        }
    }.trimEnd('\n')

    fun fromYaml(yaml: String): Result<BackupContents> = runCatching {
        val lines = yaml.lines()

        // Absent entirely in backups exported before scheduled backup existed — scheduledBackup
        // just stays null rather than that being a parse failure.
        var scheduledBackupSettings: ScheduledBackupSettings? = null
        val scheduledBackupIndex = lines.indexOfFirst { it.trim() == "scheduledBackup:" }
        if (scheduledBackupIndex != -1) {
            var enabled = false
            var cron = ScheduledBackupPrefs.DEFAULT_CRON
            var destinationUri: String? = null
            var settingsIndex = scheduledBackupIndex + 1
            while (settingsIndex < lines.size && lines[settingsIndex].startsWith("  ")) {
                val field = lines[settingsIndex].trim()
                when {
                    field.startsWith("enabled:") -> enabled = field.removePrefix("enabled:").trim().toBooleanStrict()
                    field.startsWith("cron:") -> cron = unquote(field.removePrefix("cron:").trim())
                    field.startsWith("destinationUri:") -> {
                        val value = field.removePrefix("destinationUri:").trim()
                        destinationUri = if (value == "null") null else unquote(value)
                    }
                }
                settingsIndex++
            }
            scheduledBackupSettings = ScheduledBackupSettings(enabled, cron, destinationUri)
        }

        val result = mutableListOf<ReminderWithSchedules>()

        var index = 0
        while (index < lines.size && lines[index].trim() != "reminders:") index++
        require(index < lines.size) { "Missing a top-level \"reminders:\" section" }
        index++

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == "[]") {
                index++
                continue
            }
            require(line.startsWith("  - id:")) { "Expected a reminder entry at line ${index + 1}: \"$line\"" }

            val id = line.trim().removePrefix("- id:").trim().toLong()
            index++

            var title = ""
            var date: LocalDate = LocalDate.now()
            var time: LocalTime? = null
            var description = ""
            var status = ReminderStatus.OPEN
            val schedules = mutableListOf<String>()

            while (index < lines.size && lines[index].startsWith("    ")) {
                val field = lines[index].trim()
                when {
                    field.startsWith("title:") -> title = unquote(field.removePrefix("title:").trim())
                    field.startsWith("date:") ->
                        date = LocalDate.parse(field.removePrefix("date:").trim(), storageDateFormatter)
                    field.startsWith("time:") -> {
                        val value = field.removePrefix("time:").trim()
                        time = if (value == "null") null else LocalTime.parse(value, storageTimeFormatter)
                    }
                    field.startsWith("status:") -> status = ReminderStatus.valueOf(field.removePrefix("status:").trim())
                    // Backward compatibility with backups exported before the Missed state existed.
                    field.startsWith("complete:") ->
                        status = if (field.removePrefix("complete:").trim().toBooleanStrict()) {
                            ReminderStatus.COMPLETE
                        } else {
                            ReminderStatus.OPEN
                        }
                    field == "description: []" -> { }
                    field == "description:" -> {
                        index++
                        val descriptionLines = mutableListOf<String>()
                        while (index < lines.size && lines[index].startsWith("      - ")) {
                            descriptionLines.add(unquote(lines[index].trim().removePrefix("- ").trim()))
                            index++
                        }
                        description = descriptionLines.joinToString("\n")
                        continue
                    }
                    field == "schedules: []" -> { }
                    field == "schedules:" -> {
                        index++
                        while (index < lines.size && lines[index].startsWith("      - ")) {
                            schedules.add(unquote(lines[index].trim().removePrefix("- ").trim()))
                            index++
                        }
                        continue
                    }
                }
                index++
            }

            result.add(
                ReminderWithSchedules(
                    reminder = Reminder(
                        id = id,
                        title = title,
                        date = date,
                        description = description,
                        time = time,
                        status = status
                    ),
                    schedules = schedules.map { ReminderSchedule(reminderId = id, cronExpression = it) }
                )
            )
        }

        BackupContents(result, scheduledBackupSettings)
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun unquote(value: String): String {
        if (value.length < 2 || !value.startsWith("\"") || !value.endsWith("\"")) return value
        val inner = value.substring(1, value.length - 1)
        val result = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length) {
                when (inner[i + 1]) {
                    'n' -> result.append('\n')
                    '"' -> result.append('"')
                    '\\' -> result.append('\\')
                    else -> result.append(inner[i + 1])
                }
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }
}
