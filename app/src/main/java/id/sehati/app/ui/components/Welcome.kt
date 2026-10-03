package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.delay

/** Logo SEHATI: tanda hati sederhana dalam kotak bersudut lembut. Muncul sekali dengan fade halus; tidak berdenyut. */
@Composable
fun HeartLogo(size: Dp = 120.dp, onDark: Boolean = false, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) appear.animateTo(1f, tween(Motion.Medium, easing = Motion.Emphasized)) }
    Box(
        modifier.size(size)
            .graphicsLayer { alpha = appear.value; val s = 0.97f + 0.03f * appear.value; scaleX = s; scaleY = s }
            .background(if (onDark) Color.White else Primary, RoundedCornerShape(size * 0.28f))
            .semantics { contentDescription = "Logo SEHATI" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Favorite, null, tint = if (onDark) PrimaryDark else Color.White, modifier = Modifier.size(size * 0.5f)) }
}

/** Garis detak (EKG) statis dan tipis — dekorasi opsional, tanpa animasi. */
@Composable
fun EcgLine(modifier: Modifier = Modifier, color: Color = Hairline) {
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val w = size.width; val h = size.height; val m = h / 2
        val path = Path().apply {
            moveTo(0f, m)
            lineTo(w * 0.30f, m); lineTo(w * 0.36f, m - h * 0.18f); lineTo(w * 0.41f, m + h * 0.10f)
            lineTo(w * 0.47f, m - h * 0.48f); lineTo(w * 0.53f, m + h * 0.42f); lineTo(w * 0.58f, m)
            lineTo(w * 0.66f, m); lineTo(w * 0.71f, m - h * 0.14f); lineTo(w * 0.76f, m); lineTo(w, m)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Sambutan singkat (≈1 detik) setelah masuk: fade tenang, lalu hilang sendiri. Ketuk untuk melewati. */
@Composable
fun WelcomeOverlay(name: String, roleLabel: String, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) { delay(if (reduce) 0L else 1000L); done() }
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) appear.animateTo(1f, tween(Motion.Medium, easing = Motion.Standard)) }
    Box(
        Modifier.fillMaxSize().testTag("welcome_overlay")
            .background(Background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).graphicsLayer { alpha = appear.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeartLogo(48.dp)
            Spacer(Modifier.height(20.dp))
            Text("Selamat datang", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.testTag("welcome_name"))
            Spacer(Modifier.height(4.dp))
            Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text("Jaga jantungmu, mulai hari ini.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
