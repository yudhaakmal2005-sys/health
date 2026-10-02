package id.sehati.app.ui.kader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private val LABELS = listOf("Pengukuran", "Pencatatan", "Penyuluhan", "Validasi & sinkronisasi")

/** Langkah 2–5 pelayanan Posyandu ILP untuk satu kunjungan. */
@Composable
fun ExaminationTab(kader: KaderViewModel, selectedVisit: String?, onSelect: (String?) -> Unit, vm: VisitViewModel = hiltViewModel()) {
    val ks by kader.state.collectAsStateWithLifecycle()
    if (selectedVisit == null) {
        ScreenColumn(Modifier.testTag("kader_exam_list")) {
            Text("PEMERIKSAAN", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
            if (ks.inProgress.isEmpty()) EmptyState(Icons.Rounded.MonitorHeart, "Belum ada kunjungan berlangsung", "Daftarkan warga dari tab Warga (langkah 1), lalu lanjutkan pemeriksaan di sini.")
            ks.inProgress.forEach { r ->
                SehatiCard(Modifier.testTag("exam_row_${r.visit.userId}"), onClick = { onSelect(r.visit.id) }) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("${r.visit.userId} · terdaftar ${TimeUtils.time(r.visit.registeredAt)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
        return
    }
    LaunchedEffect(selectedVisit) { vm.load(selectedVisit) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_exam_screen")) {
        if (ui.loading) { LoadingState(); return@ScreenColumn }
        val citizen = ui.citizen
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onSelect(null) }, Modifier.heightIn(min = 48.dp).testTag("exam_back_button")) { Icon(Icons.Rounded.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("Daftar") }
        }
        SehatiCard(container = PrimaryLight, border = Primary.copy(alpha = 0.2f)) {
            Text(citizen?.fullName ?: "-", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text("${ui.visit?.userId} · ${citizen?.let { AgeCalc.age(it.birthDate) } ?: 0} th · RW ${citizen?.rw}", style = MaterialTheme.typography.bodyMedium, color = PrimaryDark)
        }
        StepIndicator(ui.step, 4, labels = LABELS)
        ui.error?.let { InfoNote(it, icon = Icons.Rounded.ErrorOutline, color = RiskRedText, bg = RiskRedBg, modifier = Modifier.testTag("visit_error")) }
        when (ui.step) {
            0 -> MeasureStep(vm, ui)
            1 -> ReviewStep(vm, ui)
            2 -> EducationStep(vm, ui)
            else -> ValidateStep(vm, ui, onFinish = { onSelect(null) })
        }
    }
}

@Composable
private fun ColumnScope.MeasureStep(vm: VisitViewModel, ui: VisitUi) {
    val f = ui.form
    Text("LANGKAH 2 · PENGUKURAN", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
    if (ui.history.isNotEmpty()) {
        val last = ui.history.first()
        InfoNote("Pemeriksaan terakhir ${TimeUtils.date(last.measuredAt)}: " + listOfNotNull(last.bloodPressure?.let { "TD ${it.first}/${it.second}" }, last.weightKg?.let { "BB ${it.fmt1()} kg" }, last.glucose?.let { "GDS ${it.toInt()}" }).joinToString(" · "), icon = Icons.Rounded.History)
    }
    FieldLabel("Antropometri")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.weight, { v -> vm.update { copy(weight = dec(v)) } }, "Berat", Modifier.weight(1f), KeyboardType.Decimal, suffix = "kg", tag = "kader_weight")
        SehatiTextField(f.height, { v -> vm.update { copy(height = dec(v)) } }, "Tinggi", Modifier.weight(1f), KeyboardType.Decimal, suffix = "cm", tag = "kader_height")
    }
    SehatiTextField(f.waist, { v -> vm.update { copy(waist = dec(v)) } }, "Lingkar perut", keyboardType = KeyboardType.Decimal, suffix = "cm", tag = "kader_waist")
    val bmi = vm.bmi(f)
    if (bmi > 0f) InfoNote("IMT otomatis: ${bmi.fmt1()} · ${AnthropometryRules.classify(f.height.replace(',', '.').toFloat(), f.weight.replace(',', '.').toFloat()).category}", icon = Icons.Rounded.Calculate)
    FieldLabel("Tanda vital")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.systolic, { v -> vm.update { copy(systolic = v.filter(Char::isDigit).take(3)) } }, "Sistolik", Modifier.weight(1f), KeyboardType.Number, tag = "kader_sys")
        SehatiTextField(f.diastolic, { v -> vm.update { copy(diastolic = v.filter(Char::isDigit).take(3)) } }, "Diastolik", Modifier.weight(1f), KeyboardType.Number, tag = "kader_dia")
    }
    SehatiTextField(f.heartRate, { v -> vm.update { copy(heartRate = v.filter(Char::isDigit).take(3)) } }, "Denyut jantung", keyboardType = KeyboardType.Number, suffix = "x/mnt", tag = "kader_hr")
    FieldLabel("Pemeriksaan sederhana")
    SehatiTextField(f.glucose, { v -> vm.update { copy(glucose = dec(v)) } }, "Gula darah", keyboardType = KeyboardType.Decimal, suffix = "mg/dL", tag = "kader_glucose")
    SwitchRow("Gula darah puasa (GDP)", "Matikan bila sewaktu (GDS).", f.fasting, { v -> vm.update { copy(fasting = v) } }, "kader_fasting")
    SehatiTextField(f.cholesterol, { v -> vm.update { copy(cholesterol = dec(v)) } }, "Kolesterol total", keyboardType = KeyboardType.Decimal, suffix = "mg/dL", tag = "kader_chol")
    SehatiCard {
        Text("Measurement Quality Check", style = MaterialTheme.typography.titleSmall)
        SwitchRow("Manset terpasang benar?", null, f.cuffOk, { v -> vm.update { copy(cuffOk = v) } }, "quality_cuff")
        SwitchRow("Warga istirahat ≥5 menit?", null, f.rested, { v -> vm.update { copy(rested = v) } }, "quality_rested")
        SwitchRow("Perlu pengukuran ulang?", null, f.repeatNeeded, { v -> vm.update { copy(repeatNeeded = v) } }, "quality_repeat")
        if (!f.cuffOk || !f.rested || f.repeatNeeded) InfoNote("Kualitas pengukuran belum memenuhi SOP. Ulangi pengukuran sebelum menyimpan bila memungkinkan.", icon = Icons.Rounded.Warning, color = RiskOrangeText, bg = RiskOrangeBg)
    }
    vm.issues(f).filter { !it.blocking }.forEach { InfoNote(it.message, icon = Icons.Rounded.Warning, color = RiskOrangeText, bg = RiskOrangeBg) }
    PrimaryButton("Tinjau hasil", vm::goReview, tag = "review_measurement_button")
}

private fun dec(v: String) = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)

@Composable
private fun ColumnScope.ReviewStep(vm: VisitViewModel, ui: VisitUi) {
    val i = vm.input(ui.form)
    Text("LANGKAH 3 · PENCATATAN", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
    Text("Tinjau → Konfirmasi → Simpan", style = MaterialTheme.typography.titleMedium)
    SehatiCard {
        i.weightKg?.let { KeyValueRow("Berat", "${it.fmt1()} kg") }
        i.heightCm?.let { KeyValueRow("Tinggi", "${it.fmt1()} cm") }
        vm.bmi(ui.form).takeIf { it > 0 }?.let { KeyValueRow("IMT", it.fmt1()) }
        i.waistCm?.let { KeyValueRow("Lingkar perut", "${it.fmt1()} cm") }
        if (i.systolic != null) KeyValueRow("Tekanan darah", "${i.systolic}/${i.diastolic} mmHg")
        i.heartRate?.let { KeyValueRow("Denyut jantung", "$it x/mnt") }
        i.glucose?.let { KeyValueRow(if (ui.form.fasting) "GDP" else "GDS", "${it.toInt()} mg/dL") }
        i.cholesterol?.let { KeyValueRow("Kolesterol total", "${it.toInt()} mg/dL") }
    }
    val results = listOfNotNull(
        if (i.systolic != null && i.diastolic != null) BloodPressureRules.interpret(i.systolic, i.diastolic) else null,
        i.glucose?.let { GlucoseRules.interpret(it, ui.form.fasting) }, i.cholesterol?.let { LipidRules.interpret(it) },
    )
    if (results.any { it.needsUrgentCare }) EmergencyBanner()
    results.forEach { r ->
        val st = r.severity.style()
        SehatiCard(container = st.bg, border = st.color.copy(alpha = 0.3f)) {
            StatusPill(r.category, st.text, androidx.compose.ui.graphics.Color.White, st.icon)
            Text(r.interpretation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
    InfoNote("Hasil skrining bukan diagnosis. Warga diberi tahu perlunya konfirmasi dan evaluasi tenaga kesehatan.", icon = Icons.Rounded.Shield)
    PrimaryButton("Konfirmasi & simpan", vm::confirmAndSave, loading = ui.working, icon = Icons.Rounded.Save, tag = "save_measurement_button")
    SecondaryButton("Ubah", vm::back, tag = "edit_measurement_button")
}

@Composable
private fun ColumnScope.EducationStep(vm: VisitViewModel, ui: VisitUi) {
    Text("LANGKAH 4 · PENYULUHAN", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
    SehatiCard(container = RiskGreenBg, border = RiskGreen.copy(alpha = 0.3f)) {
        val i = vm.input(ui.form)
        Text("✓ Tersimpan di rekam kesehatan warga", style = MaterialTheme.typography.titleSmall, color = RiskGreenText, modifier = Modifier.testTag("saved_banner"))
        if (i.weightKg != null) Text("✓ Berat badan tercatat", style = MaterialTheme.typography.bodyMedium, color = RiskGreenText)
        if (i.systolic != null) Text("✓ Tekanan darah tercatat", style = MaterialTheme.typography.bodyMedium, color = RiskGreenText)
        if (i.glucose != null) Text("✓ Gula darah tercatat", style = MaterialTheme.typography.bodyMedium, color = RiskGreenText)
        if (i.waistCm != null) Text("✓ Lingkar perut tercatat", style = MaterialTheme.typography.bodyMedium, color = RiskGreenText)
    }
    Text("Materi yang relevan untuk disampaikan kepada warga:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    ui.recommended.forEach { m ->
        SehatiCard {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(m.id in ui.delivered, { vm.toggleModule(m.id) }, Modifier.testTag("deliver_${m.id}"))
                Column {
                    Text(m.title, style = MaterialTheme.typography.titleSmall)
                    Text(m.paragraphs.getOrNull(1) ?: m.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Aksi: ${m.challenge}", style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                }
            }
        }
    }
    InfoNote("Kader tidak memberikan resep obat atau mengubah terapi. Rujuk ke tenaga kesehatan bila perlu.", icon = Icons.Rounded.Shield)
    PrimaryButton("Lanjut ke validasi", vm::saveEducation, tag = "education_next_button")
}

@Composable
private fun ColumnScope.ValidateStep(vm: VisitViewModel, ui: VisitUi, onFinish: () -> Unit) {
    Text("LANGKAH 5 · VALIDASI & SINKRONISASI", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
    val res = ui.result
    if (res == null && ui.visit?.status == "COMPLETED") {
        InfoNote("Kunjungan ini sudah divalidasi pada ${ui.visit.validatedAt?.let { TimeUtils.dateTime(it) } ?: "-"}.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        ui.visit.let { SyncChip(id.sehati.app.domain.model.SyncStatus.parse(it.syncStatus)) }
        PrimaryButton("Kembali ke daftar", onFinish, tag = "visit_done_button")
    } else if (res == null) {
        Text("Review → Konfirmasi kader → Validasi → Simpan → Sinkron", style = MaterialTheme.typography.titleMedium)
        SehatiCard {
            ui.saved?.let { c ->
                c.bloodPressure?.let { KeyValueRow("Tekanan darah", "${it.first}/${it.second} mmHg") }
                c.weightKg?.let { KeyValueRow("Berat", "${it.fmt1()} kg") }
                c.glucose?.let { KeyValueRow("Gula darah", "${it.toInt()} mg/dL") }
                KeyValueRow("Sumber", c.source.label); KeyValueRow("Diverifikasi", c.verification.label)
            }
            KeyValueRow("Materi disampaikan", if (ui.delivered.isEmpty()) "-" else "${ui.delivered.size} materi")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ui.confirmed, vm::setConfirmed, Modifier.testTag("kader_confirm_checkbox"))
            Text("Saya memastikan data di atas sesuai hasil pelayanan.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        PrimaryButton("Validasi & selesaikan kunjungan", vm::validateAndSync, loading = ui.working, icon = Icons.Rounded.TaskAlt, tag = "validate_visit_button")
    } else {
        Column(Modifier.fillMaxWidth().testTag("visit_result"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedCheck()
            Text("Kunjungan selesai", style = MaterialTheme.typography.headlineSmall)
        }
        SehatiCard {
            Text("✓ Tersimpan di perangkat", style = MaterialTheme.typography.titleSmall, color = RiskGreenText)
            when (ui.syncState) {
                SyncState.SYNCED -> Text("✓ Tersinkron", style = MaterialTheme.typography.titleSmall, color = RiskGreenText, modifier = Modifier.testTag("sync_status_synced"))
                else -> Text("⚠ Menunggu internet / sinkronisasi", style = MaterialTheme.typography.titleSmall, color = RiskYellowText, modifier = Modifier.testTag("sync_status_waiting"))
            }
            ui.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        if (res.followUps.isNotEmpty()) {
            Text("Tindak lanjut dibuat", style = MaterialTheme.typography.titleSmall)
            res.followUps.forEach { f -> InfoNote(f.reason, icon = Icons.Rounded.EventAvailable, color = RiskOrangeText, bg = RiskOrangeBg, modifier = Modifier.testTag("followup_created")) }
        } else InfoNote("Tidak ada tindak lanjut baru dari hasil ini.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        RiskCard(res.profile.level, compact = true)
        PrimaryButton("Kembali ke daftar", onFinish, tag = "visit_done_button")
    }
}
