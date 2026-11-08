package uz.skillswap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.skillswap.app.R

// SkillSwap brand: indigo and violet
val BrandStart = Color(0xFF6857F5)
val BrandEnd = Color(0xFF873BE8)
val Lavender = Color(0xFFB39AFF)

private val LightColors = lightColorScheme(
    primary = BrandStart, onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4FF), onPrimaryContainer = Color(0xFF3A2FB0),
    secondary = BrandEnd, onSecondary = Color.White,
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF14162B),
    surface = Color.White, onSurface = Color(0xFF14162B),
    surfaceVariant = Color(0xFFECEEF8), onSurfaceVariant = Color(0xFF5B607A),
    outline = Color(0xFFB9BED6), outlineVariant = Color(0xFFE1E4F2),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6857F5), onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2650), onPrimaryContainer = Color(0xFFD9D2FF),
    secondary = Lavender, onSecondary = Color(0xFF1B1140),
    background = Color(0xFF0D0E1A), onBackground = Color(0xFFF4F3FF),
    surface = Color(0xFF191B30), onSurface = Color(0xFFF4F3FF),
    surfaceVariant = Color(0xFF20223A), onSurfaceVariant = Color(0xFFA6A8C2),
    outline = Color(0xFF4A4E6B), outlineVariant = Color(0xFF2A2D47),
    surfaceContainer = Color(0xFF15172A), surfaceContainerHigh = Color(0xFF20223A),
    error = Color(0xFFEF5350)
)

// Poppins is bundled inside the app (res/font), so it looks the same on every phone
private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Poppins),
    displayMedium = base.displayMedium.copy(fontFamily = Poppins),
    displaySmall = base.displaySmall.copy(fontFamily = Poppins),
    headlineLarge = base.headlineLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Poppins),
    bodyMedium = base.bodyMedium.copy(fontFamily = Poppins),
    bodySmall = base.bodySmall.copy(fontFamily = Poppins),
    labelLarge = base.labelLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun SkillSwapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
