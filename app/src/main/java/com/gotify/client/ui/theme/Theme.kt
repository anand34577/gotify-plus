package com.gotify.client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat


val GotifyBlue = Color(0xFF0A84FF)
val GotifyBlueDark = Color(0xFF0066CC)
val GotifyBlueLight = Color(0xFF5AC8FA)


val SurfaceDark = Color(0xFF0D1117)
val SurfaceDark2 = Color(0xFF161B22)
val SurfaceDark3 = Color(0xFF21262D)
val SurfaceBorder = Color(0xFF30363D)


val AccentGreen = Color(0xFF3FB950)
val AccentOrange = Color(0xFFD29922)
val AccentRed = Color(0xFFF85149)
val AccentPurple = Color(0xFFBC8CFF)


private val DarkColorScheme = darkColorScheme(
    primary = GotifyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003566),
    onPrimaryContainer = GotifyBlueLight,

    secondary = Color(0xFF8B949E),
    onSecondary = Color.Black,
    secondaryContainer = SurfaceDark3,
    onSecondaryContainer = Color(0xFFCDD9E5),

    tertiary = AccentPurple,
    onTertiary = Color.Black,

    background = SurfaceDark,
    onBackground = Color(0xFFE6EDF3),

    surface = SurfaceDark2,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = SurfaceDark3,
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceTint = GotifyBlue,

    outline = SurfaceBorder,
    outlineVariant = Color(0xFF21262D),

    error = AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFF3D1A1A),
    onErrorContainer = Color(0xFFFFB3B3),
)


private val LightColorScheme = lightColorScheme(
    primary = GotifyBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEFF),
    onPrimaryContainer = Color(0xFF003566),

    secondary = Color(0xFF57606A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6F8FA),
    onSecondaryContainer = Color(0xFF24292F),

    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF24292F),

    surface = Color.White,
    onSurface = Color(0xFF24292F),
    surfaceVariant = Color(0xFFF6F8FA),
    onSurfaceVariant = Color(0xFF57606A),

    outline = Color(0xFFD0D7DE),
)


private val DraculaColorScheme = darkColorScheme(
    primary = Color(0xFFBD93F9), // Purple
    onPrimary = Color(0xFF282A36),
    primaryContainer = Color(0xFF44475A),
    onPrimaryContainer = Color(0xFFF8F8F2),

    secondary = Color(0xFFFF79C6), // Pink
    onSecondary = Color(0xFF282A36),
    secondaryContainer = Color(0xFF44475A),
    onSecondaryContainer = Color(0xFFF8F8F2),

    background = Color(0xFF282A36),
    onBackground = Color(0xFFF8F8F2),

    surface = Color(0xFF282A36),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF44475A),
    onSurfaceVariant = Color(0xFF6272A4),

    outline = Color(0xFF6272A4),
    
    error = Color(0xFFFF5555),
    onError = Color(0xFFF8F8F2)
)

private val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0), // Frost Blue
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = Color(0xFFECEFF4),

    secondary = Color(0xFF81A1C1),
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFF434C5E),
    onSecondaryContainer = Color(0xFFECEFF4),

    background = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4),

    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFFD8DEE9),

    outline = Color(0xFF4C566A),
    
    error = Color(0xFFBF616A),
    onError = Color(0xFFECEFF4)
)

private val AmoledColorScheme = darkColorScheme(
    primary = GotifyBlue,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = GotifyBlueLight,

    secondary = Color(0xFF8B949E),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color.White,

    background = Color.Black,
    onBackground = Color.White,

    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF8B949E),

    outline = Color(0xFF333333),
    
    error = AccentRed,
    onError = Color.White
)

val CustomFontFamily = FontFamily.SansSerif

val GotifyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = CustomFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)


@Composable
fun GotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeSelection: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> when (themeSelection) {
            "AMOLED" -> AmoledColorScheme
            "DRACULA" -> DraculaColorScheme
            "NORD" -> NordColorScheme
            else -> DarkColorScheme
        }
        else -> LightColorScheme
    }


    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GotifyTypography,
        content = content
    )
}


@Composable
fun priorityColor(priority: Int): Color = when {
    priority == 0 -> MaterialTheme.colorScheme.outlineVariant
    priority <= 3 -> MaterialTheme.colorScheme.onSurfaceVariant
    priority <= 7 -> AccentOrange
    else -> AccentRed
}

@Composable
fun priorityLabel(priority: Int): String = when {
    priority == 0 -> "Silent"
    priority <= 3 -> "Low"
    priority <= 7 -> "Normal"
    else -> "High"
}
