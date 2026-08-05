# Features

A snapshot of what's implemented today and what's still open. Update this alongside feature work.

## Not yet implemented

- No editing of individual occurrences of a recurring reminder (edits apply to the current row
  only; future spawned occurrences reuse the original title/description/schedules)
- No categories, tags, priorities, or search/filter beyond the three tabs
- No custom snooze duration (fixed at 2 minutes)
- No notification sound/vibration customization per reminder
- No sync across devices
- No widgets or wearable support
- No localization (English only; date/time formats are hardcoded, not locale-aware)
- No dark/light theme toggle beyond system default
- No instrumented test launches the real `Main` Activity end-to-end (real Room DB, `AlarmManager`,
  permission-request navigation) — only its extracted `RemindersScreen` composable is covered

## Implemented

- **Reminders**
  - Create, edit, and delete reminders (title required; optional description)
  - One-time reminders: specific date with an optional time
    - Reminders without an explicit time use a configurable default time
  - Recurring reminders: one or more CRON schedules per reminder (UNIX CRON syntax), with a live
    "next occurrence" preview while editing
  - Form validation with inline error messages (title, date, time, CRON expressions)
  - Completing a recurring reminder automatically spawns the next occurrence (copying its schedules)
    and re-arms its alarm
  - Un-completing a reminder re-arms its alarm
  - Overdue reminders are visually flagged in the list, and the flag refreshes automatically
    (checked every 30s) while the list stays on screen

- **List & navigation**
  - Three tabs: **Today**, **Incomplete**, **Complete**
  - Swipe right to delete, swipe left to mark complete
  - Long-press a reminder to edit it
  - On the Complete tab, the floating action button doubles as "Clear All" — a red-tinted trash
    icon that bulk-deletes completed reminders behind a confirmation dialog, hidden entirely when
    there's nothing to clear; on other tabs it's the usual add-reminder button
  - In the "Today" tab, completed reminders default to a collapsed, overlapping card stack (each
    one behind and peeking out from under the reminder above it, with a slight drop shadow to
    reinforce the layering, and its description hidden to save space); tapping any completed
    reminder toggles the stack between collapsed and fully expanded (descriptions reappear once
    expanded)

- **Alarms & notifications**
  - Full-screen alarm activity that shows over the lock screen and vibrates (no alarm sound, by
    design — accessibility choice for hearing-impaired users)
  - Alarm screen actions: Mark Complete or Snooze 2 minutes (no plain dismiss — one of the two
    is required to silence the alarm)
  - Notification with a full-screen intent (auto-launches when locked) and heads-up fallback when
    unlocked
  - Alarms are rescheduled on app launch and after device reboot
  - Uses exact alarms when permitted, falling back to inexact scheduling otherwise

- **Settings**
  - Configurable default reminder time for reminders without an explicit time
  - Changing the default time re-arms affected reminders immediately

- **Branding**
  - Adaptive launcher icon (plus a monochrome variant for Android 13+ themed icons): a
    checklist glyph — one checked row over two pending rows — in the app's own Material 3 purple,
    styled after the iOS Reminders app icon
  - Splash screen (via `androidx.core:core-splashscreen`) reuses the same icon artwork and
    background tone on launch

- **Permissions**
  - Requests notification (`POST_NOTIFICATIONS`), exact-alarm scheduling, and "display over other
    apps" permissions on first launch

- **Data**
  - Room database with versioned migrations
    - v1 → v2: repairs reminders whose `date` was stored in a legacy display format
    - v2 → v3: adds the `reminder_schedules` table for recurring reminders
  - Backup/restore: export all reminders (with their CRON schedules) to a YAML file via the system
    file picker, and restore from a previously exported file — a destructive action gated behind
    an "Are you sure?" confirmation, since it wipes and replaces the current database wholesale.
    Restored reminders keep their original ids so their alarms line up, and all alarms are
    cancelled/re-armed around the restore

- **Testing**
  - Unit tests for CRON schedule parsing and next-occurrence computation
    (`CronScheduleTest`), and for the YAML backup round-trip and malformed-input handling
    (`ReminderBackupTest`)
  - Compose UI tests for the reminder, settings, and backup dialogs, the alarm screen, and the
    main list screen (`ReminderDialogTest`, `SettingsDialogTest`, `BackupDialogTest`,
    `AlarmScreenTest`, `RemindersScreenTest`). The main screen's UI lives in a dependency-free
    `RemindersScreen` composable (mirroring the `AlarmActivity`/`AlarmScreen` split) so it can be
    tested without a real device database or system permissions; `Main`'s own Activity wiring (DB,
    `AlarmManager`, permission requests) is not covered by these tests
  - DAO test coverage for the backup restore path (`ReminderDaoTest`'s `restoreAll_*` tests)