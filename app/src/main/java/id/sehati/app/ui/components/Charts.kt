package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.*

/**
 * Grafik garis tren (mis. tekanan darah) bergaya tenang: garis tipis netral, kisi tipis, label abu,
 * dan aksen [Primary] hanya pada titik terakhir. Seri pertama TextPrimary, seri berikutnya abu.
 * [colors] dipertahankan untuk kompatibilitas pemanggil; palet netral dipakai agar konsisten.
 */
@Composable
fun TrendChart(
    series: List<Pair<String, List<Float>>>, // nama → nilai
    colors: List<Color>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    threshold: Float? = null,
    description: String = "Grafik tren",
) {
    val reduce = LocalReduceMotion.current
    val progress = remember(series) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(series) { if (!reduce) progress.animateTo(1f, tween(Motion.Long, easing = Motion.Emphasized)) }
    val all = series.flatMap { it.second }
    if (all.isEmpty()) return
    val palette = listOf(TextPrimary, TextMuted.copy(alpha = 0.7f), PassiveMove)
    fun lineColor(i: Int) = palette[i % palette.size]
    val lo = minOf(all.min(), threshold ?: Float.MAX_VALUE) - 5f
    val hi = maxOf(all.max(), threshold ?: Float.MIN_VALUE) + 5f
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width; val h = size.height
            val pad = 6.dp.toPx()
            val plotH = h - pad * 2
            fun y(v: Float) = pad + plotH - (v - lo) / (hi - lo) * plotH
            // Kisi horizontal tipis
            val hair = 1.dp.toPx()
            repeat(4) { k ->
                val gy = pad + plotH * k / 3f
                drawLine(Hairline, Offset(0f, gy), Offset(w, gy), hair)
            }
            threshold?.let {
                drawLine(RiskOrange.copy(alpha = 0.7f), Offset(0f, y(it)), Offset(w, y(it)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            val xPad = 6.dp.toPx()
            series.forEachIndexed { si, (_, vals) ->
                val n = vals.size
                val xs = vals.indices.map { if (n == 1) w / 2 else xPad + (w - xPad * 2) * it / (n - 1) }
                val path = Path()
                vals.forEachIndexed { i, v -> if (i == 0) path.moveTo(xs[i], y(v)) else path.lineTo(xs[i], y(v)) }
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * progress.value, seg, true)
                val c = lineColor(si)
                drawPath(seg, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                vals.forEachIndexed { i, v ->
                    val last = i == n - 1
                    if (i.toFloat() / (n - 1).coerceAtLeast(1) <= progress.value) {
                        if (last) {
                            drawCircle(Color.White, 5.dp.toPx(), Offset(xs[i], y(v)))
                            drawCircle(if (si == 0) Primary else c, 3.5.dp.toPx(), Offset(xs[i], y(v)))
                        } else {
                            drawCircle(Color.White, 3.dp.toPx(), Offset(xs[i], y(v)))
                            drawCircle(c, 2.dp.toPx(), Offset(xs[i], y(v)))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = TextMuted) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEachIndexed { i, (name, vals) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 12.dp, height = 2.dp).background(lineColor(i), RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    vals.lastOrNull()?.let { Text("  ${it.toInt()}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Batang horizontal sederhana untuk distribusi (mis. profil risiko). */
@Composable
fun BarRow(label: String, value: Int, max: Int, color: Color, modifier: Modifier = Modifier, valueLabel: String = value.toString()) {
    val p = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(value, max) { p.animateTo(if (max == 0) 0f else value.toFloat() / max, if (reduce) tween(0) else tween(Motion.Long, easing = Motion.Emphasized)) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(120.dp))
        Canvas(Modifier.weight(1f).height(6.dp)) {
            drawRoundRect(SurfaceMuted, size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            if (p.value > 0f) drawRoundRect(color, size = Size(size.width * p.value, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
        Text(valueLabel, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(56.dp).padding(start = 8.dp))
    }
}
