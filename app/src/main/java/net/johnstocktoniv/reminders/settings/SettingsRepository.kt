package net.johnstocktoniv.reminders.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

object SettingsRepository {
    val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(8, 0)

    private const val PREFS_NAME = "reminders_settings"
    private const val KEY_DEFAULT_REMINDER_TIME_MINUTES = "default_reminder_time_minutes"

    @Volatile private var state: MutableStateFlow<LocalTime>? = null

    // Returns the same cached flow instance on every call (not a fresh asStateFlow() wrapper)
    // so callers like collectAsStateWithLifecycle key off a stable identity across recompositions.
    fun defaultReminderTime(context: Context): StateFlow<LocalTime> = stateFlow(context)

    fun getDefaultReminderTime(context: Context): LocalTime = stateFlow(context).value

    fun setDefaultReminderTime(context: Context, time: LocalTime) {
        prefs(context).edit()
            .putInt(KEY_DEFAULT_REMINDER_TIME_MINUTES, time.hour * 60 + time.minute)
            .apply()
        stateFlow(context).value = time
    }

    private fun stateFlow(context: Context): MutableStateFlow<LocalTime> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(readTime(context)).also { state = it }
        }

    private fun readTime(context: Context): LocalTime {
        val minutes = prefs(context).getInt(KEY_DEFAULT_REMINDER_TIME_MINUTES, -1)
        if (minutes < 0) return DEFAULT_REMINDER_TIME
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
