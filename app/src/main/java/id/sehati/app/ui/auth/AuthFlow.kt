package id.sehati.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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

private enum class AuthPage { Welcome, Onboarding, Login }

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
            AuthPage.Login -> LoginScreen(onBack = { page = AuthPage.Welcome }, onRegister = { page = AuthPage.Onboarding })
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onLogin: () -> Unit) {
    val pulse by heartbeatScale()
    ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().testTag("welcome_screen"), scroll = true) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).scale(pulse)) { IconBadge(Icons.Rounded.Favorite, Color.White, Primary, 96) }
        Text("SEHATI", style = MaterialTheme.typography.displaySmall, color = PrimaryDark, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Kenali kesehatanmu.\nJaga dari sekarang.", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().staggerIn(1))
        Text("Teman pemantauan kesehatan pribadi sekaligus penghubung ke Posyandu dan Puskesmas di desamu.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().staggerIn(2))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.staggerIn(3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Mulai", onStart, icon = Icons.Rounded.ArrowForward, tag = "welcome_start_button")
            SecondaryButton("Sudah punya akun? Masuk", onLogin, tag = "welcome_login_button")
        }
        InfoNote("SEHATI bukan alat diagnosis dan tidak menggantikan layanan Puskesmas atau tenaga kesehatan.", Modifier.staggerIn(4))
    }
}

@Composable
private fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().testTag("login_screen")) {
        ScreenHeader("Masuk", "Gunakan SEHATI ID atau nomor kontak", onBack = onBack)
        SehatiTextField(s.identifier, vm::onIdentifier, "SEHATI ID / nomor kontak", tag = "login_id_field", enabled = !s.loading)
        SehatiTextField(s.password, vm::onPassword, "Kata sandi", password = true, tag = "login_password_field", enabled = !s.loading)
        AnimatedVisibility2(s.error != null) { InfoNote(s.error.orEmpty(), color = RiskRedText, bg = RiskRedBg, icon = Icons.Rounded.ErrorOutline) }
        PrimaryButton("Masuk", vm::login, loading = s.loading, tag = "login_button")
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) { Text("Belum punya akun? Daftar") }

        if (BuildConfig.DEMO_MODE) {
            SehatiCard(container = SurfaceMuted) {
                Text("MODE DEMO", style = MaterialTheme.typography.labelMedium, color = PrimaryDark, fontWeight = FontWeight.Bold)
                Text("Dataset sintetis (bukan data nyata). Setiap tombol tetap melalui proses login.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                SecondaryButton("Warga · HM-000127 (Tariska)", { vm.demoLogin(Role.WARGA) }, icon = Icons.Rounded.Person, enabled = !s.loading, tag = "demo_login_warga")
                SecondaryButton("Kader Posyandu · KD-000001", { vm.demoLogin(Role.KADER) }, icon = Icons.Rounded.Groups, enabled = !s.loading, tag = "demo_login_kader")
                SecondaryButton("Admin Puskesmas · AD-000001", { vm.demoLogin(Role.ADMIN) }, icon = Icons.Rounded.AdminPanelSettings, enabled = !s.loading, tag = "demo_login_admin")
                Text("Kata sandi demo: ${id.sehati.app.data.demo.DemoSeeder.DEMO_PASSWORD}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun AnimatedVisibility2(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible, enter = fadeIn(tween(Motion.Short)) + androidx.compose.animation.expandVertically(), exit = fadeOut(tween(Motion.Short)) + androidx.compose.animation.shrinkVertically()) { content() }
}
