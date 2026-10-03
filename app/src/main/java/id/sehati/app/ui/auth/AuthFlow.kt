package id.sehati.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sehati.app.ui.citizen.AgendaRow
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.BuildConfig
import id.sehati.app.domain.model.Role
import id.sehati.app.ui.components.*
import id.sehati.app.ui.onboarding.OnboardingScreen
import id.sehati.app.ui.theme.*

private enum class AuthPage { Welcome, Onboarding, Login, Activate }

@Composable
fun AuthFlow(onAssessmentNeeded: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(AuthPage.Welcome) }
    BackHandler(enabled = page != AuthPage.Welcome) { page = AuthPage.Welcome }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 6 * dir } + fadeIn(tween(Motion.Medium))) togetherWith
                (slideOutHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { -it / 6 * dir } + fadeOut(tween(Motion.Short)))
        },
        label = "auth",
    ) { p ->
        when (p) {
            AuthPage.Welcome -> WelcomeScreen(onStart = { page = AuthPage.Onboarding }, onLogin = { page = AuthPage.Login })
            AuthPage.Onboarding -> OnboardingScreen(onBack = { page = AuthPage.Welcome }, onRegistered = onAssessmentNeeded)
            AuthPage.Login -> LoginScreen(onBack = { page = AuthPage.Welcome }, onRegister = { page = AuthPage.Onboarding }, onActivate = { page = AuthPage.Activate })
            AuthPage.Activate -> ActivateScreen(onBack = { page = AuthPage.Login })
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Background)) {
        ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().testTag("welcome_screen"), scroll = true) {
            Spacer(Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.staggerIn(0)) {
                HeartLogo(40.dp)
                Spacer(Modifier.width(12.dp))
                Text("SEHATI", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Text("Kenali kesehatanmu.\nJaga jantungmu dari sekarang.", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().staggerIn(1))
            Text("Teman pemantauan kesehatan pribadi sekaligus penghubung ke Posyandu dan Puskesmas di desamu.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.fillMaxWidth().staggerIn(2))
            SehatiCard(Modifier.staggerIn(3), contentPadding = 0) {
                Column {
                    listOf(
                        Triple(Icons.Outlined.FavoriteBorder, "Jantung sehat", "Pantau tekanan darah, gula, dan kebiasaan harian"),
                        Triple(Icons.Outlined.WifiOff, "Tanpa internet", "Data tersimpan di perangkat, sinkron saat online"),
                        Triple(Icons.Outlined.Lock, "Data aman", "Hanya kamu dan petugas berwenang yang dapat melihat"),
                    ).forEachIndexed { i, (ic, t, sub) ->
                        if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                        AgendaRow(ic, t, sub, null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.staggerIn(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Mulai", onStart, tag = "welcome_start_button")
                SecondaryButton("Sudah punya akun? Masuk", onLogin, tag = "welcome_login_button")
            }
            Text("SEHATI bukan alat diagnosis dan tidak menggantikan layanan Puskesmas atau tenaga kesehatan.", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().staggerIn(5))
        }
    }
}

@Composable
private fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onActivate: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showServer by rememberSaveable { mutableStateOf(false) }
    ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().testTag("login_screen")) {
        ScreenHeader("Masuk", "Gunakan SEHATI ID atau nomor kontak", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        SehatiTextField(s.identifier, vm::onIdentifier, "SEHATI ID / nomor kontak", tag = "login_id_field", enabled = !s.loading)
        SehatiTextField(s.password, vm::onPassword, "Kata sandi", password = true, tag = "login_password_field", enabled = !s.loading)
        AnimatedVisibility2(s.error != null) { InfoNote(s.error.orEmpty(), color = RiskRedText, bg = RiskRedBg, icon = Icons.Outlined.ErrorOutline) }
        PrimaryButton("Masuk", vm::login, loading = s.loading, tag = "login_button")
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) { Text("Belum punya akun? Daftar", color = PrimaryDark) }
        SehatiCard(onClick = onActivate, modifier = Modifier.testTag("activate_entry")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HowToReg, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sudah didaftarkan kader Posyandu?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Aktifkan akunmu untuk melihat hasil pemeriksaan di HP sendiri.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted)
            }
        }
        TextButton({ showServer = !showServer }, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("toggle_server_settings")) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp), tint = TextMuted); Spacer(Modifier.width(6.dp)); Text(if (showServer) "Tutup pengaturan server" else "Pengaturan server", color = TextSecondary)
        }
        AnimatedVisibility2(showServer) { id.sehati.app.ui.settings.ServerSettingsCard() }

        if (BuildConfig.DEMO_MODE) {
            SehatiCard {
                Text("MODE DEMO", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                Text("Dataset sintetis (bukan data nyata). Setiap tombol tetap melalui proses login.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                SecondaryButton("Warga · HM-000127 (Tariska)", { vm.demoLogin(Role.WARGA) }, icon = Icons.Outlined.Person, enabled = !s.loading, tag = "demo_login_warga")
                SecondaryButton("Kader Posyandu · KD-000001", { vm.demoLogin(Role.KADER) }, icon = Icons.Outlined.Groups, enabled = !s.loading, tag = "demo_login_kader")
                SecondaryButton("Admin Puskesmas · AD-000001", { vm.demoLogin(Role.ADMIN) }, icon = Icons.Outlined.AdminPanelSettings, enabled = !s.loading, tag = "demo_login_admin")
                Text("Kata sandi demo: ${id.sehati.app.data.demo.DemoSeeder.DEMO_PASSWORD}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun AnimatedVisibility2(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible, enter = fadeIn(tween(Motion.Short)) + androidx.compose.animation.expandVertically(), exit = fadeOut(tween(Motion.Short)) + androidx.compose.animation.shrinkVertically()) { content() }
}

@Composable
private fun ActivateScreen(onBack: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val a by vm.activation.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().testTag("activate_screen")) {
        ScreenHeader("Aktifkan akun", "Untuk warga yang didaftarkan kader", onBack = onBack)
        InfoNote("Masukkan SEHATI ID dari kartu atau QR yang diberikan kader, lalu tanggal lahirmu sebagai verifikasi. Setelah itu buat kata sandi sendiri.", icon = Icons.Outlined.Info, color = TextSecondary, bg = SurfaceMuted)
        SehatiTextField(a.id, { v -> vm.onActivation { it.copy(id = v.uppercase()) } }, "SEHATI ID (mis. HM-000127)", tag = "activate_id_field", enabled = !a.loading)
        FieldLabel("Tanggal lahir")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(a.day, { v -> vm.onActivation { it.copy(day = v.filter(Char::isDigit).take(2)) } }, "Tgl", Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, tag = "activate_day")
            SehatiTextField(a.month, { v -> vm.onActivation { it.copy(month = v.filter(Char::isDigit).take(2)) } }, "Bln", Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, tag = "activate_month")
            SehatiTextField(a.year, { v -> vm.onActivation { it.copy(year = v.filter(Char::isDigit).take(4)) } }, "Tahun", Modifier.weight(1.4f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, tag = "activate_year")
        }
        SehatiTextField(a.password, { v -> vm.onActivation { it.copy(password = v) } }, "Kata sandi baru", password = true, tag = "activate_password", supporting = "Minimal 6 karakter")
        SehatiTextField(a.confirm, { v -> vm.onActivation { it.copy(confirm = v) } }, "Ulangi kata sandi", password = true, tag = "activate_confirm")
        AnimatedVisibility2(a.error != null) { InfoNote(a.error.orEmpty(), color = RiskRedText, bg = RiskRedBg, icon = Icons.Outlined.ErrorOutline) }
        PrimaryButton("Aktifkan & masuk", vm::activate, loading = a.loading, tag = "activate_button")
        Text("Memerlukan internet dan alamat server SEHATI (atur di halaman Masuk).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
