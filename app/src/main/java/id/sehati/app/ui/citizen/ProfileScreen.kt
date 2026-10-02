package id.sehati.app.ui.citizen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.BuildConfig
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.rules.AgeCalc
import id.sehati.app.domain.rules.DailyTargets
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

@Composable
fun ProfileScreen(onShowQr: () -> Unit, onLogout: () -> Unit, onOpenNotifications: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val u = s.user ?: run { ScreenColumn { SkeletonBlock(120.dp) }; return }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ScreenColumn(Modifier.testTag("profile_screen")) {
        ScreenHeader("Profil", "Identitas, privasi, dan pengaturan")
        SehatiCard(Modifier.staggerIn(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Person, PrimaryDark, PrimaryLight, 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.fullName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("SEHATI ID  ${u.sehatiId}", style = MaterialTheme.typography.titleSmall, color = PrimaryDark, modifier = Modifier.testTag("profile_sehati_id"))
                    Text("${AgeCalc.age(u.birthDate)} tahun · ${Sex.parse(u.sex).label} · RW ${u.rw}, ${u.village}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            SecondaryButton("Tampilkan QR", onShowQr, icon = Icons.Rounded.QrCode2, tag = "profile_show_qr_button")
            Text("SEHATI ID bukan pengganti NIK untuk keperluan administratif resmi.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        SectionTitle("Sinkronisasi")
        SehatiCard(Modifier.testTag("sync_card")) {
            Text("Tujuan: ${s.destination}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("Menunggu ${s.sync.pending} · gagal ${s.sync.failed} · terkirim ${s.sync.done}" + (s.sync.lastSyncAt?.let { " · terakhir ${TimeUtils.dateTime(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            s.syncMessage?.let { InfoNote(it, icon = Icons.Rounded.Sync) }
            PrimaryButton("Sinkronkan sekarang", vm::syncNow, icon = Icons.Rounded.Sync, loading = s.syncing, tag = "profile_sync_button")
            if (!u.consentServerSync) InfoNote("Sinkronisasi ke server nonaktif. Data hanya di perangkat ini.", icon = Icons.Rounded.CloudOff, color = RiskYellowText, bg = RiskYellowBg)
        }

        SectionTitle("Privasi & persetujuan")
        SehatiCard(Modifier.testTag("consent_card")) {
            SwitchRow("Simpan data di perangkat", "Wajib agar SEHATI bekerja.", true, {}, "profile_consent_local")
            SwitchRow("Sinkronisasi ke server", "Agar Posyandu/Puskesmas dapat menindaklanjuti. Dapat dimatikan kapan saja.", u.consentServerSync, { vm.setConsent(true, it, u.consentHealthConnect) }, "profile_consent_server")
            SwitchRow("Baca Health Connect", "Langkah, tidur, tekanan darah, dll. Diproses di perangkat.", u.consentHealthConnect, { vm.setConsent(true, u.consentServerSync, it) }, "profile_consent_hc")
            u.consentAt?.let { Text("Persetujuan terakhir diperbarui ${TimeUtils.date(it)}", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }

        SectionTitle("Target harian")
        TargetsCard(s.settings.targets, vm::setTargets)

        SectionTitle("Pengingat")
        SehatiCard(Modifier.testTag("notification_card")) {
            val n = s.settings.notifications
            SwitchRow("Pemeriksaan kesehatan", null, n.healthCheck, { vm.setNotifications(n.copy(healthCheck = it)) }, "notif_health")
            SwitchRow("Aktivitas", null, n.activity, { vm.setNotifications(n.copy(activity = it)) }, "notif_activity")
            SwitchRow("Minum air", null, n.water, { vm.setNotifications(n.copy(water = it)) }, "notif_water")
            SwitchRow("Edukasi", null, n.education, { vm.setNotifications(n.copy(education = it)) }, "notif_education")
            SwitchRow("Jadwal Posyandu", null, n.posyandu, { vm.setNotifications(n.copy(posyandu = it)) }, "notif_posyandu")
            SwitchRow("Tindak lanjut", null, n.followUp, { vm.setNotifications(n.copy(followUp = it)) }, "notif_followup")
            if (Build.VERSION.SDK_INT >= 33) SecondaryButton("Izinkan notifikasi", { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            SecondaryButton("Kotak masuk notifikasi", onOpenNotifications, icon = Icons.Rounded.Notifications)
        }

        if (BuildConfig.DEMO_MODE) {
            SectionTitle("Mode demo")
            SehatiCard {
                SwitchRow("Server simulasi", "Hanya demo: sinkronisasi dibalas simulasi di perangkat, tidak ada data keluar. Matikan untuk memakai BASE_URL.", s.settings.demoServerSimulation, vm::setDemoSimulation, "demo_sim_switch")
                Text("Untuk berpindah peran, keluar lalu masuk sebagai Kader/Admin dari layar masuk.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        SectionTitle("Akun")
        SecondaryButton("Keluar", { confirmLogout = true }, icon = Icons.Rounded.Logout, tag = "logout_button")
        TextButton({ confirmDelete = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_data_button")) { Text("Hapus data saya", color = RiskRedText) }
        Text("SEHATI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(8.dp))
    }
    if (confirmLogout) ConfirmDialog("Keluar dari SEHATI?", "Data tetap tersimpan di perangkat.", "Keluar", { confirmLogout = false; onLogout() }, { confirmLogout = false }, tag = "logout_dialog")
    if (confirmDelete) ConfirmDialog("Hapus semua data?", "Semua data kesehatanmu di perangkat ini akan dihapus permanen dan tidak dapat dikembalikan. Bila sinkronisasi aktif, permintaan penghapusan dikirim ke server saat online.", "Hapus", { confirmDelete = false; vm.deleteMyData(onLogout) }, { confirmDelete = false }, tag = "delete_dialog")
}

@Composable
private fun TargetsCard(t: DailyTargets, onChange: (DailyTargets) -> Unit) {
    SehatiCard(Modifier.testTag("targets_card")) {
        NumberStepper("Langkah", t.steps, { onChange(t.copy(steps = it)) }, 1000..30000, step = 500, tag = "target_steps")
        NumberStepper("Air (gelas)", t.waterGlasses, { onChange(t.copy(waterGlasses = it)) }, 1..20, tag = "target_water")
        NumberStepper("Tidur (jam)", t.sleepHours.toInt(), { onChange(t.copy(sleepHours = it.toFloat())) }, 4..12, tag = "target_sleep")
        NumberStepper("Batas natrium (mg)", t.sodiumMgLimit, { onChange(t.copy(sodiumMgLimit = it)) }, 1000..4000, step = 100, tag = "target_sodium")
        Text("Target dapat disesuaikan; bukan angka universal untuk semua orang.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
