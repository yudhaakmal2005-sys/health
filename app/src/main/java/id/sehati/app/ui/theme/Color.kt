package id.sehati.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palet SEHATI "Merah Hati": hangat, ramah, tetap kontras (teks ≥ 4.5:1 pada latar).
val Background = Color(0xFFFFF8F6)
val CardWhite = Color(0xFFFFFFFF)
val Primary = Color(0xFFE11D48)        // rose-600
val PrimaryDark = Color(0xFFBE123C)    // rose-700 (teks/ikon di atas latar muda)
val PrimaryDeep = Color(0xFF881337)    // rose-900 (awal gradien)
val PrimaryLight = Color(0xFFFFE4E6)   // rose-100
val PrimarySoft = Color(0xFFFFF1F2)    // rose-50
val Coral = Color(0xFFFB7185)          // rose-400
val Sunset = Color(0xFFF97316)         // orange-500 (aksen gradien)
val Wellness = Color(0xFF10B981)
val WellnessDark = Color(0xFF047857)
val WellnessLight = Color(0xFFD1FAE5)
val Calm = Color(0xFF7C3AED)           // ungu lembut untuk tidur/napas
val CalmLight = Color(0xFFEDE9FE)
val TextPrimary = Color(0xFF1C1917)
val TextSecondary = Color(0xFF44403C)
val TextMuted = Color(0xFF78716C)
val BorderColor = Color(0xFFF2E3E1)
val SurfaceMuted = Color(0xFFFBF1EF)

// Warna risiko: semantik, selalu disertai ikon + label + penjelasan
val RiskGreen = Color(0xFF10B981)
val RiskYellow = Color(0xFFF59E0B)
val RiskOrange = Color(0xFFEA580C)
val RiskRed = Color(0xFFB91C1C)
val RiskGreenBg = Color(0xFFECFDF5)
val RiskYellowBg = Color(0xFFFFFBEB)
val RiskOrangeBg = Color(0xFFFFF7ED)
val RiskRedBg = Color(0xFFFEF2F2)
// Teks pada latar tint (kontras ≥ 4.5:1)
val RiskGreenText = Color(0xFF047857)
val RiskYellowText = Color(0xFF92400E)
val RiskOrangeText = Color(0xFF9A3412)
val RiskRedText = Color(0xFF991B1B)

val ActiveMove = Primary
val PassiveMove = Color(0xFFA8A29E)

/** Gradien khas SEHATI untuk header, sambutan, dan tombol utama. */
val HeroGradient = listOf(PrimaryDeep, Primary, Sunset)
fun heroBrush() = Brush.linearGradient(HeroGradient)
