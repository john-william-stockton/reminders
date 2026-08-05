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
