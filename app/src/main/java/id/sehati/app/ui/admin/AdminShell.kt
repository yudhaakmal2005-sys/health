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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private enum class AdminTab(val label: String, val icon: ImageVector, val tag: String) {
    Overview("Overview", Icons.Rounded.Dashboard, "admin_tab_overview"),
    Community("Community", Icons.Rounded.Map, "admin_tab_community"),
    FollowUp("Follow-Up", Icons.Rounded.EventAvailable, "admin_tab_followup"),
    Reports("Reports", Icons.Rounded.Assessment, "admin_tab_reports"),
    Cadres("Cadres", Icons.Rounded.Groups, "admin_tab_cadres"),
    Logistics("Logistics", Icons.Rounded.Inventory2, "admin_tab_logistics"),
    Settings("Settings", Icons.Rounded.Settings, "admin_tab_settings"),
}

/** Dashboard Puskesmas: hanya agregat + registri tindak lanjut; tidak ada akses detail kesehatan individu. */
@Composable
fun AdminShell(onLogout: () -> Unit, vm: AdminViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AdminTab.Overview) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding()) {
        Surface(color = PrimaryDark) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.AdminPanelSettings, PrimaryDark, androidx.compose.ui.graphics.Color.White, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Admin Puskesmas", style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.testTag("admin_name_label"))
                        Text(s.admin?.fullName ?: "", style = MaterialTheme.typography.bodySmall, color = PrimaryLight)
                    }
                    IconButton({ confirm = true }, Modifier.size(48.dp).testTag("admin_logout_button")) { Icon(Icons.Rounded.Logout, "Keluar", tint = androidx.compose.ui.graphics.Color.White) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTab.entries.forEach { t ->
                        FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }, leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag(t.tag),
                            colors = FilterChipDefaults.filterChipColors(containerColor = PrimaryDark, labelColor = PrimaryLight, iconColor = PrimaryLight, selectedContainerColor = androidx.compose.ui.graphics.Color.White, selectedLabelColor = PrimaryDark, selectedLeadingIconColor = PrimaryDark),
                            border = null)
                    }
                }
            }
        }
        AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(Motion.Medium, 60)) + slideInVertically(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 24 }) togetherWith fadeOut(tween(Motion.Short))
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
