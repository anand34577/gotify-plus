package com.gotify.client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat




val GotifyBlue        = Color(0xFF0A84FF)
val GotifyBlueDark    = Color(0xFF0066CC)
val GotifyBlueLight   = Color(0xFF5AC8FA)


val SurfaceDark       = Color(0xFF0D1117)
val SurfaceDark2      = Color(0xFF161B22)
val SurfaceDark3      = Color(0xFF21262D)
val SurfaceBorder     = Color(0xFF30363D)


val AccentGreen       = Color(0xFF3FB950)
val AccentOrange      = Color(0xFFD29922)
val AccentRed         = Color(0xFFF85149)
val AccentPurple      = Color(0xFFBC8CFF)



private val DarkColorScheme = darkColorScheme(
    primary            = GotifyBlue,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF003566),
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

    background         = Color(0xFFF6F8FA),
    onBackground       = Color(0xFF24292F),

    surface            = Color.White,
    onSurface          = Color(0xFF24292F),
    surfaceVariant     = Color(0xFFF6F8FA),
    onSurfaceVariant   = Color(0xFF57606A),

    outline            = Color(0xFFD0D7DE),
)



val GotifyTypography = Typography()



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
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = GotifyTypography,
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
