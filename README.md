# Reminders

A native Android reminders app built with Kotlin and Jetpack Compose. Reminders can be
one-time (a specific date and optional time) or recurring via CRON expressions, and fire as
full-screen alarms even when the device is locked.

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
- **Recurring schedules**: [cron-utils](https://github.com/jmrozanec/cron-utils) (UNIX CRON
  syntax)

## Project layout

```
app/src/main/java/net/johnstocktoniv/reminders/
├── Main.kt                 # Main activity: list UI, tabs, dialogs
├── alarm/                  # Alarm scheduling, receivers, full-screen alarm UI
├── component/              # Compose dialogs and list item
├── database/                # Room entities, DAO, migrations
├── settings/                # Default reminder time (SharedPreferences-backed)
└── ui/theme/                 # Compose theme
```

## Documentation

See [FEATURES.md](documentation/FEATURES.md) for a list of implemented features and known gaps, and
[BUGS.md](documentation/BUGS.md) for known defects.
