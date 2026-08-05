# Bugs

Known defects in the current codebase. Update this alongside bug fixes and new discoveries.

## Open

- **Alarm screen's back button bypasses the "no plain dismiss" design.** `AlarmActivity` never
  intercepts the system back gesture/button. Pressing back finishes the activity, which runs
  `onDestroy()` → `stopAlerting()` (cancels vibration, clears the notification) without going
  through `complete()` or `snooze()`. That silently dismisses the alarm — exactly what the UI (no
  dismiss button, per FEATURES.md) is meant to prevent.
- **A second alarm arriving while one is already showing can be lost.** `AlarmReceiver` launches
  `AlarmActivity` with `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`, so if the activity is
  already on screen, Android reuses that instance and delivers the new alarm via `onNewIntent()`
  rather than `onCreate()`. `AlarmActivity` doesn't override `onNewIntent()` (or call
  `setIntent()`), so `extras` — computed lazily from `intent` — keeps referring to the first
  reminder. The screen keeps showing the first reminder, and completing/snoozing from it acts on
  the first reminder's id, not the newly-arrived one.

## Resolved

- **Default `ExampleUnitTest` / `ExampleInstrumentedTest` templates were still unmodified
  boilerplate, not real coverage.** Neither exercised any app code (one asserted `2 + 2 == 4`, the
  other just checked the package name). Real coverage already existed elsewhere (`CronScheduleTest`,
  `ReminderDaoTest`, and the Compose component/screen tests), so both files were deleted rather than
  replaced.
- **Marking reminder as incomplete and then complete again caused duplicate reminders.**
  `AlarmScheduler.completeAndAdvance()` spawns a new `Reminder` row for a recurring reminder's
  next occurrence on completion. Un-completing the original and completing it again re-ran that
  spawn with no memory of the earlier one, leaving two rows for the same occurrence. Fixed by
  having `completeAndAdvance()` check `ReminderDao.findDuplicate()` (matching title, description,
  date, and time on an incomplete reminder) before inserting, so it only skips its own internal
  spawn — manual duplicate creation through the UI is unaffected.
