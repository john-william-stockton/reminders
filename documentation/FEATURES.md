# Features
A snapshot of what's implemented today and what's still open. Update this alongside feature work.

## Not yet implemented

- Add a "Missed" reminder state so that they can be open/incomplete, closed/complete, missed/incomplete, and deleted
- Streak tracking. For series reminders, keep track of the completion streak and include it in the `ReminderListItem`
  - Marking a reminder as complete will reset the streak back to zero if the streak was negative and will add 1 to the streak.
  - Marking a reminder as missed will reset the streak back to zero if the streak was positive and will subtract 1 from the streak
  - Negative simply shows a streak in the wrong direction show -x as `X days missed` instead of `-X day streak`
- Allow creating reminders on dates that have already passed, but guard it behind a user confirmation dialog

## Implemented

- **Reminders**
  - Create, edit, and delete reminders (title required; optional description)
  - Every reminder is driven by one or more CRON schedules — no separate date/time input. A plain
    schedule (e.g. `0 9 * * 1-5`) repeats; appending a year (e.g. `30 9 25 12 * 2026`) pins it to a
    single instant instead, which is how a one-off reminder is expressed. A live "next occurrence"
    preview is shown while editing, computed by always searching forward from now — a schedule
    with no remaining future match can't be saved, so a reminder can't be created in the past
  - Form validation with inline error messages (title, CRON expressions)
  - Completing a reminder automatically spawns its next occurrence (copying its schedules) and
    re-arms its alarm, if its schedule(s) have one; a one-off (pinned-year) reminder has no next
    occurrence once its year has passed, so nothing spawns — same as completing today's one-time
    reminders used to behave
  - Un-completing a reminder re-arms its alarm
  - Overdue reminders are visually flagged in the list, and the flag refreshes automatically
    (checked every 30s) while the list stays on screen
  - Editing a reminder that's genuinely part of a series (its schedule would still fire again
    after its own occurrence, and it's not already complete) prompts to apply the change to just
    this occurrence or the whole series. "Whole series" is an ordinary save. "This occurrence
    only" spawns the next occurrence from the pre-edit schedule (exactly as completing it would),
    then detaches the current row into a standalone one-off pinned to its own already-fixed date/
    time, carrying the user's title/description edits — the series continues untouched

- **List & navigation**
  - Three tabs: **Today**, **Incomplete**, **Complete**
  - The "Today" tab also surfaces still-incomplete reminders left over from earlier dates
    (overdue), sorted above today's own reminders (oldest overdue first); an overdue reminder
    that's already complete is not shown there
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
    - v3 → v4: backfills a pinned-year CRON schedule (from its own date/time) onto any reminder
      predating the always-CRON model, so nothing loses its due date
  - Backup/restore: export all reminders (with their CRON schedules) to a YAML file via the system
    file picker, and restore from a previously exported file — a destructive action gated behind
    an "Are you sure?" confirmation, since it wipes and replaces the current database wholesale.
    Restored reminders keep their original ids so their alarms line up, and all alarms are
    cancelled/re-armed around the restore
  - Scheduled backup: an opt-in, CRON-driven periodic export (default `0 */8 * * *`, editable) to
    a folder chosen once via the system folder picker. Each run writes a new timestamped
    `reminders-backup-<yyyy-MM-dd'T'HHmmss>.yaml` snapshot (same format as manual export) and
    prunes the folder down to the newest 7 — unlike manual export's single overwritten file, this
    keeps real backup history. Uses exact alarms when permitted (same fallback as reminder alarms)
    and self-reschedules on each run (a CRON schedule isn't a fixed interval `AlarmManager` can
    repeat on its own); re-armed on boot and app launch alongside reminders. Posts a low-importance
    notification confirming each run's success (with the saved filename) or failure (e.g. the
    chosen folder became inaccessible)

- **Testing**
  - Unit tests for CRON schedule parsing and next-occurrence computation, including the optional
    year field (`CronScheduleTest`), and for the YAML backup round-trip and malformed-input
    handling (`ReminderBackupTest`). `AlarmScheduler`'s spawn/detach logic (`completeAndAdvance`,
    `editOccurrence`) isn't unit-tested directly since it needs a real `Context`/`AlarmManager` —
    exercised indirectly through the Compose UI tests below instead. `ScheduledBackupReceiverTest`
    covers the scheduled-backup filename format against its own pruning regex (a mismatch there
    once meant pruning silently matched nothing); the rest of that receiver needs a real
    `ContentResolver`/`DocumentsContract` tree and isn't unit-tested
  - Compose UI tests for the reminder and backup dialogs, the alarm screen, and the main list
    screen (`ReminderDialogTest`, `BackupDialogTest`, `AlarmScreenTest`, `RemindersScreenTest`).
    The main screen's UI lives in a dependency-free `RemindersScreen` composable (mirroring the
    `AlarmActivity`/`AlarmScreen` split) so it can be tested without a real device database or
    system permissions
  - `MainActivityTest` launches the real `Main` Activity end-to-end (real Compose wiring, real
    `AlarmScheduler` calls, permission-request navigation pre-granted via shell so it never fires)
    — `DatabaseProvider.overrideForTesting()` swaps in an in-memory database first, so it never
    touches the real on-disk `reminders.db` and is safe to run against a device with real saved
    data. All Compose UI test files use the `androidx.compose.ui.test.junit4.v2` rule factories
    (`StandardTestDispatcher`-based) rather than the deprecated v1 ones — the v1 ones'
    `UnconfinedTestDispatcher` handoff between `Dispatchers.Main` overrides didn't always restore
    cleanly across ~70 sequential tests in one instrumentation process, intermittently stalling
    `MainActivityTest`'s real coroutine work when it ran late in a full suite (never reproduced in
    isolation)
  - DAO test coverage for the backup restore path (`ReminderDaoTest`'s `restoreAll_*` tests)