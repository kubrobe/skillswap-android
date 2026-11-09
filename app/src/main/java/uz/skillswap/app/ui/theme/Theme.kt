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

// SkillSwap brand: graphite + gold (serious, premium; gold = the credit coin)
val BrandStart = Color(0xFF2B2E36)      // graphite - start of the header gradient
val BrandEnd = Color(0xFF6E5420)        // dark gold - end of the header gradient
val GoldPrimary = Color(0xFFE0B44C)     // buttons, selected tab, accents
val GoldDeep = Color(0xFFB8892B)
val Lavender = Color(0xFFF2D28B)        // light gold: "Upcoming" status (name kept for older code)
val OnGold = Color(0xFF1A1407)          // dark text on gold, for strong contrast

private val LightColors = lightColorScheme(
    primary = GoldDeep, onPrimary = OnGold,
    primaryContainer = Color(0xFFF6E7C1), onPrimaryContainer = Color(0xFF5A4310),
    secondary = Color(0xFF8A6A22), onSecondary = Color.White,
    background = Color(0xFFF7F6F2), onBackground = Color(0xFF1A1B1F),
    surface = Color.White, onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFEFEDE7), onSurfaceVariant = Color(0xFF5E5A50),
    outline = Color(0xFFC9C4B8), outlineVariant = Color(0xFFE6E2D8),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = GoldPrimary, onPrimary = OnGold,
    primaryContainer = Color(0xFF3A3020), onPrimaryContainer = Color(0xFFF2D28B),
    secondary = Color(0xFFF2D28B), onSecondary = OnGold,
    background = Color(0xFF111317), onBackground = Color(0xFFF5F3EE),
    surface = Color(0xFF1B1D23), onSurface = Color(0xFFF5F3EE),
    surfaceVariant = Color(0xFF25272E), onSurfaceVariant = Color(0xFFA9A59B),
    outline = Color(0xFF4A4A50), outlineVariant = Color(0xFF2C2E35),
    surfaceContainer = Color(0xFF17191E), surfaceContainerHigh = Color(0xFF22242B),
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
