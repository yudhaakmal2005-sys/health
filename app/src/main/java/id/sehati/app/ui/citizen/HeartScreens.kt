package id.sehati.app.ui.citizen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

internal fun challengeIcon(key: String): ImageVector = when (key) {
    "walk" -> Icons.Rounded.DirectionsWalk
    "salt" -> Icons.Rounded.Grain
    "smoke" -> Icons.Rounded.SmokeFree
    "water" -> Icons.Rounded.WaterDrop
    "veg" -> Icons.Rounded.Eco
    else -> Icons.Rounded.Bedtime
}

private data class BandStyle(val text: Color, val bg: Color, val icon: ImageVector)

private fun HeartBand.style() = when (this) {
    HeartBand.INSUFFICIENT -> BandStyle(TextSecondary, SurfaceMuted, Icons.Rounded.HelpOutline)
    HeartBand.FEW -> BandStyle(RiskGreenText, RiskGreenBg, Icons.Rounded.CheckCircle)
    HeartBand.SOME -> BandStyle(RiskYellowText, RiskYellowBg, Icons.Rounded.Info)
    HeartBand.MANY -> BandStyle(RiskOrangeText, RiskOrangeBg, Icons.Rounded.Warning)
}

/** Bagian "Jantung Sehat" di beranda: pilar harian, faktor risiko, tantangan, fakta hari ini. */
@Composable
fun HeartHomeSection(
    onOpenRisk: () -> Unit, onOpenChallenges: () -> Unit,
    vm: HeartViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading) return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("heart_section")) {
        SectionTitle("Jantung sehat hari ini")
        val done = s.pillars.count { it.done }
        val celebrate = if (done == 5) java.time.LocalDate.now().toString() else null
        Box {
        SehatiCard(Modifier.staggerIn(2).testTag("heart_pillars_card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(done / 5f, size = 84.dp, stroke = 9.dp, color = RiskRed, track = RiskRedBg) {
                    Text("$done/5", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (done == 5) "Luar biasa! Lima pilar tercapai." else "Lima pilar jantung sehat", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Kebiasaan kecil hari ini untuk menjaga jantung.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            s.pillars.forEach { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (p.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (p.done) Wellness else TextMuted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = if (p.done) FontWeight.Medium else FontWeight.Normal)
                        if (!p.done) Text(p.hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
        Confetti(celebrate, Modifier.matchParentSize())
        }

        s.report?.let { r ->
            val st = r.band.style()
            SehatiCard(Modifier.staggerIn(3).testTag("heart_risk_card"), onClick = onOpenRisk) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.MonitorHeart, RiskRed, RiskRedBg, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Faktor risiko jantung koroner", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(if (r.known < 5) "${r.known} dari ${r.total} faktor sudah diketahui" else "${r.present} dari ${r.total} faktor terdeteksi", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
                }
                StatusPill(r.band.label, st.text, st.bg, st.icon)
                Text(r.band.explanation, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        SectionTitle("Tantangan jantung sehat", action = "Semua", onAction = onOpenChallenges)
        val active = s.started.filter { it.status == ChallengeStatus.ACTIVE }
        if (active.isEmpty()) {
            SehatiCard(Modifier.staggerIn(4).testTag("challenge_empty_card"), onClick = onOpenChallenges, container = WellnessLight, border = Wellness.copy(alpha = 0.3f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.EmojiEvents, WellnessDark, Color.White, 44)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Mulai tantanganmu", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Pilih satu kebiasaan, selesaikan 7 hari dalam 2 minggu, dan raih lencana.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        } else active.take(2).forEachIndexed { i, p -> ChallengeCard(p, vm::checkIn, Modifier.staggerIn(4 + i)) }

        FactCard(s, vm::answerFact)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = RiskOrange)
            Spacer(Modifier.width(8.dp))
            Text("Streak ${s.progress.streakDays} hari · ${s.progress.learningPoints} poin · ${s.progress.badges.count { it.earned }} lencana", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onOpenChallenges, Modifier.heightIn(min = 48.dp).testTag("open_challenges")) { Text("Lihat", color = PrimaryDark) }
        }
    }
}

@Composable
private fun FactCard(s: HeartUiState, onAnswer: () -> Unit) {
    var choice by remember(s.fact.statement) { mutableStateOf<Boolean?>(null) }
    val answered = choice != null
    SehatiCard(Modifier.testTag("daily_fact_card"), container = PrimaryLight, border = Primary.copy(alpha = 0.25f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Lightbulb, Color.White, Primary, 36)
            Spacer(Modifier.width(10.dp))
            Text("Fakta atau mitos?", style = MaterialTheme.typography.titleSmall, color = PrimaryDark)
        }
        Text(s.fact.statement, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.testTag("daily_fact_text"))
        if (!answered) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Fakta", { choice = true; onAnswer() }, Modifier.weight(1f), tag = "fact_true_button")
                SecondaryButton("Mitos", { choice = false; onAnswer() }, Modifier.weight(1f), tag = "fact_false_button")
            }
        }
        AnimatedVisibility(answered) {
            val correct = choice == s.fact.isTrue
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (correct) "Benar! Ini ${if (s.fact.isTrue) "fakta" else "mitos"}." else "Belum tepat, ini ${if (s.fact.isTrue) "fakta" else "mitos"}.", style = MaterialTheme.typography.titleSmall, color = if (correct) WellnessDark else RiskOrangeText, modifier = Modifier.testTag("fact_result"))
                Text(s.fact.explanation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (!answered && s.factAnswered) Text("Kamu sudah menjawab hari ini. Kembali lagi besok!", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
internal fun ChallengeCard(p: ChallengeProgress, onCheckIn: (String) -> Unit, modifier: Modifier = Modifier) {
    var burst by remember { mutableStateOf<Long?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(modifier) {
    SehatiCard(Modifier.testTag("challenge_${p.def.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(challengeIcon(p.def.iconKey), WellnessDark, WellnessLight, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.def.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(p.def.tagline, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        BarRow("Hari tercapai", p.done, p.def.target, if (p.status == ChallengeStatus.COMPLETED) Wellness else Primary, valueLabel = "${p.done}/${p.def.target}")
        when (p.status) {
            ChallengeStatus.COMPLETED -> StatusPill("Selesai! Hebat 🎉", RiskGreenText, RiskGreenBg, Icons.Rounded.EmojiEvents)
            ChallengeStatus.EXPIRED -> Text("Waktu habis. Kamu bisa mencoba lagi.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            ChallengeStatus.ACTIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sisa ${p.daysLeft} hari", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
                if (p.def.auto != AutoRule.NONE) Text("Otomatis dari catatanmu", style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                else if (p.checkedToday) StatusPill("Hari ini tercatat", RiskGreenText, RiskGreenBg, Icons.Rounded.CheckCircle)
                else PrimaryButton("Saya berhasil hari ini", {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    burst = System.currentTimeMillis(); onCheckIn(p.def.id)
                }, Modifier.widthIn(min = 180.dp), tag = "checkin_${p.def.id}")
            }
        }
    }
    Confetti(burst, Modifier.matchParentSize())
    }
}

@Composable
fun ChallengesScreen(onBack: () -> Unit, onOpenAcademy: (String) -> Unit, vm: HeartViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("challenges_screen")) {
        ScreenHeader("Tantangan & lencana", "Kebiasaan kecil, jantung lebih sehat", onBack = onBack)
        SectionTitle("Tantanganmu")
        if (s.started.isEmpty()) Text("Belum ada tantangan yang dimulai.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        s.started.forEachIndexed { i, p -> ChallengeCard(p, vm::checkIn, Modifier.staggerIn(i)) }
        SectionTitle("Mulai tantangan baru")
        s.available.forEachIndexed { i, d ->
            SehatiCard(Modifier.staggerIn(i).testTag("available_${d.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(challengeIcon(d.iconKey), PrimaryDark, PrimaryLight, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("${d.tagline} Selesaikan ${d.target} hari dalam ${d.windowDays} hari.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Mulai", { vm.startChallenge(d.id) }, Modifier.weight(1f), tag = "start_${d.id}")
                    d.moduleId?.let { m -> SecondaryButton("Pelajari", { onOpenAcademy(m) }, Modifier.weight(1f), tag = "learn_${d.id}") }
                }
            }
        }
        SectionTitle("Lencana (${s.progress.badges.count { it.earned }}/${s.progress.badges.size})")
        s.progress.badges.forEach { b ->
            SehatiCard(Modifier.testTag("badge_${b.id}").semantics { contentDescription = "${b.title}, ${if (b.earned) "diraih" else "belum diraih"}" }, container = if (b.earned) WellnessLight else SurfaceMuted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(if (b.earned) Wellness else BorderColor, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (b.earned) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(b.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(b.description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun HeartRiskScreen(onBack: () -> Unit, onOpenAcademy: (String) -> Unit, vm: HeartViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("heart_risk_screen")) {
        ScreenHeader("Faktor risiko jantung", "Penyakit jantung koroner", onBack = onBack)
        val r = s.report
        if (r == null) { SkeletonBlock(140.dp); return@ScreenColumn }
        val st = r.band.style()
        SehatiCard(container = st.bg, border = st.text.copy(alpha = 0.3f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(st.icon, null, tint = st.text, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(r.band.label, style = MaterialTheme.typography.titleLarge, color = st.text)
                    Text("${r.present} faktor terdeteksi · ${r.known} dari ${r.total} diketahui", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Text(r.band.explanation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        if (r.ageFactor) InfoNote("Usiamu termasuk kelompok yang risikonya bertambah. Faktor ini tidak dapat diubah, tetapi pemeriksaan rutin membantu.", icon = Icons.Rounded.Cake)
        InfoNote(HeartRisk.DISCLAIMER, icon = Icons.Rounded.Info)
        SectionTitle("Faktor yang dinilai")
        r.factors.forEachIndexed { i, f ->
            val (c, bg, ic) = when (f.status) {
                FactorStatus.PRESENT -> Triple(RiskOrangeText, RiskOrangeBg, Icons.Rounded.Warning)
                FactorStatus.ABSENT -> Triple(RiskGreenText, RiskGreenBg, Icons.Rounded.CheckCircle)
                FactorStatus.UNKNOWN -> Triple(TextSecondary, SurfaceMuted, Icons.Rounded.HelpOutline)
            }
            SehatiCard(Modifier.staggerIn(i).testTag("factor_${f.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    StatusPill(when (f.status) { FactorStatus.PRESENT -> "Perlu perhatian"; FactorStatus.ABSENT -> "Baik"; FactorStatus.UNKNOWN -> "Belum diketahui" }, c, bg, ic)
                }
                Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (f.status != FactorStatus.ABSENT) Text("Langkah: ${f.action}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                f.moduleId?.let { m -> Academy.byId(m)?.let { mod -> TextButton({ onOpenAcademy(m) }, Modifier.heightIn(min = 48.dp)) { Text("Pelajari: ${mod.title}", color = PrimaryDark) } } }
            }
        }
        InfoNote("Bila ada nyeri dada, sesak berat, atau keringat dingin mendadak, segera hubungi ${id.sehati.app.domain.content.HeartKnowledge.EMERGENCY_NUMBERS}.", icon = Icons.Rounded.LocalHospital, color = RiskRedText, bg = RiskRedBg)
        Spacer(Modifier.height(8.dp))
    }
}
