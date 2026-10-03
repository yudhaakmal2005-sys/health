package id.sehati.app.ui.admin

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private enum class AdminTab(val label: String, val icon: ImageVector, val tag: String) {
    Overview("Overview", Icons.Outlined.Dashboard, "admin_tab_overview"),
    Community("Community", Icons.Outlined.Map, "admin_tab_community"),
    FollowUp("Follow-Up", Icons.Outlined.EventAvailable, "admin_tab_followup"),
    Reports("Reports", Icons.Outlined.Assessment, "admin_tab_reports"),
    Cadres("Cadres", Icons.Outlined.Groups, "admin_tab_cadres"),
    Logistics("Logistics", Icons.Outlined.Inventory2, "admin_tab_logistics"),
    Settings("Settings", Icons.Outlined.Settings, "admin_tab_settings"),
}

/** Dashboard Puskesmas: hanya agregat + registri tindak lanjut; tidak ada akses detail kesehatan individu. */
@Composable
fun AdminShell(onLogout: () -> Unit, vm: AdminViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AdminTab.Overview) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding()) {
        Surface(color = CardWhite, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin Puskesmas", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("admin_name_label"))
                        Text(s.admin?.fullName ?: "", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    IconButton({ confirm = true }, Modifier.size(48.dp).testTag("admin_logout_button")) { Icon(Icons.Outlined.Logout, "Keluar", tint = TextSecondary) }
                }
                NeutralTabStrip(AdminTab.entries, tab, { tab = it }, { it.label }, { it.tag }, Modifier.padding(top = 4.dp), icon = { it.icon })
            }
        }
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(Motion.Medium, 60)) + slideInVertically(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 48 }) togetherWith fadeOut(tween(Motion.Short))
        }, label = "adminTab") { t ->
            when (t) {
                AdminTab.Overview -> OverviewTab(vm, onOpenFollowUp = { tab = AdminTab.FollowUp }, onOpenCommunity = { tab = AdminTab.Community })
                AdminTab.Community -> CommunityTab(vm)
                AdminTab.FollowUp -> FollowUpAdminTab(vm)
                AdminTab.Reports -> ReportsTab(vm)
                AdminTab.Cadres -> CadresTab(vm)
                AdminTab.Logistics -> LogisticsTab(vm)
                AdminTab.Settings -> SettingsTab(vm)
            }
        }
    }
    if (confirm) ConfirmDialog("Keluar?", "Anda akan keluar dari dashboard admin.", "Keluar", { confirm = false; onLogout() }, { confirm = false }, tag = "admin_logout_dialog")
}
