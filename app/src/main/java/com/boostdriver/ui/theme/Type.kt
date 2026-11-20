package com.boostdriver.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Use the system Roboto family across all supported versions without a font download.
private val AppFontFamily = FontFamily.Default

private fun type(size: Int, lineHeight: Int, label: Boolean = false) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = if (label) FontWeight.Medium else FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

internal val AppTypography = Typography(
    displayLarge = type(45, 52),
    displayMedium = type(36, 44),
    displaySmall = type(36, 44),
    headlineLarge = type(28, 36),
    headlineMedium = type(28, 36),
    headlineSmall = type(22, 28),
    titleLarge = type(22, 28),
    titleMedium = type(16, 24, label = true),
    titleSmall = type(14, 20, label = true),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 20),
    bodySmall = type(12, 16),
    labelLarge = type(14, 20, label = true),
    labelMedium = type(12, 16, label = true),
    labelSmall = type(12, 16, label = true),
)
