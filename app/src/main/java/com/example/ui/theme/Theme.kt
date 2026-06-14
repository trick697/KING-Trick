package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LavenderColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = LavenderTertiary,
    background = LavenderBackground,
    surface = LavenderSurface,
    onPrimary = LavenderBackground,
    onSecondary = LavenderBackground,
    onTertiary = LavenderBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

private val SakuraColorScheme = darkColorScheme(
    primary = SakuraPrimary,
    secondary = SakuraSecondary,
    tertiary = SakuraTertiary,
    background = SakuraBackground,
    surface = SakuraSurface,
    onPrimary = SakuraBackground,
    onSecondary = SakuraBackground,
    onTertiary = SakuraBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

private val CyberColorScheme = darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberBackground,
    surface = CyberSurface,
    onPrimary = CyberBackground,
    onSecondary = Color.White,
    onTertiary = CyberBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

private val ForestColorScheme = darkColorScheme(
    primary = ForestPrimary,
    secondary = ForestSecondary,
    tertiary = ForestTertiary,
    background = ForestBackground,
    surface = ForestSurface,
    onPrimary = ForestBackground,
    onSecondary = ForestBackground,
    onTertiary = ForestBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
  themeName: String = "DREAMY_LAVENDER",
  content: @Composable () -> Unit,
) {
  val colorScheme = when (themeName) {
      "SAKURA_BLOSSOM" -> SakuraColorScheme
      "CYBER_NEON" -> CyberColorScheme
      "FOREST_SPIRIT" -> ForestColorScheme
      else -> LavenderColorScheme
  }

  val context = LocalContext.current

  MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
  )
}
