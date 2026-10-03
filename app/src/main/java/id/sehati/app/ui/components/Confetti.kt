package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import id.sehati.app.ui.theme.*
import kotlin.random.Random

private data class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/** Hujan konfeti singkat untuk merayakan pencapaian. Tidak tampil bila "kurangi animasi" aktif. */
@Composable
fun Confetti(trigger: Any?, modifier: Modifier = Modifier, durationMs: Int = 1800) {
    if (LocalReduceMotion.current || trigger == null) return
    val colors = listOf(Primary, Wellness, RiskYellow, RiskRed, Color(0xFF6366F1), Color(0xFF0EA5A4))
    val pieces = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(70) { Piece(r.nextFloat(), r.nextFloat() * 0.35f, 0.7f + r.nextFloat() * 0.6f, (r.nextFloat() - 0.5f) * 0.25f, r.nextFloat() * 720f, colors[r.nextInt(colors.size)], 6f + r.nextFloat() * 6f, 10f + r.nextFloat() * 8f) }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val local = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + local * p.speed * size.height * 1.1f
            val x = (p.x + p.drift * local) * size.width
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}
