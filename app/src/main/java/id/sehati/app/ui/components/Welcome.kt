package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.delay

/** Logo SEHATI: hati berdenyut di dalam cincin gradien yang berputar. Cincin diam bila "kurangi animasi" aktif. */
@Composable
fun HeartLogo(size: Dp = 120.dp, onDark: Boolean = false, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "logo")
    val angle by t.animateFloat(0f, if (reduce) 0f else 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ring")
    val beat by heartbeatScale()
    val ringColors = if (onDark) listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.9f), Color.White)
    else listOf(Primary.copy(alpha = 0f), Wellness, Primary)
    val track = if (onDark) Color.White.copy(alpha = 0.18f) else PrimaryLight
    Box(modifier.size(size).semantics { contentDescription = "Logo SEHATI" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * 0.07f
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
            rotate(angle) {
                drawArc(Brush.sweepGradient(ringColors), 0f, 300f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier.size(size * 0.62f).scale(beat).background(if (onDark) Color.White else Primary, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Favorite, null, tint = if (onDark) PrimaryDark else Color.White, modifier = Modifier.size(size * 0.34f)) }
    }
}

/** Garis detak (EKG) yang tergambar perlahan. */
@Composable
fun EcgLine(modifier: Modifier = Modifier, color: Color = Color.White) {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) p.animateTo(1f, tween(1400, easing = Motion.Standard)) }
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val w = size.width; val h = size.height; val m = h / 2
        val path = Path().apply {
            moveTo(0f, m)
            lineTo(w * 0.30f, m); lineTo(w * 0.36f, m - h * 0.18f); lineTo(w * 0.41f, m + h * 0.10f)
            lineTo(w * 0.47f, m - h * 0.48f); lineTo(w * 0.53f, m + h * 0.42f); lineTo(w * 0.58f, m)
            lineTo(w * 0.66f, m); lineTo(w * 0.71f, m - h * 0.14f); lineTo(w * 0.76f, m); lineTo(w, m)
        }
        val measure = PathMeasure().also { it.setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * p.value, seg, true)
        drawPath(seg, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Layar sambutan singkat setelah masuk. Ketuk untuk melewati; dilewati otomatis bila "kurangi animasi" aktif. */
@Composable
fun WelcomeOverlay(name: String, roleLabel: String, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) { delay(if (reduce) 0L else 2600L); done() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(600, 150, Motion.Emphasized)) }
    Box(
        Modifier.fillMaxSize().testTag("welcome_overlay")
            .background(Brush.verticalGradient(HeroGradient))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            HeartLogo(132.dp, onDark = true)
            Spacer(Modifier.height(28.dp))
            Column(Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 24f }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Selamat datang", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                Text(name, style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.testTag("welcome_name"))
                Spacer(Modifier.height(6.dp))
                Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            EcgLine(Modifier.width(220.dp).alpha(0.9f))
            Spacer(Modifier.height(16.dp))
            Text("Jaga jantungmu, mulai hari ini.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
