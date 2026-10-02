package id.sehati.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.Severity
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private val TITLES = listOf(
    "Tubuhmu" to "Berat, tinggi, dan lingkar perut", "Riwayat kesehatan" to "Kondisi yang pernah didiagnosis dokter",
    "Riwayat keluarga" to "Orang tua atau saudara kandung", "Merokok" to "Tanpa penilaian, kami ingin membantu",
    "Pola makan" to "Kebiasaan satu minggu terakhir", "Aktivitas fisik" to "Gerak dan duduk lama",
    "Tidur & stres" to "Istirahat dan perasaanmu", "Pengukuran" to "Opsional, isi bila ada hasil pemeriksaan",
    "Profil SEHATI" to "Ringkasan dari data yang kamu isi", "Rencana pribadi" to "Langkah kecil untuk mulai hari ini",
)
private const val RESULT = 8
private const val PLAN = 9

/** Asesmen awal bertahap → Profil SEHATI → Rencana pribadi. */
@Composable
fun AssessmentFlow(onFinished: () -> Unit, vm: AssessmentViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { if (saved) step = RESULT }
    BackHandler(enabled = step in 1..7) { step--; showError = false }

    val err = if (step <= 7) vm.stepError(step, f) else null
    val isLastInput = step == 7
    WizardLayout(
        step = step, total = TITLES.size, title = TITLES[step].first, subtitle = TITLES[step].second,
        onBack = if (step in 1..7) ({ step--; showError = false }) else null,
        primaryText = when (step) { 7 -> "Lihat profil saya"; RESULT -> "Lihat rencana"; PLAN -> "Mulai gunakan SEHATI"; else -> "Lanjut" },
        onPrimary = {
            when {
                step == PLAN -> onFinished()
                step == RESULT -> step = PLAN
                err != null -> showError = true
                isLastInput -> vm.submit()
                else -> { step++; showError = false }
            }
        },
        primaryLoading = f.saving, primaryTag = "submit_assessment_button",
        error = if (showError) err else f.error,
        modifier = Modifier.testTag("assessment_screen"),
    ) {
        when (step) {
            0 -> BodyStep(f, vm)
            1 -> HistoryStep(f, vm)
            2 -> FamilyStep(f, vm)
            3 -> SmokingStep(f, vm)
            4 -> DietStep(f, vm)
            5 -> ActivityStep(f, vm)
            6 -> SleepStep(f, vm)
            7 -> MeasureStep(f, vm)
            RESULT -> ResultStep(vm, f.redFlag)
            else -> PlanStep(vm)
        }
    }
}

@Composable
private fun ColumnScope.BodyStep(f: AssessmentForm, vm: AssessmentViewModel) {
    SehatiTextField(f.heightCm, { v -> vm.update { copy(heightCm = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Tinggi badan", keyboardType = KeyboardType.Decimal, suffix = "cm", tag = "assess_height_field")
    SehatiTextField(f.weightKg, { v -> vm.update { copy(weightKg = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Berat badan", keyboardType = KeyboardType.Decimal, suffix = "kg", tag = "assess_weight_field")
    SehatiTextField(f.waistCm, { v -> vm.update { copy(waistCm = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Lingkar perut (opsional)", keyboardType = KeyboardType.Decimal, suffix = "cm", tag = "assess_waist_field",
        supporting = "Ukur sejajar pusar, saat napas biasa.")
    vm.bmiText(f)?.let { InfoNote(it, icon = Icons.Rounded.Calculate) }
}

@Composable
private fun ColumnScope.HistoryStep(f: AssessmentForm, vm: AssessmentViewModel) {
    Text("Pilih yang pernah dikatakan dokter/tenaga kesehatan (boleh lebih dari satu). Kosongkan bila tidak ada.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    MultiChips(CONDITIONS, f.conditions, { c -> vm.update { copy(conditions = if (c in conditions) conditions - c else conditions + c) } }, { it }, tagPrefix = "cond")
    SehatiTextField(f.otherConditions, { v -> vm.update { copy(otherConditions = v) } }, "Kondisi lain (opsional)", tag = "assess_other_conditions")
}

@Composable
private fun ColumnScope.FamilyStep(f: AssessmentForm, vm: AssessmentViewModel) {
    Text("Apakah orang tua atau saudara kandungmu memiliki riwayat berikut? Kosongkan bila tidak ada atau tidak tahu.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    MultiChips(FAMILY, f.family, { c -> vm.update { copy(family = if (c in family) family - c else family + c) } }, { it }, tagPrefix = "fam")
}

@Composable
private fun ColumnScope.SmokingStep(f: AssessmentForm, vm: AssessmentViewModel) {
    ChoiceChips(SMOKING, SMOKING.firstOrNull { it.first == f.smoking }, { s -> vm.update { copy(smoking = s.first) } }, { it.second }, tagPrefix = "smoke")
    if (f.smoking == "CURRENT") {
        FieldLabel("Jenis produk")
        ChoiceChips(PRODUCTS, f.product, { p -> vm.update { copy(product = p) } }, { it }, tagPrefix = "product")
        NumberStepper("Batang per hari", f.cigarettes, { v -> vm.update { copy(cigarettes = v) } }, 1..60, tag = "assess_cigs")
        InfoNote("Mengurangi dan berhenti merokok membantu menurunkan risiko penyakit kardiovaskular. Kami akan membantumu bertahap.", icon = Icons.Rounded.Favorite)
    }
}

@Composable
private fun ColumnScope.DietStep(f: AssessmentForm, vm: AssessmentViewModel) {
    NumberStepper("Makan sayur (hari/minggu)", f.vegetableDays, { v -> vm.update { copy(vegetableDays = v) } }, 0..7, tag = "assess_veg")
    NumberStepper("Makan buah (hari/minggu)", f.fruitDays, { v -> vm.update { copy(fruitDays = v) } }, 0..7, tag = "assess_fruit")
    SehatiCard {
        SwitchRow("Sering makanan asin/tinggi garam", "Mi instan, ikan asin, kerupuk, makanan kemasan", f.salty, { v -> vm.update { copy(salty = v) } }, "assess_salty")
        SwitchRow("Sering makanan/minuman manis", "Teh manis, minuman kemasan, kue", f.sugary, { v -> vm.update { copy(sugary = v) } }, "assess_sugary")
        SwitchRow("Sering gorengan/makanan berlemak", "Gorengan, santan kental, jeroan", f.fatty, { v -> vm.update { copy(fatty = v) } }, "assess_fatty")
    }
}

@Composable
private fun ColumnScope.ActivityStep(f: AssessmentForm, vm: AssessmentViewModel) {
    NumberStepper("Hari aktif per minggu", f.activeDays, { v -> vm.update { copy(activeDays = v) } }, 0..7, tag = "assess_days")
    NumberStepper("Durasi per sesi", f.activeMinutes, { v -> vm.update { copy(activeMinutes = v) } }, 0..180, unit = " mnt", step = 5, tag = "assess_minutes")
    FieldLabel("Intensitas")
    ChoiceChips(INTENSITY, f.intensity, { v -> vm.update { copy(intensity = v) } }, { it }, tagPrefix = "intensity")
    NumberStepper("Duduk per hari", f.sedentaryHours, { v -> vm.update { copy(sedentaryHours = v) } }, 0..16, unit = " jam", tag = "assess_sedentary")
}

@Composable
private fun ColumnScope.SleepStep(f: AssessmentForm, vm: AssessmentViewModel) {
    NumberStepper("Durasi tidur", f.sleepHours, { v -> vm.update { copy(sleepHours = v) } }, 3..12, unit = " jam", tag = "assess_sleep")
    FieldLabel("Kualitas tidur")
    ChoiceChips(SLEEP_Q, f.sleepQuality, { v -> vm.update { copy(sleepQuality = v) } }, { it }, tagPrefix = "sleepq")
    FieldLabel("Tingkat stres (laporan sendiri)")
    Slider(value = f.stress.toFloat(), onValueChange = { v -> vm.update { copy(stress = v.toInt()) } }, valueRange = 0f..4f, steps = 3, modifier = Modifier.testTag("assess_stress"))
    Text("${listOf("Sangat rendah", "Rendah", "Sedang", "Tinggi", "Sangat tinggi")[f.stress]}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

@Composable
private fun ColumnScope.MeasureStep(f: AssessmentForm, vm: AssessmentViewModel) {
    Text("Lewati bagian ini bila belum punya hasil. Satu hasil pengukuran adalah data pemantauan, bukan diagnosis.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.systolic, { v -> vm.update { copy(systolic = v.filter(Char::isDigit).take(3)) } }, "Sistolik", Modifier.weight(1f), KeyboardType.Number, tag = "assess_sys")
        SehatiTextField(f.diastolic, { v -> vm.update { copy(diastolic = v.filter(Char::isDigit).take(3)) } }, "Diastolik", Modifier.weight(1f), KeyboardType.Number, tag = "assess_dia")
    }
    SehatiTextField(f.heartRate, { v -> vm.update { copy(heartRate = v.filter(Char::isDigit).take(3)) } }, "Denyut jantung (opsional)", keyboardType = KeyboardType.Number, suffix = "x/mnt", tag = "assess_hr")
    SehatiTextField(f.glucose, { v -> vm.update { copy(glucose = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Gula darah sewaktu (opsional)", keyboardType = KeyboardType.Decimal, suffix = "mg/dL", tag = "assess_glucose")
    SehatiTextField(f.cholesterol, { v -> vm.update { copy(cholesterol = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) } }, "Kolesterol total (opsional)", keyboardType = KeyboardType.Decimal, suffix = "mg/dL", tag = "assess_chol")
    SehatiCard(container = RiskRedBg, border = RiskRed.copy(alpha = 0.3f)) {
        SwitchRow("Saat ini ada nyeri/tekanan dada, sesak berat, pingsan, atau gejala akut lain", null, f.redFlag, { v -> vm.update { copy(redFlag = v) } }, "assess_redflag")
    }
    if (f.redFlag) EmergencyBanner()
}

@Composable
private fun ColumnScope.ResultStep(vm: AssessmentViewModel, redFlag: Boolean) {
    val level by vm.level.collectAsStateWithLifecycle()
    val findings by vm.findings.collectAsStateWithLifecycle()
    if (level == null) { LoadingState(label = "Menyusun profilmu…"); return }
    if (redFlag || level == RiskLevel.MEDICAL_FOLLOW_UP && findings.any { it.id == "symptom_red_flag" }) EmergencyBanner()
    RiskCard(level!!, Modifier.staggerIn(0))
    SectionTitle("Yang kami temukan")
    if (findings.isEmpty()) InfoNote("Tidak ada indikator yang perlu ditindaklanjuti dari data yang tersedia. Pertahankan kebiasaan sehatmu.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
    findings.forEachIndexed { i, fd ->
        val st = runCatching { Severity.valueOf(fd.severity) }.getOrDefault(Severity.WATCH).style()
        SehatiCard(Modifier.staggerIn(i + 1)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(st.icon, st.text, st.bg, 36)
                Spacer(Modifier.width(10.dp))
                Column { Text(fd.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary); Text(fd.detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlanStep(vm: AssessmentViewModel) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    Text("Mulai dari yang kecil. Rencana ini akan muncul di Beranda setiap hari.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    plan.forEachIndexed { i, p ->
        SehatiCard(Modifier.staggerIn(i)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Flag, PrimaryDark, PrimaryLight, 36)
                Spacer(Modifier.width(10.dp))
                Text(p.title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
        }
    }
    InfoNote("Rencana ini saran gaya hidup umum, bukan resep medis. Tanyakan tenaga kesehatan untuk hal yang menyangkut pengobatan.", icon = Icons.Rounded.Shield)
}
