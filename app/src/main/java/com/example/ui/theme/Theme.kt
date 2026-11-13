package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = LimbahBrandDark,
    onPrimary = LimbahOnBrandDark,
    secondary = LimbahPetAccent,
    onSecondary = Color.White,
    tertiary = LimbahOilAccent,
    onTertiary = LimbahOnBrandDark,
    background = LimbahBgDark,
    onBackground = LimbahInkDark,
    surface = LimbahCardDark,
    onSurface = LimbahInkDark,
    surfaceVariant = LimbahCardDark,
    onSurfaceVariant = LimbahMuteDark,
    outline = LimbahLineDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LimbahBrandLight,
    onPrimary = Color.White,
    secondary = LimbahPetAccent,
    onSecondary = Color.White,
    tertiary = LimbahOilAccent,
    onTertiary = LimbahInkLight,
    background = LimbahBgLight,
    onBackground = LimbahInkLight,
    surface = LimbahCardLight,
    onSurface = LimbahInkLight,
    surfaceVariant = LimbahCardLight,
    onSurfaceVariant = LimbahMuteLight,
    outline = LimbahLineLight,
  )

@Composable
fun Limbah2GOTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
