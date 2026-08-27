package com.gotify.client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat




val GotifyBlue        = Color(0xFF55B7FF)
val GotifyBlueDark    = Color(0xFF0068B7)
val GotifyBlueLight   = Color(0xFF9DD5FF)


val SurfaceDark       = Color(0xFF0B1220)
val SurfaceDark2      = Color(0xFF131C2D)
val SurfaceDark3      = Color(0xFF1D2A40)
val SurfaceBorder     = Color(0xFF33445F)


val AccentGreen       = Color(0xFF40C49D)
val AccentOrange      = Color(0xFFD88921)
val AccentRed         = Color(0xFFE2535F)
val AccentPurple      = Color(0xFFD7A8FF)



private val DarkColorScheme = darkColorScheme(
    primary            = GotifyBlue,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF123B5D),
    onPrimaryContainer = GotifyBlueLight,

    secondary          = Color(0xFF8B949E),
    onSecondary        = Color.Black,
    secondaryContainer = SurfaceDark3,
    onSecondaryContainer = Color(0xFFCDD9E5),

    tertiary           = AccentPurple,
    onTertiary         = Color.Black,

    background         = SurfaceDark,
    onBackground       = Color(0xFFE6EDF3),

    surface            = SurfaceDark2,
    onSurface          = Color(0xFFE6EDF3),
    surfaceVariant     = SurfaceDark3,
    onSurfaceVariant   = Color(0xFF8B949E),
    surfaceTint        = GotifyBlue,

    outline            = SurfaceBorder,
    outlineVariant     = Color(0xFF21262D),

    error              = AccentRed,
    onError            = Color.White,
    errorContainer     = Color(0xFF3D1A1A),
    onErrorContainer   = Color(0xFFFFB3B3),
)



private val LightColorScheme = lightColorScheme(
    primary            = GotifyBlueDark,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFDCEEFF),
    onPrimaryContainer = Color(0xFF003566),

    secondary          = Color(0xFF57606A),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFF6F8FA),
    onSecondaryContainer = Color(0xFF24292F),

    background         = Color(0xFFF5F7FB),
    onBackground       = Color(0xFF24292F),

    surface            = Color.White,
    onSurface          = Color(0xFF24292F),
    surfaceVariant     = Color(0xFFEEF2F9),
    onSurfaceVariant   = Color(0xFF57606A),

    outline            = Color(0xFFD0D7DE),
)



val GotifyTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(letterSpacing = (-0.25).sp),
    titleLarge = Typography().titleLarge.copy(letterSpacing = (-0.15).sp),
    titleMedium = Typography().titleMedium.copy(letterSpacing = 0.sp),
    bodyLarge = Typography().bodyLarge.copy(lineHeight = 25.sp),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 21.sp)
)

val GotifyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)



@Composable
fun GotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }


    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = GotifyTypography,
        shapes      = GotifyShapes,
        content     = content
    )
}



@Composable
fun priorityColor(priority: Int): Color = when {
    priority == 0    -> MaterialTheme.colorScheme.outlineVariant
    priority <= 3    -> MaterialTheme.colorScheme.onSurfaceVariant
    priority <= 7    -> AccentOrange
    else             -> AccentRed
}

@Composable
fun priorityLabel(priority: Int): String = when {
    priority == 0    -> "Silent"
    priority <= 3    -> "Low"
    priority <= 7    -> "Normal"
    else             -> "High"
}
