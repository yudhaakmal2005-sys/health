package id.sehati.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palet SEHATI: netral hangat + satu aksen merah tua yang dipakai hemat (teks ≥ 4.5:1 pada latar).
val Background = Color(0xFFFAF9F8)
val CardWhite = Color(0xFFFFFFFF)
val Primary = Color(0xFFC8102E)        // merah tua (aksen, dipakai hemat)
val PrimaryDark = Color(0xFFA50D26)    // teks/ikon aksen di atas latar muda
val PrimaryDeep = Color(0xFF7A0A1D)
val PrimaryLight = Color(0xFFFBECEE)
val PrimarySoft = Color(0xFFFDF6F7)
val Coral = Color(0xFFD9475C)
val Sunset = Color(0xFFB4232F)         // (dulu oranye; kini merah senada agar tenang)
val Wellness = Color(0xFF15803D)
val WellnessDark = Color(0xFF166534)
val WellnessLight = Color(0xFFEAF6EE)
val Calm = Color(0xFF4A423F)           // netral untuk tidur/napas
val CalmLight = Color(0xFFF3F1EF)
val TextPrimary = Color(0xFF1B1715)
val TextSecondary = Color(0xFF4A423F)
val TextMuted = Color(0xFF7A716D)
val BorderColor = Color(0xFFEBE6E3)
val Hairline = Color(0xFFEBE6E3)
val SurfaceMuted = Color(0xFFF3F1EF)

// Warna risiko: semantik, selalu disertai ikon + label + penjelasan
val RiskGreen = Color(0xFF15803D)
val RiskYellow = Color(0xFFB45309)
val RiskOrange = Color(0xFFC2410C)
val RiskRed = Color(0xFFB91C1C)
val RiskGreenBg = Color(0xFFEAF6EE)
val RiskYellowBg = Color(0xFFFDF3E7)
val RiskOrangeBg = Color(0xFFFFF7ED)
val RiskRedBg = Color(0xFFFDEEEE)
// Teks pada latar tint (kontras ≥ 4.5:1)
val RiskGreenText = Color(0xFF047857)
val RiskYellowText = Color(0xFF92400E)
val RiskOrangeText = Color(0xFF9A3412)
val RiskRedText = Color(0xFF991B1B)

val ActiveMove = Primary
val PassiveMove = Color(0xFFD6CFCB)

/** Gradien tenang (merah tua → merah) — hanya untuk layar sambutan. */
val HeroGradient = listOf(PrimaryDeep, Primary)
fun heroBrush() = Brush.linearGradient(HeroGradient)
