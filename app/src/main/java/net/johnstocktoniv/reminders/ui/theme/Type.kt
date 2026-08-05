package net.johnstocktoniv.reminders.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Every rung of the M3 type scale bumped up to the next rung's metrics (labelSmall takes on
// labelMedium's size, labelMedium takes on labelLarge's, labelLarge takes on bodySmall's, and so
// on up the ladder) so text reads one step larger everywhere in the app without touching every
// call site that references a style by name. displayLarge has nothing above it to borrow from,
// so it continues the same size/line-height step as the rung below it.
private fun style(fontSize: Int, lineHeight: Int, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val Typography = Typography(
    labelSmall = style(12, 16, 0.5),
    labelMedium = style(14, 20, 0.1),
    labelLarge = style(12, 16, 0.4),
    bodySmall = style(14, 20, 0.25),
    bodyMedium = style(16, 24, 0.5),
    bodyLarge = style(14, 20, 0.1),
    titleSmall = style(16, 24, 0.15),
    titleMedium = style(22, 28, 0.0),
    titleLarge = style(24, 32, 0.0),
    headlineSmall = style(28, 36, 0.0),
    headlineMedium = style(32, 40, 0.0),
    headlineLarge = style(36, 44, 0.0),
    displaySmall = style(45, 52, 0.0),
    displayMedium = style(57, 64, -0.25),
    displayLarge = style(69, 76, -0.25)
)
