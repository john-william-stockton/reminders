# Bugs

Known defects in the current codebase. Update this alongside bug fixes and new discoveries.

## Open

(none currently)

## Resolved

- **A second alarm arriving while one was already showing could be lost.** `AlarmReceiver` launches
  `AlarmActivity` with `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`, so if the activity is
  already on screen, Android reuses that instance and delivers the new alarm via `onNewIntent()`
  rather than `onCreate()`. `AlarmActivity` didn't override `onNewIntent()` (or call `setIntent()`),
  so `extras` — computed via `by lazy` from `intent` — kept referring to the first reminder: the
  screen kept showing it, and completing/snoozing acted on its id, not the newly-arrived one. Fixed
  by overriding `onNewIntent()` to call `setIntent()` and refresh `extras` (now a Compose-observable
  `mutableStateOf`, not a one-shot `lazy`), so the screen and its actions always track whichever
  alarm most recently arrived.
- **Alarm screen's back button bypassed the "no plain dismiss" design.** `AlarmActivity` never
  intercepted the system back gesture/button, so pressing back finished the activity, running
  `onDestroy()` → `stopAlerting()` (cancels vibration, clears the notification) without going
  through `complete()` or `snooze()` — silently dismissing the alarm. Fixed by registering an
  `onBackPressedDispatcher` callback in `onCreate()` that swallows back presses, leaving Mark
  Complete/Snooze as the only ways to silence the alarm.
- **Adding a CRON expression to a reminder and then reopening it for editing didn't reflect the
  newly added expression — persisted even after making `onSaveReminder` a suspend callback that's
  awaited before the dialog closes (which fixed a real but ultimately unrelated DB-write race).**
  The actual cause: `ReminderRow`'s `Modifier.pointerInput(Unit) { detectTapGestures(...) }` keys
  its gesture-detection coroutine on `Unit`, so it launches once per list item and never restarts.
  Its `onLongPress`/`onTap` lambdas were called directly (not via `rememberUpdatedState`), so they
  stayed bound to whichever `reminderWithSchedules` closure existed at that item's *first*
  composition — reopening the same on-screen row for editing kept showing that original data no
  matter how many times it was saved, until the row itself was torn down and recomposed fresh
  (switching tabs, reopening the app). Fixed by wrapping `onLongPress`/`onTap` in
  `rememberUpdatedState` so the always-running gesture coroutine reads the latest closure.
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
