package id.sehati.app.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import id.sehati.app.ui.components.WelcomeOverlay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.ui.admin.AdminShell
import id.sehati.app.ui.auth.AuthFlow
import id.sehati.app.ui.citizen.CitizenShell
import id.sehati.app.ui.kader.KaderShell
import id.sehati.app.ui.onboarding.AssessmentFlow
import id.sehati.app.ui.theme.Background
import id.sehati.app.ui.theme.Motion
import id.sehati.app.ui.theme.motionTween

/**
 * Akar aplikasi. Layar yang tampil ditentukan sesi terautentikasi + role (RBAC), bukan tombol di UI:
 * Warga → Kader → Admin tidak dapat saling dibuka tanpa login yang sah.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SehatiRoot(vm: AppViewModel = hiltViewModel()) {
    val root by vm.root.collectAsStateWithLifecycle()
    val welcome by vm.welcome.collectAsStateWithLifecycle()
    // testTag juga terlihat sebagai resource-id agar UiAutomator (rekaman video demo) dapat menemukannya.
    Box(Modifier.fillMaxSize().background(Background).semantics { testTagsAsResourceId = true }) {
        AnimatedContent(
            targetState = root,
            transitionSpec = {
                (fadeIn(tween(Motion.Medium, 80)) + scaleIn(tween(Motion.Medium, 80), initialScale = 0.98f)) togetherWith
                    (fadeOut(tween(Motion.Short)) + scaleOut(tween(Motion.Short), targetScale = 1.01f))
            },
            label = "root",
        ) { state ->
            when (state) {
                RootState.Preparing -> SplashScreen()
                RootState.LoggedOut -> AuthFlow(onAssessmentNeeded = vm::beginAssessmentFlow)
                RootState.Assessment -> AssessmentFlow(onFinished = vm::finishAssessmentFlow)
                RootState.Citizen -> CitizenShell(onLogout = vm::logout, onRetakeAssessment = vm::beginAssessmentFlow)
                RootState.Kader -> KaderShell(onLogout = vm::logout)
                RootState.Admin -> AdminShell(onLogout = vm::logout)
            }
        }
        AnimatedVisibility(welcome != null, enter = fadeIn(tween(Motion.Short)), exit = fadeOut(motionTween(Motion.Long))) {
            welcome?.let { w -> WelcomeOverlay(w.name, w.label, onDone = vm::dismissWelcome) }
        }
    }
}
