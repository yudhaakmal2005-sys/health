package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.*

/** Grafik garis tren (mis. tekanan darah) dengan garis ambang opsional; menggambar bertahap saat muncul. */
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
    LaunchedEffect(series) { if (!reduce) progress.animateTo(1f, tween(Motion.Long + 200, easing = Motion.Emphasized)) }
    val all = series.flatMap { it.second }
    if (all.isEmpty()) return
    val lo = minOf(all.min(), threshold ?: Float.MAX_VALUE) - 5f
    val hi = maxOf(all.max(), threshold ?: Float.MIN_VALUE) + 5f
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width; val h = size.height
            fun y(v: Float) = h - (v - lo) / (hi - lo) * h
            threshold?.let {
                drawLine(RiskOrange.copy(alpha = 0.6f), Offset(0f, y(it)), Offset(w, y(it)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            series.forEachIndexed { si, (_, vals) ->
                val n = vals.size
                val xs = vals.indices.map { if (n == 1) w / 2 else w * it / (n - 1) }
                val path = Path()
                vals.forEachIndexed { i, v -> if (i == 0) path.moveTo(xs[i], y(v)) else path.lineTo(xs[i], y(v)) }
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * progress.value, seg, true)
                drawPath(seg, colors[si % colors.size], style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                vals.forEachIndexed { i, v ->
                    if (i.toFloat() / (n - 1).coerceAtLeast(1) <= progress.value) {
                        drawCircle(Color.White, 5.dp.toPx(), Offset(xs[i], y(v)))
                        drawCircle(colors[si % colors.size], 4.dp.toPx(), Offset(xs[i], y(v)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEachIndexed { i, (name, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[i % colors.size], CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
        Canvas(Modifier.weight(1f).height(14.dp)) {
            drawRoundRect(color.copy(alpha = 0.15f), size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
            if (p.value > 0f) drawRoundRect(color, size = Size(size.width * p.value, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
        }
        Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.width(48.dp).padding(start = 8.dp))
    }
}
