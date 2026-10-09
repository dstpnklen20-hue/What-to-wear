package ru.whattowear.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.whattowear.app.R

/** Единственный шрифт приложения — Inter (OFL), вшит в APK. */
val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val AppTypography = Typography(
    // Крупная текущая температура
    displayLarge = style(88, 92, FontWeight.Light, -0.04),
    displayMedium = style(56, 60, FontWeight.Light, -0.03),
    displaySmall = style(40, 44, FontWeight.Light, -0.02),
    headlineLarge = style(30, 36, FontWeight.Normal, -0.01),
    headlineMedium = style(26, 32, FontWeight.Normal, -0.01),
    headlineSmall = style(22, 28, FontWeight.Medium, -0.005),
    titleLarge = style(20, 26, FontWeight.Medium),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium, 0.01),
    labelSmall = style(11, 14, FontWeight.Medium, 0.02),
)
