package net.johnstocktoniv.reminders.database

import androidx.room3.ColumnTypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Storage formats are locale-independent and fixed-width so they sort correctly
// as plain strings (e.g. in ORDER BY). Display formatting is a separate concern,
// handled by dateFormatter/timeFormatter in Reminder.kt.
internal val storageDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val storageTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

object DateConverters {
    @ColumnTypeConverter
    fun localDateToString(date: LocalDate): String = date.format(storageDateFormatter)

    @ColumnTypeConverter
    fun stringToLocalDate(string: String): LocalDate = LocalDate.parse(string, storageDateFormatter)

    @ColumnTypeConverter
    fun localTimeToString(time: LocalTime): String = time.format(storageTimeFormatter)

    @ColumnTypeConverter
    fun stringToLocalTime(string: String): LocalTime = LocalTime.parse(string, storageTimeFormatter)
}