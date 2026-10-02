package id.sehati.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Satu sumber kebenaran untuk gerak: durasi & easing konsisten; hormati preferensi "kurangi animasi". */
val LocalReduceMotion = compositionLocalOf { false }

object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    const val Short = 180
    const val Medium = 320
    const val Long = 700
    const val Ring = 1000
}

@Composable
fun <T> motionTween(duration: Int = Motion.Medium, delay: Int = 0): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) tween(0) else tween(duration, delay, Motion.Emphasized)

@Composable
fun <T> motionSpring(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduce = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
