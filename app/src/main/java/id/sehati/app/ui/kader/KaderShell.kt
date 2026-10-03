package id.sehati.app.ui.kader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private enum class KaderTab(val label: String, val icon: ImageVector, val tag: String) {
    Today("Hari Ini", Icons.Outlined.Today, "kader_tab_today"),
    Citizens("Warga", Icons.Outlined.Groups, "kader_tab_citizens"),
    Exam("Pemeriksaan", Icons.Outlined.MonitorHeart, "kader_tab_exam"),
    FollowUp("Follow-Up", Icons.Outlined.EventAvailable, "kader_tab_followup"),
    Home("Kunjungan Rumah", Icons.Outlined.Home, "kader_tab_home"),
    Sync("Sinkronisasi", Icons.Outlined.Sync, "kader_tab_sync"),
}

/** Workstation kader (bukan navigasi warga): strip tab di atas, alur mengikuti layanan Posyandu ILP. */
@Composable
fun KaderShell(onLogout: () -> Unit, vm: KaderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(KaderTab.Today) }
    var visitId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanFirst by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val openVisit: (String) -> Unit = { id -> visitId = id; tab = KaderTab.Exam }

    Column(Modifier.fillMaxSize().background2().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Surface(color = CardWhite, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("KADER POSYANDU", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.2.sp)
                        Text(s.cadre?.fullName ?: "", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("kader_name"))
                        Text(s.facility, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    val pending = s.sync.pending
                    StatusPill(if (pending == 0) "Tersinkron" else "$pending menunggu", if (pending == 0) RiskGreenText else RiskYellowText, if (pending == 0) RiskGreenBg else RiskYellowBg,
                        if (pending == 0) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff, Modifier.testTag("kader_sync_pill"))
                    IconButton({ confirmLogout = true }, Modifier.size(48.dp).testTag("kader_logout_button")) { Icon(Icons.Outlined.Logout, "Keluar", tint = TextSecondary) }
                }
                NeutralTabStrip(KaderTab.entries, tab, { tab = it }, { it.label }, { it.tag }, Modifier.padding(top = 4.dp), icon = { it.icon })
            }
        }
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(Motion.Medium, 60)) + slideInVertically(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 48 }) togetherWith fadeOut(tween(Motion.Short))
        }, label = "kaderTab") { t ->
            when (t) {
                KaderTab.Today -> TodayTab(vm, openVisit, onStartRegistration = { scanFirst = true; tab = KaderTab.Citizens })
                KaderTab.Citizens -> CitizensTab(vm, openVisit, startWithScanner = scanFirst)
                KaderTab.Exam -> ExaminationTab(vm, visitId, { visitId = it })
                KaderTab.FollowUp -> FollowUpTab(vm)
                KaderTab.Home -> HomeVisitTab(vm)
                KaderTab.Sync -> SyncTab(vm)
            }
        }
    }
    if (confirmLogout) ConfirmDialog("Keluar?", "Pastikan data sudah tersinkron bila perlu. Data yang belum terkirim tetap aman di perangkat.", "Keluar", { confirmLogout = false; onLogout() }, { confirmLogout = false }, tag = "kader_logout_dialog")
}

private fun Modifier.background2(): Modifier = this.then(Modifier.background(Background))
