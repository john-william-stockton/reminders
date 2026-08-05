package net.johnstocktoniv.reminders.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Pinned to the Material 3 baseline error-container tones (rather than whatever a dynamic
// color scheme derives from wallpaper) so the full-screen alarm always reads as an alert
// without landing on a jarringly saturated red.
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

// Pale yellow for the alarm screen's Snooze button. Pinned rather than theme-derived, same
// reasoning as the error container above.
val SnoozeContainer = Color(0xFFFFF3B0)
val OnSnoozeContainer = Color(0xFF4A3B00)

// Green for "complete" actions — the swipe-to-complete gesture and the alarm screen's Mark
// Complete button. Pinned rather than primaryContainer so it reads as a consistent green
// regardless of dynamic color / the app's purple primary.
val SuccessContainer = Color(0xFFC8E6C9)
val OnSuccessContainer = Color(0xFF1B5E20)