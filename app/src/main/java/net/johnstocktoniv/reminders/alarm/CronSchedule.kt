package net.johnstocktoniv.reminders.alarm

import com.cronutils.model.Cron
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import net.johnstocktoniv.reminders.database.ReminderSchedule
import java.time.LocalDateTime
import java.time.ZoneId

// Same 5 fields as UNIX crontab (minute hour day-of-month month day-of-week), plus an optional
// trailing YEAR field. Every expression already stored under the old plain-UNIX definition still
// parses identically (an omitted year matches every year, same as before) — the extra field only
// exists so a schedule can pin itself to a single instant (e.g. "30 9 25 12 * 2026"), letting a
// one-off reminder be expressed as CRON instead of needing a separate date/time input.
private val cronDefinition = CronDefinitionBuilder.defineCron()
    .withMinutes().withValidRange(0, 59).withStrictRange().and()
    .withHours().withValidRange(0, 23).withStrictRange().and()
    .withDayOfMonth().withValidRange(1, 31).withStrictRange().and()
    .withMonth().withValidRange(1, 12).withStrictRange().and()
    .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0).withStrictRange().and()
    .withYear().withValidRange(1970, 2099).withStrictRange().optional().and()
    .instance()

private val cronParser = CronParser(cronDefinition)

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
