package io.github.eucsoh.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.eucsoh.android.R

private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal),
)

private val LightColors = lightColorScheme(
    // Primaire — bleu principal et ses surfaces
    primary = Color(0xFF3D85DC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4F7),   // bleu très pâle, pour les chips/cards actives
    onPrimaryContainer = Color(0xFF0D2F5C), // bleu nuit, lisible sur fond pâle

    // Secondaire — bleu-gris neutre, pour les éléments d'accentuation secondaire
    secondary = Color(0xFF4F7BAA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEAF5),
    onSecondaryContainer = Color(0xFF1B3550),

    // Tertiaire — bleu sarcelle légèrement chaud, pour varier sans agresser
    tertiary = Color(0xFF3A7D8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCECF2),
    onTertiaryContainer = Color(0xFF0D3540),

    // Erreur — rouge désaturé, pas criard
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // Fond général — gris blanc très légèrement bleuté, pas blanc pur
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF1A1C1E),

    // Surface — blanc cassé propre
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),

    // Surface variante — gris bleuté léger, pour les inputs, dividers, cards neutres
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484F),

    // Outline — pour les bordures (TextField, Card outlinée)
    outline = Color(0xFF71787F),
    outlineVariant = Color(0xFFC1C7CE),

    // Inverse — pour les snackbars, tooltips
    inverseSurface = Color(0xFF2F3133),
    inverseOnSurface = Color(0xFFF0F0F3),
    inversePrimary = Color(0xFFA8C8F5),

    // Scrim — overlay sombre derrière les dialogs
    scrim = Color(0xFF000000),
)
private val DarkColors = darkColorScheme(
    // Primaire — bleu éclairci pour être lisible sur fond sombre
    primary = Color(0xFF92BEF0),
    onPrimary = Color(0xFF0D3566),
    primaryContainer = Color(0xFF1E5499),   // bleu profond, pas noir, pas saturé
    onPrimaryContainer = Color(0xFFD6E4F7), // reprend le light primaryContainer

    // Secondaire
    secondary = Color(0xFF9DBBD8),
    onSecondary = Color(0xFF1B3550),
    secondaryContainer = Color(0xFF2A4E6E),
    onSecondaryContainer = Color(0xFFDCEAF5),

    // Tertiaire
    tertiary = Color(0xFF7EC8D8),
    onTertiary = Color(0xFF0D3540),
    tertiaryContainer = Color(0xFF1A5464),
    onTertiaryContainer = Color(0xFFCCECF2),

    // Erreur
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Fond — pas noir pur, bleu-gris très sombre
    background = Color(0xFF111518),
    onBackground = Color(0xFFE2E5E9),

    // Surface — légèrement plus clair que le fond, perceptible
    surface = Color(0xFF1A1E23),
    onSurface = Color(0xFFE2E5E9),

    // Surface variante — pour inputs, dividers, NavigationBar
    surfaceVariant = Color(0xFF2C3540),
    onSurfaceVariant = Color(0xFFA8B5C2),

    // Outline
    outline = Color(0xFF6B7885),
    outlineVariant = Color(0xFF2C3540),

    // Inverse — snackbars, tooltips
    inverseSurface = Color(0xFFE2E5E9),
    inverseOnSurface = Color(0xFF1A1E23),
    inversePrimary = Color(0xFF3D85DC),  // reprend exactement le primary light

    // Scrim
    scrim = Color(0xFF000000),
)

@Composable
fun EucSohTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        typography = AppTypography,   // tu pourras raffiner plus tard
        content = content
    )
}
