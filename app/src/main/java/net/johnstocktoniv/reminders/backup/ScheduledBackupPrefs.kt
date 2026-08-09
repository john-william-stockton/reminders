package net.johnstocktoniv.reminders.backup

import android.content.Context

// The app's only persisted settings, so a plain named SharedPreferences file is enough — no need
// for DataStore or a Room table for three scalar values.
object ScheduledBackupPrefs {
    const val DEFAULT_CRON = "0 */8 * * *"

    private const val PREFS_NAME = "scheduled_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CRON_EXPRESSION = "cron_expression"
    private const val KEY_DESTINATION_TREE_URI = "destination_tree_uri"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getCronExpression(context: Context): String =
        prefs(context).getString(KEY_CRON_EXPRESSION, null) ?: DEFAULT_CRON

    fun setCronExpression(context: Context, expression: String) {
        prefs(context).edit().putString(KEY_CRON_EXPRESSION, expression).apply()
    }

    fun getDestinationTreeUri(context: Context): String? =
        prefs(context).getString(KEY_DESTINATION_TREE_URI, null)

    fun setDestinationTreeUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_DESTINATION_TREE_URI, uri).apply()
    }
}
