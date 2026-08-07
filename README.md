# Reminders

A native Android reminders app built with Kotlin and Jetpack Compose. Every reminder is driven by
one or more CRON expressions — a plain expression repeats, and appending an optional year field
pins it to a single instant, which is how a one-off reminder is expressed — and fires as a
full-screen alarm even when the device is locked.

## Requirements

- Android Studio (recent stable)
- JDK 11
- Android SDK: `minSdk` 26, `targetSdk`/`compileSdk` 37

## Getting started

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew installDebug    # install to a connected device/emulator
./gradlew test            # run unit tests
./gradlew connectedAndroidTest  # run instrumented tests
```

On first launch the app requests notification, exact-alarm, and display-over-other-apps
permissions, which it needs to reliably show full-screen alarms.

## Tech stack

- **UI**: Jetpack Compose + Material 3
- **Persistence**: Room (`room3`), with migrations in
  `app/src/main/java/net/johnstocktoniv/reminders/database/Migrations.kt`
- **Scheduling**: `AlarmManager` (exact alarms where permitted, inexact fallback otherwise),
  rearmed on boot and app launch
- **Scheduling syntax**: [cron-utils](https://github.com/jmrozanec/cron-utils), with a custom
  `CronDefinition` in `alarm/CronSchedule.kt` — the same 5 UNIX CRON fields, plus an optional
  trailing year field for one-off (single-instant) reminders
- **Launch**: `androidx.core:core-splashscreen`

## Project layout

```
app/src/main/java/net/johnstocktoniv/reminders/
├── Main.kt                 # Main activity: list UI, tabs, dialogs
├── alarm/                  # Alarm scheduling, receivers, full-screen alarm UI
├── backup/                  # YAML export/restore (de)serialization
├── component/              # Compose dialogs and list item
├── database/                # Room entities, DAO, migrations
└── ui/theme/                 # Compose theme
```

## Documentation

See [FEATURES.md](documentation/FEATURES.md) for a list of implemented features and known gaps, and
[BUGS.md](documentation/BUGS.md) for known defects.
