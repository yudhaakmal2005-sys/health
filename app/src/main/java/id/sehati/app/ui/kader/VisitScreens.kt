package id.sehati.app.ui.kader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val LABELS = listOf("Pendaftaran", "Pengukuran", "Pencatatan", "Penyuluhan", "Validasi & sinkronisasi")

/** Langkah 2–5 pelayanan Posyandu ILP untuk satu kunjungan. */
@Composable
fun ExaminationTab(kader: KaderViewModel, selectedVisit: String?, onSelect: (String?) -> Unit, vm: VisitViewModel = hiltViewModel()) {
    val ks by kader.state.collectAsStateWithLifecycle()
    if (selectedVisit == null) {
        ScreenColumn(Modifier.testTag("kader_exam_list")) {
            OverlineLabel("PEMERIKSAAN")
            if (ks.inProgress.isEmpty()) EmptyState(Icons.Outlined.MonitorHeart, "Belum ada kunjungan berlangsung", "Daftarkan warga dari tab Warga (langkah 1), lalu lanjutkan pemeriksaan di sini.")
            if (ks.inProgress.isNotEmpty()) SehatiCard(contentPadding = 0) {
                Column {
                    ks.inProgress.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(r.visit.id) }.testTag("exam_row_${r.visit.userId}")
                                .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("${r.visit.userId} · terdaftar ${TimeUtils.time(r.visit.registeredAt)}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted)
                        }
                    }
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
            TextButton({ onSelect(null) }, Modifier.heightIn(min = 48.dp).testTag("exam_back_button")) { Icon(Icons.Outlined.ArrowBack, null, tint = TextSecondary); Spacer(Modifier.width(4.dp)); Text("Daftar", color = TextSecondary) }
        }
        SehatiCard {
            Text(citizen?.fullName ?: "-", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("${ui.visit?.userId} · ${citizen?.let { AgeCalc.age(it.birthDate) } ?: 0} th · RW ${citizen?.rw}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = TextMuted)
        }
        StepIndicator(ui.step + 1, 5, labels = LABELS)
        ui.error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg, modifier = Modifier.testTag("visit_error")) }
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
    OverlineLabel("LANGKAH 2 · PENGUKURAN")
    if (ui.history.isNotEmpty()) {
        val last = ui.history.first()
        InfoNote("Pemeriksaan terakhir ${TimeUtils.date(last.measuredAt)}: " + listOfNotNull(last.bloodPressure?.let { "TD ${it.first}/${it.second}" }, last.weightKg?.let { "BB ${it.fmt1()} kg" }, last.glucose?.let { "GDS ${it.toInt()}" }).joinToString(" · "), icon = Icons.Outlined.History, color = TextSecondary, bg = SurfaceMuted)
    }
    FieldLabel("Antropometri")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.weight, { v -> vm.update { copy(weight = dec(v)) } }, "Berat", Modifier.weight(1f), KeyboardType.Decimal, suffix = "kg", tag = "kader_weight")
        SehatiTextField(f.height, { v -> vm.update { copy(height = dec(v)) } }, "Tinggi", Modifier.weight(1f), KeyboardType.Decimal, suffix = "cm", tag = "kader_height")
    }
    SehatiTextField(f.waist, { v -> vm.update { copy(waist = dec(v)) } }, "Lingkar perut", keyboardType = KeyboardType.Decimal, suffix = "cm", tag = "kader_waist")
    val bmi = vm.bmi(f)
    if (bmi > 0f) InfoNote("IMT otomatis: ${bmi.fmt1()} · ${AnthropometryRules.classify(f.height.replace(',', '.').toFloat(), f.weight.replace(',', '.').toFloat()).category}", icon = Icons.Outlined.Calculate, color = TextSecondary, bg = SurfaceMuted)
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
        Text("Measurement Quality Check", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        SwitchRow("Manset terpasang benar?", null, f.cuffOk, { v -> vm.update { copy(cuffOk = v) } }, "quality_cuff")
        SwitchRow("Warga istirahat ≥5 menit?", null, f.rested, { v -> vm.update { copy(rested = v) } }, "quality_rested")
        SwitchRow("Perlu pengukuran ulang?", null, f.repeatNeeded, { v -> vm.update { copy(repeatNeeded = v) } }, "quality_repeat")
        if (!f.cuffOk || !f.rested || f.repeatNeeded) InfoNote("Kualitas pengukuran belum memenuhi SOP. Ulangi pengukuran sebelum menyimpan bila memungkinkan.", icon = Icons.Outlined.Warning, color = RiskOrangeText, bg = RiskOrangeBg)
    }
    vm.issues(f).filter { !it.blocking }.forEach { InfoNote(it.message, icon = Icons.Outlined.Warning, color = RiskOrangeText, bg = RiskOrangeBg) }
    PrimaryButton("Tinjau hasil", vm::goReview, tag = "review_measurement_button")
}

private fun dec(v: String) = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)

@Composable
private fun ColumnScope.ReviewStep(vm: VisitViewModel, ui: VisitUi) {
    val i = vm.input(ui.form)
    OverlineLabel("LANGKAH 3 · PENCATATAN")
    Text("Tinjau → Konfirmasi → Simpan", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
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
    if (results.isNotEmpty()) SehatiCard(contentPadding = 0) {
        Column {
            results.forEachIndexed { idx, r ->
                val st = r.severity.style()
                if (idx > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 2.dp).size(width = 3.dp, height = 36.dp).clip(RoundedCornerShape(2.dp)).background(st.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(r.category, st.text, st.bg, st.icon)
                        Text(r.interpretation, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }
        }
    }
    InfoNote("Hasil skrining bukan diagnosis. Warga diberi tahu perlunya konfirmasi dan evaluasi tenaga kesehatan.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    PrimaryButton("Konfirmasi & simpan", vm::confirmAndSave, loading = ui.working, icon = Icons.Outlined.Save, tag = "save_measurement_button")
    SecondaryButton("Ubah", vm::back, tag = "edit_measurement_button")
}

@Composable
private fun ColumnScope.EducationStep(vm: VisitViewModel, ui: VisitUi) {
    OverlineLabel("LANGKAH 4 · PENYULUHAN")
    SehatiCard {
        val i = vm.input(ui.form)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tersimpan di rekam kesehatan warga", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f).testTag("saved_banner"))
            StatusPill("Tersimpan", RiskGreenText, RiskGreenBg, Icons.Outlined.Check)
        }
        if (i.weightKg != null) CheckLine("Berat badan tercatat")
        if (i.systolic != null) CheckLine("Tekanan darah tercatat")
        if (i.glucose != null) CheckLine("Gula darah tercatat")
        if (i.waistCm != null) CheckLine("Lingkar perut tercatat")
    }
    Text("Materi yang relevan untuk disampaikan kepada warga:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    if (ui.recommended.isNotEmpty()) SehatiCard(contentPadding = 0) {
        Column {
            ui.recommended.forEachIndexed { idx, m ->
                if (idx > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(m.id in ui.delivered, { vm.toggleModule(m.id) }, Modifier.testTag("deliver_${m.id}"), colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = TextMuted))
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(m.paragraphs.getOrNull(1) ?: m.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Aksi: ${m.challenge}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
            }
        }
    }
    InfoNote("Kader tidak memberikan resep obat atau mengubah terapi. Rujuk ke tenaga kesehatan bila perlu.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    PrimaryButton("Lanjut ke validasi", vm::saveEducation, tag = "education_next_button")
}

@Composable
private fun ColumnScope.ValidateStep(vm: VisitViewModel, ui: VisitUi, onFinish: () -> Unit) {
    OverlineLabel("LANGKAH 5 · VALIDASI & SINKRONISASI")
    val res = ui.result
    if (res == null && ui.visit?.status == "COMPLETED") {
        InfoNote("Kunjungan ini sudah divalidasi pada ${ui.visit.validatedAt?.let { TimeUtils.dateTime(it) } ?: "-"}.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        ui.visit.let { SyncChip(id.sehati.app.domain.model.SyncStatus.parse(it.syncStatus)) }
        PrimaryButton("Kembali ke daftar", onFinish, tag = "visit_done_button")
    } else if (res == null) {
        Text("Review → Konfirmasi kader → Validasi → Simpan → Sinkron", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
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
            Checkbox(ui.confirmed, vm::setConfirmed, Modifier.testTag("kader_confirm_checkbox"), colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = TextMuted))
            Text("Saya memastikan data di atas sesuai hasil pelayanan.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        PrimaryButton("Validasi & selesaikan kunjungan", vm::validateAndSync, loading = ui.working, icon = Icons.Outlined.TaskAlt, tag = "validate_visit_button")
    } else {
        Column(Modifier.fillMaxWidth().testTag("visit_result"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedCheck(size = 56.dp)
            Text("Kunjungan selesai", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        SehatiCard {
            CheckLine("Tersimpan di perangkat")
            when (ui.syncState) {
                SyncState.SYNCED -> StatusPill("Tersinkron", RiskGreenText, RiskGreenBg, Icons.Outlined.CloudDone, Modifier.testTag("sync_status_synced"))
                else -> StatusPill("Menunggu internet / sinkronisasi", RiskYellowText, RiskYellowBg, Icons.Outlined.CloudOff, Modifier.testTag("sync_status_waiting"))
            }
            ui.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        if (res.followUps.isNotEmpty()) {
            Text("Tindak lanjut dibuat", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            res.followUps.forEach { f -> InfoNote(f.reason, icon = Icons.Outlined.EventAvailable, color = RiskOrangeText, bg = RiskOrangeBg, modifier = Modifier.testTag("followup_created")) }
        } else InfoNote("Tidak ada tindak lanjut baru dari hasil ini.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        RiskCard(res.profile.level, compact = true)
        PrimaryButton("Kembali ke daftar", onFinish, tag = "visit_done_button")
    }
}

/** Baris konfirmasi kecil: ikon centang garis + teks netral. */
@Composable
private fun CheckLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, null, tint = RiskGreenText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
