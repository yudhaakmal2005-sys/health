package id.sehati.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
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
import id.sehati.app.domain.model.Sex
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private const val PAGES = 6
private val TITLES = listOf("Selamat datang", "Apa itu SEHATI?", "Privasi & persetujuan", "Identitas dasar", "Tujuan kesehatan", "Buat akun")

@Composable
fun OnboardingScreen(onBack: () -> Unit, onRegistered: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showError by remember { mutableStateOf(false) }
    val back = { if (page == 0) onBack() else { page--; showError = false } }
    BackHandler(onBack = back)

    val pageErr = vm.pageError(page, f)
    WizardLayout(
        step = page, total = PAGES, title = TITLES[page], subtitle = null, onBack = back,
        primaryText = if (page == PAGES - 1) "Buat akun" else "Lanjut",
        onPrimary = { if (pageErr != null) showError = true else if (page == PAGES - 1) vm.register(onRegistered) else { page++; showError = false } },
        primaryLoading = f.loading, primaryTag = "onboarding_next_button",
        error = if (showError) pageErr else f.error,
        modifier = Modifier.testTag("onboarding_screen"),
    ) {
        when (page) {
            0 -> WelcomePage()
            1 -> WhatIsPage()
            2 -> ConsentPage(f, vm)
            3 -> IdentityPage(f, vm)
            4 -> GoalsPage(f, vm)
            else -> AccountPage(f, vm)
        }
    }
}

@Composable
private fun ColumnScope.WelcomePage() {
    Text("Kenali kesehatanmu.\nJaga dari sekarang.", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, modifier = Modifier.staggerIn(0))
    Text("Dalam beberapa langkah singkat kami akan mengenalmu, lalu menyusun gambaran kesehatan dan rencana harian yang sederhana.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.staggerIn(1))
    listOf(
        Triple(Icons.Outlined.Quiz, "Nilai", "Jawab pertanyaan singkat tentang kebiasaan dan riwayatmu."),
        Triple(Icons.Outlined.MonitorHeart, "Pantau", "Catat aktivitas, makanan, dan hasil pemeriksaan."),
        Triple(Icons.Outlined.Groups, "Tindak lanjut", "Hasil Posyandu terhubung ke kader dan Puskesmas."),
    ).forEachIndexed { i, (ic, t, d) ->
        SehatiCard(Modifier.staggerIn(i + 2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(ic, PrimaryDark, PrimaryLight)
                Spacer(Modifier.width(12.dp))
                Column { Text(t, style = MaterialTheme.typography.titleSmall, color = TextPrimary); Text(d, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            }
        }
    }
}

@Composable
private fun ColumnScope.WhatIsPage() {
    Text("Sistem Edukasi & Pemantauan Kesehatan Komunitas", style = MaterialTheme.typography.titleLarge, color = PrimaryDark, modifier = Modifier.staggerIn(0))
    Text("SEHATI membantu mencegah penyakit tidak menular seperti hipertensi dan diabetes lewat pemahaman, pemantauan, dan tindak lanjut.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.staggerIn(1))
    SehatiCard(Modifier.staggerIn(2)) {
        Text("Alur SEHATI", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text("Warga → Kader Posyandu → Puskesmas", style = MaterialTheme.typography.titleMedium, color = PrimaryDark)
        Text("Nilai → Pahami → Pantau → Tindak → Tindak lanjut → Nilai ulang", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
    InfoNote("SEHATI bukan alat diagnosis mandiri dan tidak menggantikan layanan Puskesmas atau dokter. Hasilnya adalah profil pemantauan, bukan diagnosis medis.", Modifier.staggerIn(3), icon = Icons.Outlined.Shield)
}

@Composable
private fun ColumnScope.ConsentPage(f: OnboardingForm, vm: OnboardingViewModel) {
    Text("Kamu berhak tahu apa yang terjadi pada datamu.", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    listOf(
        "Data apa yang dibaca?" to "Yang kamu isi (identitas dasar, kebiasaan, pengukuran) dan, bila kamu izinkan, langkah, tidur, tekanan darah, dan data lain dari Health Connect.",
        "Mengapa dibutuhkan?" to "Untuk menyusun profil pemantauan, rencana harian, dan membantu kader menindaklanjuti hasil pemeriksaan.",
        "Apakah dikirim ke server?" to "Secara bawaan data diproses di perangkatmu. Pengiriman ke server hanya bila kamu mengizinkan di bawah.",
        "Siapa yang dapat melihat?" to "Kamu. Kader Posyandu hanya melihat data saat melayanimu. Admin Puskesmas hanya melihat angka agregat dan daftar tindak lanjut.",
        "Bagaimana menghapusnya?" to "Kapan saja lewat Profil → Hapus data saya.",
    ).forEachIndexed { i, (q, a) ->
        SehatiCard(Modifier.staggerIn(i)) { Text(q, style = MaterialTheme.typography.titleSmall, color = TextPrimary); Text(a, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
    SehatiCard {
        SwitchRow("Simpan data di perangkat (wajib)", "Diperlukan agar SEHATI dapat bekerja, termasuk saat tanpa internet.", f.consentLocal, { v -> vm.update { copy(consentLocal = v) } }, tag = "consent_local_switch")
        SwitchRow("Izinkan sinkronisasi ke server (opsional)", "Data dikirim terenkripsi ke server SEHATI agar Posyandu/Puskesmas dapat menindaklanjuti.", f.consentServer, { v -> vm.update { copy(consentServer = v) } }, tag = "consent_server_switch")
        SwitchRow("Baca data dari Health Connect (opsional)", "Langkah, tidur, tekanan darah, dll. Dapat dicabut kapan saja di pengaturan Health Connect.", f.consentHealthConnect, { v -> vm.update { copy(consentHealthConnect = v) } }, tag = "consent_hc_switch")
    }
}

@Composable
private fun ColumnScope.IdentityPage(f: OnboardingForm, vm: OnboardingViewModel) {
    SehatiTextField(f.name, { v -> vm.update { copy(name = v) } }, "Nama lengkap", tag = "onboarding_name_field")
    FieldLabel("Tanggal lahir")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.day, { v -> vm.update { copy(day = v.filter(Char::isDigit).take(2)) } }, "Tgl", Modifier.weight(1f), KeyboardType.Number, tag = "onboarding_day_field")
        SehatiTextField(f.month, { v -> vm.update { copy(month = v.filter(Char::isDigit).take(2)) } }, "Bln", Modifier.weight(1f), KeyboardType.Number, tag = "onboarding_month_field")
        SehatiTextField(f.year, { v -> vm.update { copy(year = v.filter(Char::isDigit).take(4)) } }, "Tahun", Modifier.weight(1.4f), KeyboardType.Number, tag = "onboarding_year_field")
    }
    FieldLabel("Jenis kelamin")
    ChoiceChips(Sex.entries, f.sex, { v -> vm.update { copy(sex = v) } }, { it.label }, tagPrefix = "sex")
    SehatiTextField(f.village, { v -> vm.update { copy(village = v) } }, "Desa/kelurahan", tag = "onboarding_village_field")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SehatiTextField(f.rw, { v -> vm.update { copy(rw = v.filter(Char::isDigit).take(2)) } }, "RW", Modifier.weight(1f), KeyboardType.Number, tag = "onboarding_rw_field")
        SehatiTextField(f.rt, { v -> vm.update { copy(rt = v.filter(Char::isDigit).take(2)) } }, "RT", Modifier.weight(1f), KeyboardType.Number, tag = "onboarding_rt_field")
    }
    SehatiTextField(f.phone, { v -> vm.update { copy(phone = v.filter { it.isDigit() || it == '+' }.take(15)) } }, "Nomor kontak (opsional)", keyboardType = KeyboardType.Phone, tag = "onboarding_phone_field",
        supporting = "Dipakai untuk masuk dan pengingat; tidak dimasukkan ke QR.")
}

@Composable
private fun ColumnScope.GoalsPage(f: OnboardingForm, vm: OnboardingViewModel) {
    Text("Apa yang ingin kamu capai? Pilih satu atau lebih (opsional).", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    MultiChips(GOAL_OPTIONS, f.goals, { g -> vm.update { copy(goals = if (g in goals) goals - g else goals + g) } }, { it }, tagPrefix = "goal")
}

@Composable
private fun ColumnScope.AccountPage(f: OnboardingForm, vm: OnboardingViewModel) {
    Text("Kamu akan mendapat SEHATI ID otomatis (contoh HM-000127). ID ini bukan pengganti NIK untuk keperluan administratif resmi.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    SehatiTextField(f.password, { v -> vm.update { copy(password = v) } }, "Kata sandi", password = true, tag = "onboarding_password_field", supporting = "Minimal 6 karakter")
    SehatiTextField(f.confirm, { v -> vm.update { copy(confirm = v) } }, "Ulangi kata sandi", password = true, tag = "onboarding_confirm_field")
    InfoNote("Setelah ini kamu akan mengisi asesmen awal (±5 menit) untuk menyusun profil dan rencana kesehatanmu.", icon = Icons.Outlined.Assignment)
}
