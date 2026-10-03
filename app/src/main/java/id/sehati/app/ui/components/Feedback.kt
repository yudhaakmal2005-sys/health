package id.sehati.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sehati.app.domain.model.PROFILE_DISCLAIMER
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.model.SyncStatus
import id.sehati.app.domain.rules.RedFlag
import id.sehati.app.domain.rules.Severity
import id.sehati.app.ui.theme.*

data class RiskStyle(val color: Color, val bg: Color, val text: Color, val icon: ImageVector)

fun RiskLevel.style() = when (this) {
    RiskLevel.HEALTHY_HABIT -> RiskStyle(RiskGreen, RiskGreenBg, RiskGreenText, Icons.Rounded.CheckCircle)
    RiskLevel.RISK_AWARENESS -> RiskStyle(RiskYellow, RiskYellowBg, RiskYellowText, Icons.Outlined.Visibility)
    RiskLevel.HIGHER_MONITORING -> RiskStyle(RiskOrange, RiskOrangeBg, RiskOrangeText, Icons.Outlined.MonitorHeart)
    RiskLevel.MEDICAL_FOLLOW_UP -> RiskStyle(RiskRed, RiskRedBg, RiskRedText, Icons.Outlined.MedicalServices)
}

fun Severity.style() = when (this) {
    Severity.INFO -> RiskStyle(RiskGreen, RiskGreenBg, RiskGreenText, Icons.Rounded.CheckCircle)
    Severity.WATCH -> RiskStyle(RiskYellow, RiskYellowBg, RiskYellowText, Icons.Outlined.Info)
    Severity.ATTENTION -> RiskStyle(RiskOrange, RiskOrangeBg, RiskOrangeText, Icons.Outlined.Warning)
    Severity.URGENT -> RiskStyle(RiskRed, RiskRedBg, RiskRedText, Icons.Outlined.Error)
}

/** Warna risiko selalu disertai ikon + label + penjelasan (tidak mengandalkan warna saja). */
@Composable
fun RiskCard(level: RiskLevel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, compact: Boolean = false) {
    val st = level.style()
    SehatiCard(modifier.testTag("health_status_card"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 36.dp).clip(RoundedCornerShape(2.dp)).background(st.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PROFIL SEHATI", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.2.sp)
                Text(level.label, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            StatusPill(level.shortLabel(), st.text, st.bg, st.icon)
        }
        Text(level.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (!compact) Text(PROFILE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
fun SyncChip(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, c, bg) = when (status) {
        SyncStatus.SYNCED -> Triple(Icons.Outlined.CloudDone, RiskGreenText, RiskGreenBg)
        SyncStatus.SYNCING -> Triple(Icons.Outlined.Sync, PrimaryDark, PrimaryLight)
        SyncStatus.SYNC_FAILED -> Triple(Icons.Outlined.SyncProblem, RiskRedText, RiskRedBg)
        SyncStatus.LOCAL_ONLY -> Triple(Icons.Outlined.CloudOff, RiskYellowText, RiskYellowBg)
    }
    StatusPill(status.label, c, bg, icon, modifier.semantics { contentDescription = "Status sinkronisasi: ${status.label}" })
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(icon, TextMuted, SurfaceMuted, 56)
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) SecondaryButton(action, onAction, Modifier.widthIn(max = 280.dp))
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Memuat…") {
    Column(modifier.fillMaxWidth().padding(24.dp).testTag("loading_state"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, retry: String? = null, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp).testTag("error_state"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(Icons.Outlined.ErrorOutline, RiskRedText, RiskRedBg, 64)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (retry != null && onRetry != null) SecondaryButton(retry, onRetry, Modifier.widthIn(max = 280.dp))
    }
}

@Composable
fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(SurfaceMuted).shimmer())
}

/** Banner gawat darurat untuk gejala serius. Tidak menenangkan; mengarahkan ke bantuan medis. */
@Composable
fun EmergencyBanner(modifier: Modifier = Modifier) {
    val scale by heartbeatScale()
    Surface(modifier.fillMaxWidth().testTag("emergency_banner").semantics { contentDescription = "Peringatan darurat. ${RedFlag.EMERGENCY_MESSAGE}" },
        shape = RoundedCornerShape(18.dp), color = RiskRedBg, border = androidx.compose.foundation.BorderStroke(1.5.dp, RiskRed)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Warning, null, tint = RiskRed, modifier = Modifier.size(28.dp).scale(scale))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(RedFlag.EMERGENCY_TITLE, style = MaterialTheme.typography.titleMedium, color = RiskRedText, fontWeight = FontWeight.Bold)
                Text(RedFlag.EMERGENCY_MESSAGE, style = MaterialTheme.typography.bodyMedium, color = RiskRedText)
            }
        }
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, color: Color = PrimaryDark, bg: Color = PrimaryLight) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = bg) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

/** Cincin progres beranimasi (0 → nilai). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 8.dp,
    color: Color = Primary,
    track: Color = SurfaceMuted,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), motionTween(Motion.Ring), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
            if (anim > 0f) drawArc(color, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun AnimatedNumber(value: Int, style: androidx.compose.ui.text.TextStyle, color: Color = TextPrimary, format: (Int) -> String = { it.toString() }) {
    val v by animateIntAsState(value, motionTween(Motion.Ring), label = "num")
    Text(format(v), style = style, color = color, fontWeight = FontWeight.Bold)
}

@Composable
fun MetricTile(
    icon: ImageVector, label: String, value: String, sub: String, color: Color, bg: Color,
    progress: Float? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tag: String = "",
) {
    SehatiCard(modifier.then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier), onClick = onClick, contentPadding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold)
        if (progress != null) {
            val p by animateFloatAsState(progress.coerceIn(0f, 1f), motionTween(Motion.Long), label = "bar")
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(SurfaceMuted)) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(color))
            }
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** Centang beranimasi setelah data berhasil disimpan. */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, size: Dp = 72.dp, color: Color = Wellness) {
    val reduce = LocalReduceMotion.current
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    val pop = remember { Animatable(if (reduce) 1f else 0.6f) }
    val popSpec = motionSpringSpec()
    LaunchedEffect(Unit) { if (!reduce) pop.animateTo(1f, popSpec) }
    LaunchedEffect(Unit) { if (!reduce) t.animateTo(1f, androidx.compose.animation.core.tween(Motion.Long, easing = Motion.Emphasized)) }
    Canvas(modifier.size(size).scale(pop.value)) {
        val w = this.size.width
        drawCircle(color.copy(alpha = 0.14f))
        drawCircle(color, radius = w / 2 - 3.dp.toPx(), style = Stroke(3.dp.toPx()))
        val p = Path().apply {
            moveTo(w * 0.28f, w * 0.52f); lineTo(w * 0.44f, w * 0.67f); lineTo(w * 0.73f, w * 0.36f)
        }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(p, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * t.value, seg, true)
        drawPath(seg, color, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun motionSpringSpec(): androidx.compose.animation.core.AnimationSpec<Float> =
    androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f)

/** Indikator langkah (wizard Posyandu / onboarding). */
@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier, labels: List<String>? = null) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Langkah ${current + 1} dari $total" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                val c by animateFloatAsState(if (i <= current) 1f else 0f, motionTween(Motion.Medium), label = "step$i")
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(SurfaceMuted)) {
                    Box(Modifier.fillMaxWidth(c).fillMaxHeight().clip(CircleShape).background(Primary))
                }
            }
        }
        if (labels != null) Text("Langkah ${current + 1} dari $total · ${labels.getOrNull(current).orEmpty()}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

private fun RiskLevel.shortLabel() = when (this) {
    RiskLevel.HEALTHY_HABIT -> "Baik"
    RiskLevel.RISK_AWARENESS -> "Waspada"
    RiskLevel.HIGHER_MONITORING -> "Pantau"
    RiskLevel.MEDICAL_FOLLOW_UP -> "Rujuk"
}
