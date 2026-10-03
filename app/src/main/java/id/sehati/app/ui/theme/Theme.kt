package id.sehati.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Scheme = lightColorScheme(
    primary = Primary, onPrimary = Color.White, primaryContainer = PrimaryLight, onPrimaryContainer = PrimaryDark,
    secondary = Wellness, onSecondary = Color.White, secondaryContainer = WellnessLight, onSecondaryContainer = WellnessDark,
    tertiary = RiskOrange, background = Background, onBackground = TextPrimary,
    surface = CardWhite, onSurface = TextPrimary, surfaceVariant = SurfaceMuted, onSurfaceVariant = TextSecondary,
    outline = BorderColor, outlineVariant = BorderColor, error = RiskRed, onError = Color.White, errorContainer = RiskRedBg,
    onErrorContainer = RiskRedText,
)

private val SehatiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SehatiTheme(content: @Composable () -> Unit) {
    ProvideReduceMotion {
        MaterialTheme(colorScheme = Scheme, typography = SehatiTypography, shapes = SehatiShapes, content = content)
    }
}
