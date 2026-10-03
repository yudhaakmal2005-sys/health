package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.repository.HealthCheck
import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.FollowUpType
import id.sehati.app.domain.model.SyncStatus
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(onShowQr: () -> Unit, onOpenAcademy: (String?) -> Unit, onRetakeAssessment: () -> Unit, vm: HealthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    if (s.loading) { ScreenColumn { SkeletonBlock(120.dp); SkeletonBlock(200.dp) }; return }

    ScreenColumn(Modifier.testTag("health_screen")) {
        ScreenHeader("Kesehatan", "Profil, riwayat pemeriksaan, dan tindak lanjut")
        if (s.findings.any { it.id == "symptom_red_flag" }) EmergencyBanner()
        RiskCard(s.level, Modifier.staggerIn(0))

        PrimaryButton("Tunjukkan QR ke kader", onShowQr, icon = Icons.Outlined.QrCode2, tag = "show_qr_button")

        if (s.findings.isNotEmpty()) {
            SectionTitle("Yang perlu diperhatikan")
            s.findings.take(6).forEachIndexed { i, fd ->
                val st = runCatching { Severity.valueOf(fd.severity) }.getOrDefault(Severity.WATCH).style()
                SehatiCard(Modifier.staggerIn(i + 1), contentPadding = 12) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(st.icon, st.text, st.bg, 32)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(fd.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary); Text(fd.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                    }
                }
            }
            TextButton({ onOpenAcademy(null) }, Modifier.heightIn(min = 48.dp)) { Text("Pelajari di Health Academy") }
        }

        if (s.followUps.isNotEmpty()) {
            SectionTitle("Tindak lanjut")
            s.followUps.forEach { f ->
                val open = f.status != FollowUpStatus.DONE.name && f.status != FollowUpStatus.CANCELLED.name
                SehatiCard(Modifier.testTag("followup_${f.id}"), container = if (open) RiskOrangeBg else CardWhite, border = if (open) RiskOrange.copy(alpha = 0.3f) else BorderColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Outlined.EventAvailable, if (open) RiskOrangeText else RiskGreenText, if (open) Color2(RiskOrange) else RiskGreenBg, 36)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(FollowUpType.parse(f.type).label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(f.reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("${FollowUpStatus.parse(f.status).label} · target ${TimeUtils.date(f.dueAt)}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        }
                    }
                }
            }
            s.referrals.firstOrNull()?.let { InfoNote("Permintaan rujukan tercatat (${it.status.lowercase()}). Rujukan membantu proses administrasi dan bukan diagnosis.", icon = Icons.Outlined.LocalHospital) }
        }

        // Tren tekanan darah
        val bps = s.checks.filter { it.bloodPressure != null }.take(8).reversed()
        if (bps.size >= 2) {
            SectionTitle("Tren tekanan darah")
            SehatiCard(Modifier.staggerIn(2)) {
                TrendChart(
                    series = listOf("Sistolik" to bps.map { it.systolic!!.toFloat() }, "Diastolik" to bps.map { it.diastolic!!.toFloat() }),
                    colors = listOf(Primary, Wellness), labels = bps.map { TimeUtils.shortDate(it.measuredAt) }, threshold = 140f,
                    description = "Grafik tren tekanan darah. Garis putus-putus adalah batas pemantauan 140 mmHg.",
                )
                Text("Garis putus-putus: batas sistolik untuk pemantauan lebih lanjut (konfigurasi internal).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        SectionTitle("Riwayat pemeriksaan", action = "Catat sendiri", onAction = { adding = true })
        if (s.checks.isEmpty()) EmptyState(Icons.Outlined.MonitorHeart, "Belum ada pemeriksaan", "Catat hasil pengukuran sendiri atau datang ke Posyandu.", action = "Catat pengukuran", onAction = { adding = true })
        s.checks.forEachIndexed { i, c -> CheckRow(c, Modifier.staggerIn(i)) }

        SecondaryButton("Ulangi asesmen", onRetakeAssessment, icon = Icons.Outlined.Refresh, tag = "retake_assessment_button")
        FloatingNote()
        Spacer(Modifier.height(8.dp))
    }

    if (adding || saved != null) {
        ModalBottomSheet(onDismissRequest = { adding = false; vm.clearSaved() }, containerColor = CardWhite, modifier = Modifier.testTag("add_measurement_sheet")) {
            if (saved != null) SavedResult(saved!!) { adding = false; vm.clearSaved() } else MeasurementForm(vm)
        }
    }
}

private fun Color2(c: androidx.compose.ui.graphics.Color) = c.copy(alpha = 0.15f)

@Composable
private fun FloatingNote() = InfoNote(PROFILE_NOTE, icon = Icons.Outlined.Shield)
private const val PROFILE_NOTE = "Profil dan hasil di sini adalah pemantauan berbasis data yang dimasukkan, bukan diagnosis medis."

@Composable
fun CheckRow(c: HealthCheck, modifier: Modifier = Modifier) {
    SehatiCard(modifier.testTag("check_${c.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TimeUtils.dateTime(c.measuredAt), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.weight(1f))
            SyncChip(SyncStatus.parse(c.syncStatus))
        }
        c.bloodPressure?.let { (sy, di) ->
            val r = BloodPressureRules.interpret(sy, di); val st = r.severity.style()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BP $sy/$di", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                StatusPill(r.category, st.text, st.bg, st.icon)
            }
        }
        c.glucose?.let { g -> val r = GlucoseRules.interpret(g, c.glucoseFasting); val st = r.severity.style()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${if (c.glucoseFasting) "GDP" else "GDS"} ${g.toInt()} mg/dL", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                StatusPill(r.category, st.text, st.bg, st.icon)
            } }
        c.cholesterol?.let { Text("Kolesterol total ${it.toInt()} mg/dL", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        val body = listOfNotNull(c.weightKg?.let { "Berat ${it.fmt1()} kg" }, c.waistCm?.let { "Perut ${it.fmt1()} cm" }, c.bmi?.let { "IMT ${it.fmt1()}" }, c.heartRate?.let { "Nadi $it" }).joinToString(" · ")
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        // Data provenance: dari mana data ini berasal
        Text("Sumber: ${c.source.label}" + (c.operatorId?.let { " · petugas $it" } ?: "") + " · ${c.verification.label}", style = MaterialTheme.typography.bodySmall, color = PrimaryDark)
        if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun MeasurementForm(vm: HealthViewModel) {
    val f by vm.form.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Catat pengukuran sendiri", style = MaterialTheme.typography.titleLarge)
        Text("Isi yang kamu ukur saja. Hasil mandiri ditandai belum diverifikasi.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(f.systolic, { v -> vm.update { copy(systolic = v.filter(Char::isDigit).take(3)) } }, "Sistolik", Modifier.weight(1f), KeyboardType.Number, tag = "self_sys")
            SehatiTextField(f.diastolic, { v -> vm.update { copy(diastolic = v.filter(Char::isDigit).take(3)) } }, "Diastolik", Modifier.weight(1f), KeyboardType.Number, tag = "self_dia")
        }
        SehatiTextField(f.heartRate, { v -> vm.update { copy(heartRate = v.filter(Char::isDigit).take(3)) } }, "Denyut jantung", keyboardType = KeyboardType.Number, suffix = "x/mnt", tag = "self_hr")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(f.weight, { v -> vm.update { copy(weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Berat", Modifier.weight(1f), KeyboardType.Decimal, suffix = "kg", tag = "self_weight")
            SehatiTextField(f.waist, { v -> vm.update { copy(waist = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Lingkar perut", Modifier.weight(1f), KeyboardType.Decimal, suffix = "cm", tag = "self_waist")
        }
        SehatiTextField(f.glucose, { v -> vm.update { copy(glucose = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Gula darah", keyboardType = KeyboardType.Decimal, suffix = "mg/dL", tag = "self_glucose")
        SwitchRow("Pengukuran gula darah puasa", null, f.fasting, { v -> vm.update { copy(fasting = v) } }, "self_fasting")
        SehatiTextField(f.notes, { v -> vm.update { copy(notes = v) } }, "Catatan (opsional)", tag = "self_notes")
        f.error?.let { Text(it, color = RiskRedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("measurement_error")) }
        PrimaryButton("Simpan", vm::save, loading = f.saving, tag = "save_measurement_button")
    }
}

@Composable
private fun SavedResult(c: HealthCheck, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedCheck()
        Text("Tersimpan di perangkat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("measurement_saved_title"))
        val results = listOfNotNull(
            c.bloodPressure?.let { BloodPressureRules.interpret(it.first, it.second) },
            c.glucose?.let { GlucoseRules.interpret(it, c.glucoseFasting) },
        )
        if (results.any { it.needsUrgentCare }) EmergencyBanner()
        results.forEach { r ->
            val st = r.severity.style()
            SehatiCard(container = st.bg, border = st.color.copy(alpha = 0.3f)) {
                StatusPill(r.category, st.text, Color2White(), st.icon)
                Text(r.interpretation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("Edukasi: ${r.education}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("Langkah berikutnya: ${r.nextStep}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        PrimaryButton("Selesai", onDone, tag = "measurement_done_button")
    }
}

private fun Color2White() = androidx.compose.ui.graphics.Color.White
