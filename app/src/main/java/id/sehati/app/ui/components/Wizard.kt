package id.sehati.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sehati.app.ui.theme.*

/** Kerangka wizard: header + indikator langkah, isi bergulir dengan transisi geser, tombol aksi menempel di bawah. */
@Composable
fun WizardLayout(
    step: Int,
    total: Int,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    primaryText: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    primaryTag: String = "wizard_next_button",
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    error: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 8.dp)) {
            ScreenHeader(title, subtitle, onBack = onBack)
            Spacer(Modifier.height(10.dp))
            StepIndicator(step, total)
            Text("Langkah ${step + 1} dari $total", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = TextMuted, modifier = Modifier.padding(top = 6.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 12 * dir } + fadeIn(tween(Motion.Medium))) togetherWith
                        (slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 12 * dir } + fadeOut(tween(Motion.Short)))
                },
                label = "wizard",
            ) { _ ->
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
                )
            }
        }
        HorizontalDivider(color = Hairline)
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error != null) Text(error, color = RiskRedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("wizard_error"))
            PrimaryButton(primaryText, onPrimary, enabled = primaryEnabled, loading = primaryLoading, tag = primaryTag)
            if (secondaryText != null && onSecondary != null) TextButton(onSecondary, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(secondaryText, color = TextSecondary) }
        }
    }
}

/** Stepper angka (+/−) dengan target sentuh 48dp. */
@Composable
fun NumberStepper(
    label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier,
    unit: String = "", step: Int = 1, tag: String = "stepper", display: ((Int) -> String)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(48.dp).testTag("${tag}_minus"), colors = stepperColors()) {
            Icon(Icons.Outlined.Remove, contentDescription = "Kurangi $label")
        }
        Text(display?.invoke(value) ?: "$value$unit", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = "$label ${display?.invoke(value) ?: "$value $unit"}" })
        FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(48.dp).testTag("${tag}_plus"), colors = stepperColors()) {
            Icon(Icons.Outlined.Add, contentDescription = "Tambah $label")
        }
    }
}

@Composable
private fun stepperColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = SurfaceMuted, contentColor = TextPrimary, disabledContainerColor = SurfaceMuted.copy(alpha = 0.5f), disabledContentColor = TextMuted,
)

/** Judul bagian kecil di dalam formulir/wizard. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 8.dp))
}

/** Label kecil berhuruf kapital di atas bagian (mis. "POSYANDU HARI INI"). Netral, tidak berwarna aksen. */
@Composable
fun OverlineLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = modifier.padding(top = 4.dp))
}

/** KPI: label kecil abu + angka besar tabular. [accent] opsional tampil sebagai titik kecil di depan label. */
@Composable
fun KpiTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color? = null, tag: String = "") {
    SehatiCard(modifier.then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier), contentPadding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) { Box(Modifier.size(6.dp).background(accent, CircleShape)); Spacer(Modifier.width(6.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** Strip tab netral: teks abu, tab terpilih teks gelap + garis bawah tipis [Primary]. Dapat digulir horizontal. */
@Composable
fun <T> NeutralTabStrip(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            tabs.forEach { t ->
                val sel = t == selected
                val fg = if (sel) TextPrimary else TextMuted
                Column(
                    Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
                        .clickable(role = Role.Tab, onClick = { onSelect(t) })
                        .testTag(tag(t))
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(Modifier.padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) { Icon(icon(t), null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(label(t), style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (sel) Primary else Color.Transparent, RoundedCornerShapeTop))
                }
            }
        }
        HorizontalDivider(color = Hairline)
    }
}

private val RoundedCornerShapeTop = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
