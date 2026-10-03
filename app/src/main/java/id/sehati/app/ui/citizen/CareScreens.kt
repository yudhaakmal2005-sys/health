package id.sehati.app.ui.citizen

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun dial(ctx: android.content.Context, number: String) =
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** Darurat: telepon 119/112, tanda serangan jantung & stroke, langkah pertama, dan panduan pijat jantung (RJP) dengan metronom. */
@Composable
fun EmergencyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenColumn(Modifier.testTag("emergency_screen")) {
        ScreenHeader("Darurat", "Bertindak cepat menyelamatkan nyawa", onBack = onBack)
        SehatiCard(container = RiskRedBg, border = RiskRed.copy(alpha = 0.4f)) {
            Text("Nyeri dada, sesak berat, pingsan, atau wajah mencong mendadak?", style = MaterialTheme.typography.titleMedium, color = RiskRedText, fontWeight = FontWeight.Bold)
            Text("Jangan menunggu. Hubungi ambulans atau segera ke IGD terdekat. Jangan mengemudi sendiri.", style = MaterialTheme.typography.bodyMedium, color = RiskRedText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Telepon 119", { dial(ctx, "119") }, Modifier.weight(1f), icon = Icons.Rounded.Call, tag = "call_119")
                SecondaryButton("Telepon 112", { dial(ctx, "112") }, Modifier.weight(1f), icon = Icons.Rounded.Call, tag = "call_112")
            }
            Text("119 = layanan ambulans/gawat darurat (PSC 119). 112 = nomor darurat umum.", style = MaterialTheme.typography.bodySmall, color = RiskRedText)
        }

        SectionTitle("Tanda serangan jantung")
        Checklist(listOf(
            "Nyeri, rasa tertekan atau berat di dada lebih dari beberapa menit",
            "Nyeri menjalar ke lengan kiri, rahang, leher, atau punggung",
            "Sesak napas, keringat dingin, mual, atau lemas mendadak",
            "Pada perempuan & lansia gejala bisa tidak khas",
        ), Icons.Rounded.Favorite, RiskRed)

        SectionTitle("Langkah pertama")
        Steps(listOf(
            "Hentikan aktivitas, dudukkan dengan nyaman, longgarkan pakaian.",
            "Telepon 119 atau minta orang di sekitar membawa ke IGD terdekat.",
            "Jangan biarkan sendirian. Catat jam mulai keluhan.",
            "Bila tidak sadar dan tidak bernapas normal: mulai pijat jantung (di bawah).",
        ))

        SectionTitle("Tanda stroke: SeGeRa ke RS")
        SehatiCard {
            listOf(
                "Se" to "Senyum tidak simetris (mencong)",
                "Ge" to "Gerak separuh tubuh lemah tiba-tiba",
                "Ra" to "bicaRa pelo atau sulit dipahami",
                "Ke" to "Kebas/baal separuh tubuh",
                "R" to "Rabun mendadak",
                "S" to "Sakit kepala hebat mendadak",
            ).forEach { (k, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 36.dp)) {
                    Box(Modifier.size(36.dp).background(PrimaryLight, CircleShape), contentAlignment = Alignment.Center) { Text(k, style = MaterialTheme.typography.labelLarge, color = PrimaryDark) }
                    Spacer(Modifier.width(12.dp)); Text(v, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
            Text("Waktu sangat menentukan: segera bawa ke rumah sakit.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        SectionTitle("Pijat jantung (RJP) tangan saja")
        CprCoach()
        InfoNote("Panduan ini untuk orang dewasa dan bukan pengganti pelatihan. Ikuti instruksi petugas 119 di telepon.", icon = Icons.Rounded.Info)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Checklist(items: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    SehatiCard {
        items.forEach {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 2.dp)); Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun Steps(items: List<String>) {
    SehatiCard {
        items.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(26.dp).background(Primary, CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(10.dp)); Text(t, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Metronom 110 kali/menit dengan getar & bunyi, hitungan 30 tekanan per siklus. */
@Composable
private fun CprCoach() {
    val haptic = LocalHapticFeedback.current
    var running by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(1f) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(running) {
        count = 0
        while (running && isActive) {
            count = count % 30 + 1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            pulse.snapTo(1.18f); pulse.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            delay(545L - 380L) // 110 kali/menit ≈ 545 ms per tekanan
        }
    }
    SehatiCard(Modifier.testTag("cpr_coach")) {
        Steps(listOf(
            "Pastikan aman, tepuk bahu dan panggil. Tidak sadar & tidak bernapas normal → telepon 119.",
            "Letakkan tumit telapak tangan di tengah dada, tangan lain di atasnya, lengan lurus.",
            "Tekan kuat 5–6 cm, cepat 100–120 kali per menit, biarkan dada kembali penuh.",
            "Jangan berhenti sampai petugas datang atau orang mulai bernapas.",
        ))
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(150.dp).scale(if (running) pulse.value else 1f).background(if (running) RiskRed else RiskRedBg, CircleShape)
                    .semantics { contentDescription = if (running) "Metronom berjalan, tekanan ke $count dari 30" else "Metronom berhenti"; liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (running) "$count" else "110", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = if (running) Color.White else RiskRedText)
                    Text(if (running) "dari 30" else "kali/menit", style = MaterialTheme.typography.labelMedium, color = if (running) Color.White else RiskRedText)
                }
            }
        }
        if (running) Text("Tekan setiap kali lingkaran berdenyut", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        PrimaryButton(if (running) "Berhenti" else "Mulai metronom RJP", { running = !running }, icon = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, tag = "cpr_toggle")
    }
}

private enum class BreathPhase(val label: String, val seconds: Int) { IN("Tarik napas", 4), HOLD("Tahan", 4), OUT("Hembuskan perlahan", 6) }

/** Latihan napas 4-4-6 untuk menurunkan stres. Lingkaran membesar/mengecil mengikuti fase. */
@Composable
fun BreathingScreen(onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var minutes by rememberSaveable { mutableIntStateOf(2) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.IN) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var cycles by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val size = remember { Animatable(0.55f) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        finished = false; cycles = 0
        val end = System.currentTimeMillis() + minutes * 60_000L
        while (running && isActive && System.currentTimeMillis() < end) {
            for (p in BreathPhase.entries) {
                phase = p
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val target = when (p) { BreathPhase.IN -> 1f; BreathPhase.HOLD -> 1f; BreathPhase.OUT -> 0.55f }
                val anim = launch { size.animateTo(target, tween(p.seconds * 1000, easing = LinearEasing)) }
                for (s in p.seconds downTo 1) { secondsLeft = s; delay(1000) }
                anim.cancel()
            }
            cycles++
        }
        if (running) { finished = true; running = false }
    }

    ScreenColumn(Modifier.testTag("breathing_screen")) {
        ScreenHeader("Latihan napas", "Tenangkan pikiran, ringankan kerja jantung", onBack = onBack)
        SehatiCard {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    drawCircle(WellnessLight, radius = this.size.minDimension / 2)
                    drawCircle(Wellness.copy(alpha = 0.85f), radius = this.size.minDimension / 2 * size.value)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(if (running) phase.label else if (finished) "Selesai, kerja bagus!" else "Siap?", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    if (running) Text("$secondsLeft", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (running || finished) "$cycles siklus" else "Pola 4-4-6: tarik 4 detik, tahan 4 detik, hembuskan 6 detik.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!running) ChoiceChips(listOf(1, 2, 5), minutes, { minutes = it }, { "$it menit" }, tagPrefix = "breath_min")
            PrimaryButton(if (running) "Berhenti" else "Mulai", { running = !running }, icon = if (running) Icons.Rounded.Stop else Icons.Rounded.SelfImprovement, tag = "breath_toggle")
        }
        InfoNote("Latihan napas rutin membantu mengelola stres, salah satu faktor yang memengaruhi tekanan darah. Hentikan bila pusing.", icon = Icons.Rounded.Spa)
    }
}
