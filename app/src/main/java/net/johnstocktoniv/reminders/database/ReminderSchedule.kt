package net.johnstocktoniv.reminders.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(
    tableName = "reminder_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Reminder::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reminderId")]
)
data class ReminderSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val cronExpression: String
)

data class ReminderWithSchedules(
    @Embedded val reminder: Reminder,
    @Relation(parentColumns = ["id"], entityColumns = ["reminderId"]) val schedules: List<ReminderSchedule>
)
