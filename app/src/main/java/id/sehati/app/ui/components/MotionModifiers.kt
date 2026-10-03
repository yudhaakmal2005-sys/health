package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.LocalReduceMotion
import id.sehati.app.ui.theme.Motion
import id.sehati.app.ui.theme.motionSpring
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalDensity

/** Masuk bertahap (fade + naik) untuk daftar/kartu. */
fun Modifier.staggerIn(index: Int, stepMs: Int = 55): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val px = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduce) {
            kotlinx.coroutines.delay((index.coerceAtMost(10)) * stepMs.toLong())
            progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 220f))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * px
        val sc = 0.94f + 0.06f * p
        scaleX = sc; scaleY = sc
    }
}

/** Umpan balik sentuh: kartu/tombol mengecil sedikit saat ditekan. */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, motionSpring(), label = "press")
    scale(s)
}

/** Denyut jantung (skala) — dipakai pada logo & indikator penting. */
@Composable
fun heartbeatScale(): State<Float> {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "heartbeat")
    return t.animateFloat(
        initialValue = 1f, targetValue = if (reduce) 1f else 1.14f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "hb",
    )
}

@Composable
fun shimmerAlpha(): State<Float> {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "shimmer")
    return t.animateFloat(
        initialValue = 0.45f, targetValue = if (reduce) 0.45f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sh",
    )
}

fun Modifier.shimmer(): Modifier = composed {
    val a by shimmerAlpha()
    alpha(a)
}


/** Melayang naik-turun pelan (dekorasi). Diam bila "kurangi animasi" aktif. */
fun Modifier.floating(amplitudeDp: Float = 6f, periodMs: Int = 2600, phase: Float = 0f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = androidx.compose.animation.core.LinearEasing)), label = "f")
    val px = with(LocalDensity.current) { amplitudeDp.dp.toPx() }
    graphicsLayer { if (!reduce) translationY = kotlin.math.sin((v + phase) * 2f * Math.PI.toFloat()) * px }
}

/** Efek "memantul" sekali saat [key] berubah, mis. ketika ceklis tercapai. */
fun Modifier.popOnChange(key: Any?): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val s = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (!reduce) { s.snapTo(0.6f); s.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 400f)) }
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}
