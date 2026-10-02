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
    val px = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(Motion.Long, delayMillis = (index.coerceAtMost(10)) * stepMs, easing = Motion.Emphasized))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * px
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
