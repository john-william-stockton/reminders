package net.johnstocktoniv.reminders.alarm

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import net.johnstocktoniv.reminders.database.ReminderSchedule
import java.time.LocalDateTime
import java.time.ZoneId

private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

fun parseCronOrNull(expression: String): Cron? =
    runCatching { cronParser.parse(expression).validate() }.getOrNull()

// minOrNull() across every schedule's next match is what guarantees overlapping schedules on the
// same reminder collapse to a single next occurrence instead of producing more than one.
fun nextOccurrence(schedules: List<ReminderSchedule>, after: LocalDateTime): LocalDateTime? {
    val zonedAfter = after.atZone(ZoneId.systemDefault())
    return schedules
        .mapNotNull { parseCronOrNull(it.cronExpression) }
        .mapNotNull { ExecutionTime.forCron(it).nextExecution(zonedAfter).orElse(null) }
        .minOrNull()
        ?.toLocalDateTime()
}
