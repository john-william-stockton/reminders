package net.johnstocktoniv.reminders.backup

import android.content.Context

// A snapshot of the three settings below, for round-tripping through ReminderBackup's YAML
// alongside the reminders themselves — so restoring a backup also restores how/whether scheduled
// backup was configured, not just the reminder data.
data class ScheduledBackupSettings(
    val enabled: Boolean,
    val cronExpression: String,
    val destinationTreeUri: String?
)

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

    fun setDestinationTreeUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_DESTINATION_TREE_URI, uri).apply()
    }

    fun currentSettings(context: Context): ScheduledBackupSettings = ScheduledBackupSettings(
        enabled = isEnabled(context),
        cronExpression = getCronExpression(context),
        destinationTreeUri = getDestinationTreeUri(context)
    )

    // Restoring destinationTreeUri here only reinstates the *string* — a SAF persistable grant
    // can't be silently re-acquired without the user going through the folder picker again, so a
    // URI restored from a backup made on a different device/install (or after the grant was
    // otherwise revoked) will fail on first write. That failure is already handled gracefully
    // (see ScheduledBackupReceiver's try/catch + failure notification), so this doesn't need its
    // own special-casing here.
    fun applySettings(context: Context, settings: ScheduledBackupSettings) {
        setEnabled(context, settings.enabled)
        setCronExpression(context, settings.cronExpression)
        setDestinationTreeUri(context, settings.destinationTreeUri)
    }
}
