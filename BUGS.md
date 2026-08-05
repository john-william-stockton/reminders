# Bugs

Known defects in the current codebase. Update this alongside bug fixes and new discoveries.

## Open

### Completed reminders are nearly invisible in the Complete tab
`ReminderListItem` applies `.alpha(if (reminder.complete) 0.1f else 1.0f)` to the entire row
(`component/ReminderListItem.kt`), so every item in the **Complete** tab renders at 10% opacity —
effectively unreadable against the surface background.

### Alarm screen lost its urgent styling
`AlarmScreen`'s `Surface` (`alarm/AlarmActivity.kt`) now uses
`MaterialTheme.colorScheme.background` instead of `errorContainer`, so the full-screen alarm looks
like any other screen in the app instead of standing out as an alert.
