package id.sehati.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 8.dp)) {
            ScreenHeader(title, subtitle, onBack = onBack)
            Spacer(Modifier.height(8.dp))
            StepIndicator(step, total)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 5 * dir } + fadeIn(tween(Motion.Medium))) togetherWith
                        (slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 5 * dir } + fadeOut(tween(Motion.Short)))
                },
                label = "wizard",
            ) { _ ->
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
                )
            }
        }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error != null) Text(error, color = RiskRedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("wizard_error"))
            PrimaryButton(primaryText, onPrimary, enabled = primaryEnabled, loading = primaryLoading, tag = primaryTag)
            if (secondaryText != null && onSecondary != null) TextButton(onSecondary, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(secondaryText) }
        }
    }
}

/** Stepper angka (+/−) dengan target sentuh 48dp. */
@Composable
fun NumberStepper(
    label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier,
    unit: String = "", step: Int = 1, tag: String = "stepper",
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(48.dp).testTag("${tag}_minus")) {
            Icon(Icons.Rounded.Remove, contentDescription = "Kurangi $label")
        }
        Text("$value$unit", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = "$label $value $unit" })
        FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(48.dp).testTag("${tag}_plus")) {
            Icon(Icons.Rounded.Add, contentDescription = "Tambah $label")
        }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TextSecondary, modifier = modifier.padding(top = 4.dp))
}
