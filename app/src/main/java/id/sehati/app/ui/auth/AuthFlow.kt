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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeScreen(onStart: () -> Unit, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(PrimarySoft, Background, Background)))) {
        ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().testTag("welcome_screen"), scroll = true) {
            Spacer(Modifier.height(28.dp))
            HeartLogo(118.dp, modifier = Modifier.align(Alignment.CenterHorizontally).staggerIn(0))
            Text("SEHATI", style = MaterialTheme.typography.displaySmall, color = PrimaryDark, modifier = Modifier.align(Alignment.CenterHorizontally).staggerIn(1))
            Text("Kenali kesehatanmu.\nJaga jantungmu dari sekarang.", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().staggerIn(2))
            Text("Teman pemantauan kesehatan pribadi sekaligus penghubung ke Posyandu dan Puskesmas di desamu.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().staggerIn(3))
            FlowRow(Modifier.fillMaxWidth().staggerIn(4), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Icons.Outlined.FavoriteBorder to "Jantung sehat", Icons.Outlined.WifiOff to "Tanpa internet", Icons.Outlined.Lock to "Data aman").forEach { (ic, t) ->
                    StatusPill(t, PrimaryDark, PrimaryLight, ic)
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.staggerIn(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Mulai", onStart, icon = Icons.Outlined.ArrowForward, tag = "welcome_start_button")
                SecondaryButton("Sudah punya akun? Masuk", onLogin, tag = "welcome_login_button")
            }
            InfoNote("SEHATI bukan alat diagnosis dan tidak menggantikan layanan Puskesmas atau tenaga kesehatan.", Modifier.staggerIn(6))
        }
    }
}

@Composable
private fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onActivate: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showServer by rememberSaveable { mutableStateOf(false) }
    ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().testTag("login_screen")) {
        ScreenHeader("Masuk", "Gunakan SEHATI ID atau nomor kontak", onBack = onBack)
        SehatiTextField(s.identifier, vm::onIdentifier, "SEHATI ID / nomor kontak", tag = "login_id_field", enabled = !s.loading)
        SehatiTextField(s.password, vm::onPassword, "Kata sandi", password = true, tag = "login_password_field", enabled = !s.loading)
        AnimatedVisibility2(s.error != null) { InfoNote(s.error.orEmpty(), color = RiskRedText, bg = RiskRedBg, icon = Icons.Outlined.ErrorOutline) }
        PrimaryButton("Masuk", vm::login, loading = s.loading, tag = "login_button")
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) { Text("Belum punya akun? Daftar") }
        SehatiCard(onClick = onActivate, container = WellnessLight, border = Wellness.copy(alpha = 0.3f), modifier = Modifier.testTag("activate_entry")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.HowToReg, WellnessDark, Color.White, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sudah didaftarkan kader Posyandu?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Aktifkan akunmu untuk melihat hasil pemeriksaan di HP sendiri.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = WellnessDark)
            }
        }
        TextButton({ showServer = !showServer }, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("toggle_server_settings")) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (showServer) "Tutup pengaturan server" else "Pengaturan server")
        }
        AnimatedVisibility2(showServer) { id.sehati.app.ui.settings.ServerSettingsCard() }

        if (BuildConfig.DEMO_MODE) {
            SehatiCard(container = SurfaceMuted) {
                Text("MODE DEMO", style = MaterialTheme.typography.labelMedium, color = PrimaryDark, fontWeight = FontWeight.Bold)
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
        InfoNote("Masukkan SEHATI ID dari kartu atau QR yang diberikan kader, lalu tanggal lahirmu sebagai verifikasi. Setelah itu buat kata sandi sendiri.", icon = Icons.Outlined.Info)
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
