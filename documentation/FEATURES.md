# Features
A snapshot of what's implemented today and what's still open. Update this alongside feature work.

## Not yet implemented

- Allow creating reminders on dates that have already passed, but guard it behind a user confirmation dialog

## Implemented

- **Reminders**
  - Create, edit, and delete reminders (title required; optional description)
  - Each reminder has a status — **Open**, **Complete**, or **Missed**. Missed is a manual action
    (only offered once a reminder is actually overdue, or to revert an already-Missed one back to
    Open), distinct from the display-only "Overdue" flag below — an overdue reminder stays Open
    until the user explicitly marks it Missed. Missed is treated as terminal/resolved (like
    Complete) for tab and stacking purposes — see "List & navigation" below — even though it isn't
    Complete either; marking a recurring reminder Missed advances its series (cancels its alarm,
    spawns the next occurrence) exactly like completing it would, so a missed occurrence doesn't
    block the next one
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
  - Overdue reminders are visually flagged in the list; the flag flips the instant a reminder
    passes its due time while the list stays on screen, via a delay scheduled precisely to that
    due instant rather than polling on some fixed interval
  - Editing a reminder that's genuinely part of a series (its schedule would still fire again
    after its own occurrence, and it's not already complete) prompts to apply the change to just
    this occurrence or the whole series. "Whole series" is an ordinary save. "This occurrence
    only" spawns the next occurrence from the pre-edit schedule (exactly as completing it would),
    then detaches the current row into a standalone one-off pinned to its own already-fixed date/
    time, carrying the user's title/description edits — the series continues untouched
  - A genuinely recurring reminder tracks a completion streak (`Reminder.streak`), propagated
    forward onto each newly spawned occurrence the same way title/description/schedules already
    are. Completing resets a negative streak to 0 then adds 1; marking Missed resets a positive
    streak to 0 then subtracts 1 — so alternating complete/miss never lets the "wrong direction"
    count linger. Shown in `ReminderListItem` only for series reminders with a nonzero streak: "N
    day(s) streak" (green) for positive, "N day(s) missed" (red) for negative — never the naive
    "-N day streak". Reopening a Complete/Missed reminder back to Open leaves its streak as-is
    (not specified, and not cleanly reversible once a next occurrence may have already spawned
    from it)

- **List & navigation**
  - Three tabs: **Today**, **Incomplete**, **Complete**. "Incomplete" is strictly Open reminders
    (things still to do) — Missed doesn't appear there even though it isn't Complete either, since
    it's a resolved/terminal status (see below); "Complete" is strictly Complete
  - The "Today" tab also surfaces still-unresolved (Open or Missed) reminders left over from
    earlier dates, sorted above today's own reminders (oldest first); an overdue reminder that's
    already complete is not shown there
  - Swipe left to mark complete (toggles: swiping an already-complete reminder reopens it).
    Swipe right to mark Missed/un-Missed, the same toggle pattern — only enabled once a reminder
    is actually overdue, or on an already-Missed one to reopen it; disabled otherwise rather than
    swiping to a no-op
  - Long-press a reminder to open it for editing, which is also where deleting it lives now (a
    "Delete" button, gated behind an "Are you sure?" confirmation since a single tap doesn't have
    the friction a swipe distance does)
  - On the Complete tab, the floating action button doubles as "Clear All" — a red-tinted trash
    icon that bulk-deletes completed reminders behind a confirmation dialog, hidden entirely when
    there's nothing to clear; on other tabs it's the usual add-reminder button
  - In the "Today" tab, resolved reminders (Complete *and* Missed — both terminal, dimmed the same
    way) default to a collapsed, overlapping card stack (each one behind and peeking out from
    under the reminder above it, with a slight drop shadow to reinforce the layering, and its
    description hidden to save space); tapping any reminder toggles the stack between collapsed
    and fully expanded (descriptions reappear once expanded)

- **Alarms & notifications**
  - Full-screen alarm activity that shows over the lock screen and vibrates (no alarm sound, by
    design — accessibility choice for hearing-impaired users)
  - Alarm screen actions: Mark Complete or Snooze 2 minutes (no plain dismiss — one of the two
    is required to silence the alarm). Snoozing only re-arms the alarm 2 minutes out — it doesn't
    touch the reminder's own date/time, so an overdue reminder stays Overdue through a snooze
    instead of appearing freshly due later
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
    - v4 → v5: replaces the `complete: Boolean` column with `status` (Open/Complete/Missed)
    - v5 → v6: adds the `streak` counter column (defaults existing rows to 0)
  - Backup/restore: export all reminders (with their CRON schedules) to a YAML file via the system
    file picker, and restore from a previously exported file — a destructive action gated behind
    an "Are you sure?" confirmation, since it wipes and replaces the current database wholesale.
    Restored reminders keep their original ids so their alarms line up, and all alarms are
    cancelled/re-armed around the restore. A backup also captures the scheduled-backup settings
    below (enabled/CRON/destination folder) in an optional `scheduledBackup:` section, so restoring
    one restores that configuration too — absent in backups made before this existed, and the
    restored destination folder is only as good as whichever device/install still holds a valid
    permission grant for it (restoring it elsewhere fails gracefully, same as any other backup
    write failure, rather than silently re-granting access)
  - Scheduled backup: an opt-in, CRON-driven periodic export (default `0 */8 * * *`, editable) to
    a folder chosen once via the system folder picker. Each run writes a new timestamped
    `reminders-backup-<yyyy-MM-dd'T'HHmmss>.yaml` snapshot (same format as manual export, including
    its own current settings) and prunes the folder down to the newest 7 — unlike manual export's
    single overwritten file, this keeps real backup history. Uses exact alarms when permitted (same
    fallback as reminder alarms) and self-reschedules on each run (a CRON schedule isn't a fixed
    interval `AlarmManager` can repeat on its own); re-armed on boot and app launch alongside
    reminders. Posts a low-importance notification confirming each run's success (with the saved
    filename) or failure (e.g. the chosen folder became inaccessible)

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