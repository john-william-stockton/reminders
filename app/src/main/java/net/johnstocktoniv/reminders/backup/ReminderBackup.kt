package net.johnstocktoniv.reminders.backup

import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.storageDateFormatter
import net.johnstocktoniv.reminders.database.storageTimeFormatter
import java.time.LocalDate
import java.time.LocalTime

// Hand-rolled YAML (de)serialization, deliberately scoped to exactly the shape this app persists
// rather than a general-purpose YAML implementation — fromYaml() only needs to round-trip what
// toYaml() emits (including a human hand-editing a value in place).
object ReminderBackup {
    fun toYaml(reminders: List<ReminderWithSchedules>): String = buildString {
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
            appendLine("    complete: ${reminder.complete}")
            if (schedules.isEmpty()) {
                appendLine("    schedules: []")
            } else {
                appendLine("    schedules:")
                schedules.forEach { appendLine("      - ${quote(it.cronExpression)}") }
            }
        }
    }.trimEnd('\n')

    fun fromYaml(yaml: String): Result<List<ReminderWithSchedules>> = runCatching {
        val lines = yaml.lines()
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
            var complete = false
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
                    field.startsWith("complete:") -> complete = field.removePrefix("complete:").trim().toBooleanStrict()
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
                        complete = complete
                    ),
                    schedules = schedules.map { ReminderSchedule(reminderId = id, cronExpression = it) }
                )
            )
        }

        result
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
