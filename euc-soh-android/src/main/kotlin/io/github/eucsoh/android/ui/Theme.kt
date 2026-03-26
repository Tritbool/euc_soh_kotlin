package io.github.eucsoh.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB.toInt()),       // Indigo 600
    onPrimary = Color(0xFFFFFFFF.toInt()),
    primaryContainer = Color(0xFFC5CAE9.toInt()),
    onPrimaryContainer = Color(0xFF1A237E.toInt()),

    secondary = Color(0xFF00897B.toInt()),     // Teal 600
    onSecondary = Color(0xFFFFFFFF.toInt()),
    secondaryContainer = Color(0xFFB2DFDB.toInt()),
    onSecondaryContainer = Color(0xFF004D40.toInt()),

    error = Color(0xFFD32F2F.toInt()),
    onError = Color(0xFFFFFFFF.toInt()),

    background = Color(0xFFF5F5F5.toInt()),
    onBackground = Color(0xFF212121.toInt()),

    surface = Color(0xFFFFFFFF.toInt()),
    onSurface = Color(0xFF212121.toInt()),
    surfaceVariant = Color(0xFFE0E0E0.toInt()),
    onSurfaceVariant = Color(0xFF424242.toInt())
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA.toInt()),
    onPrimary = Color(0xFF1A237E.toInt()),
    primaryContainer = Color(0xFF303F9F.toInt()),
    onPrimaryContainer = Color(0xFFC5CAE9.toInt()),

    secondary = Color(0xFF4DB6AC.toInt()),
    onSecondary = Color(0xFF004D40.toInt()),
    secondaryContainer = Color(0xFF00695C.toInt()),
    onSecondaryContainer = Color(0xFFB2DFDB.toInt()),

    error = Color(0xFFEF9A9A.toInt()),
    onError = Color(0xFFB71C1C.toInt()),

    background = Color(0xFF121212.toInt()),
    onBackground = Color(0xFFE0E0E0.toInt()),

    surface = Color(0xFF1E1E1E.toInt()),
    onSurface = Color(0xFFE0E0E0.toInt()),
    surfaceVariant = Color(0xFF424242.toInt()),
    onSurfaceVariant = Color(0xFFBDBDBD.toInt())
)

@Composable
fun EucSohTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColors else LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),   // tu pourras raffiner plus tard
        content = content
    )
}
