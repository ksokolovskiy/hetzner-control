package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared design-contract tokens. Features use semantic Material roles, never copy hex values. */
object HCloudDesignTokens {
    val spaceSmall = 4.dp
    val spaceMedium = 8.dp
    val spaceLarge = 12.dp
    val contentInset = 16.dp
    val sectionGap = 24.dp
    val minimumTarget = 48.dp
    val minimumRow = 62.dp
    val cardShape = RoundedCornerShape(16.dp)
    val compactShape = RoundedCornerShape(12.dp)
}

/** Confirmed means verified provider state, never merely an accepted request. */
data class HCloudSemanticColors(val confirmed: Color, val warning: Color, val information: Color)

val hCloudSemanticColors: HCloudSemanticColors
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        HCloudSemanticColors(Color(0xFF78DBAD), Color(0xFFFFC078), Color(0xFFB6C4FF))
    } else {
        HCloudSemanticColors(Color(0xFF147D58), Color(0xFFA65D00), Color(0xFF315AE8))
    }

val HCloudLightScheme = lightColorScheme(
    primary = Color(0xFF0059B8), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FA), onPrimaryContainer = Color(0xFF00315F),
    secondary = Color(0xFF435466), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF1FA), onSecondaryContainer = Color(0xFF17212F),
    background = Color(0xFFF3F5F8), onBackground = Color(0xFF17212F),
    surface = Color.White, onSurface = Color(0xFF17212F),
    surfaceVariant = Color(0xFFEAF1FA), onSurfaceVariant = Color(0xFF526176),
    outline = Color(0xFF677489), outlineVariant = Color(0xFFDFE5ED),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFF0EE), onErrorContainer = Color(0xFF8C1414),
)

val HCloudDarkScheme = darkColorScheme(
    primary = Color(0xFF9DC5FF), onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF20324A), onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFFC0CAD9), onSecondary = Color(0xFF20324A),
    secondaryContainer = Color(0xFF222D3B), onSecondaryContainer = Color(0xFFE0E7F2),
    background = Color(0xFF10151D), onBackground = Color(0xFFE0E7F2),
    surface = Color(0xFF161D27), onSurface = Color(0xFFE0E7F2),
    surfaceVariant = Color(0xFF222D3B), onSurfaceVariant = Color(0xFFC0CAD9),
    outline = Color(0xFF8A98AA), outlineVariant = Color(0xFF3C4858),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E2020), onErrorContainer = Color(0xFFFFDAD6),
)

val HCloudDesignTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

val HCloudDesignShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = HCloudDesignTokens.compactShape,
    medium = HCloudDesignTokens.cardShape,
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HCloudDesignTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) HCloudDarkScheme else HCloudLightScheme,
        typography = HCloudDesignTypography,
        shapes = HCloudDesignShapes,
        content = content,
    )
}
