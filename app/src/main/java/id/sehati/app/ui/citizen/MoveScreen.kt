package id.sehati.app.ui.citizen

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.NumberFmt
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.ActivitySessionEntity
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.MovementClassifier
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

private val PICKABLE = listOf(MovementKind.WALKING, MovementKind.RUNNING, MovementKind.CYCLING)

@Composable
fun MoveScreen(vm: MoveViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val tr by vm.trackerState.collectAsStateWithLifecycle()
    val hc by vm.hc.collectAsStateWithLifecycle()
    val last by vm.lastResult.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(MovementKind.WALKING) }
    var manualMinutes by remember { mutableIntStateOf(20) }

    val view = LocalView.current
    DisposableEffect(tr.running) { view.keepScreenOn = tr.running; onDispose { view.keepScreenOn = false } }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startSession(kind) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.onPermissionsResult(it) }

    ScreenColumn(Modifier.testTag("move_screen")) {
        ScreenHeader("Aktivitas", "Today's Movement · gerak harianmu")

        SehatiCard(Modifier.staggerIn(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(s.steps.toFloat() / s.stepTarget, size = 104.dp, stroke = 11.dp) {
                    AnimatedNumber(s.steps, MaterialTheme.typography.titleLarge, format = { NumberFmt.thousands(it) })
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target ${NumberFmt.thousands(s.stepTarget)} langkah", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Sumber langkah: ${DataSource.parse(s.stepsSource).label}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    StatusPill("Aktif ${TimeUtils.durationLabel(s.summary.activeMinutes)}", PrimaryDark, PrimaryLight, Icons.Rounded.DirectionsWalk)
                    if (s.summary.passiveMinutes > 0) StatusPill("Transportasi ${TimeUtils.durationLabel(s.summary.passiveMinutes)}", TextSecondary, SurfaceMuted, Icons.Rounded.DirectionsCar)
                }
            }
        }

        // ---- Sesi GPS ----
        if (tr.running) {
            SehatiCard(Modifier.testTag("tracker_card"), container = PrimaryLight, border = Primary.copy(alpha = 0.3f)) {
                Text(if (tr.paused) "Dijeda" else "Sedang merekam · ${tr.declared.label}", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
                val secs = tr.activeMillis / 1000
                Text("%02d:%02d".format(secs / 60, secs % 60), style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("tracker_timer"))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column { Text("%.2f km".format(tr.distanceMeters / 1000f), style = MaterialTheme.typography.titleMedium); Text("jarak", style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                    Column { Text("%.1f km/j".format(tr.currentSpeedKmh), style = MaterialTheme.typography.titleMedium); Text("kecepatan", style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                    Column { Text(MovementClassifier.paceMinPerKm(tr.avgSpeedKmh), style = MaterialTheme.typography.titleMedium); Text("pace/km", style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                }
                if (!tr.gpsFixed) Text("Mencari sinyal GPS… pindah ke tempat terbuka.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tr.paused) PrimaryButton("Lanjut", vm::resume, Modifier.weight(1f), Icons.Rounded.PlayArrow, tag = "resume_session_button")
                    else SecondaryButton("Jeda", vm::pause, Modifier.weight(1f), Icons.Rounded.Pause, tag = "pause_session_button")
                    PrimaryButton("Selesai", vm::finishSession, Modifier.weight(1f), Icons.Rounded.Stop, tag = "finish_session_button")
                }
            }
        } else {
            SehatiCard(Modifier.staggerIn(1)) {
                Text("Mulai sesi", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                ChoiceChips(PICKABLE, kind, { kind = it }, { it.label }, tagPrefix = "kind")
                PrimaryButton("Mulai sesi", { if (vm.trackerHasPermission()) vm.startSession(kind) else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, icon = Icons.Rounded.PlayArrow, tag = "start_activity_button")
                InfoNote("Lokasi hanya dibaca selama sesi berjalan dan tidak disimpan per titik. Hanya ringkasan jarak dan durasi yang tersimpan.", icon = Icons.Rounded.LocationOn)
            }
        }
        notice?.let { InfoNote(it, color = RiskOrangeText, bg = RiskOrangeBg, icon = Icons.Rounded.Warning) }
        last?.let { r ->
            SehatiCard(Modifier.testTag("session_result_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedCheck(size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Sesi tersimpan", style = MaterialTheme.typography.titleSmall)
                        Text("%.2f km · %s".format(r.distanceMeters / 1000f, TimeUtils.durationLabel(((r.endAt - r.startAt) / 60000).toInt())), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                if (!r.classified.isActive) InfoNote("Kecepatan rata-rata seperti kendaraan, jadi sesi ini dicatat sebagai transportasi dan tidak dihitung sebagai aktivitas fisik.", icon = Icons.Rounded.DirectionsCar)
                else if (r.classified != r.declared) InfoNote("Jenis gerak disesuaikan menjadi ${r.classified.label.lowercase()} berdasarkan kecepatan.", icon = Icons.Rounded.Info)
                TextButton3("Tutup", vm::dismissResult)
            }
        }

        SectionTitle("Pergerakan hari ini")
        if (s.timeline.isEmpty()) EmptyState(Icons.Rounded.DirectionsWalk, "Belum ada pergerakan tercatat", "Mulai sesi, catat manual, atau sinkronkan Health Connect.")
        else s.timeline.forEachIndexed { i, a -> TimelineRow(a, Modifier.staggerIn(i)) }

        SehatiCard {
            Text("Catat manual", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            ChoiceChips(listOf(MovementKind.WALKING, MovementKind.RUNNING, MovementKind.CYCLING, MovementKind.EXERCISE, MovementKind.VEHICLE), kind, { kind = it }, { it.label }, tagPrefix = "mkind")
            NumberStepper("Durasi", manualMinutes, { manualMinutes = it }, 5..240, unit = " mnt", step = 5, tag = "manual_minutes")
            SecondaryButton("Simpan", { vm.addManual(kind, manualMinutes) }, tag = "save_manual_activity_button")
        }

        // ---- Health Connect ----
        SectionTitle("Health Connect")
        SehatiCard(Modifier.testTag("health_connect_card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Sync, PrimaryDark, PrimaryLight, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.hcLastSync > 0) "Tersambung" else "Belum tersambung", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(if (s.hcLastSync > 0) "Sinkron terakhir ${TimeUtils.dateTime(s.hcLastSync)}" else "Langkah, tidur, tekanan darah, dll.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("Dibaca: ${id.sehati.app.data.healthconnect.HealthConnectGateway.dataLabels.joinToString(", ")}. Diproses di perangkat; dikirim ke server hanya bila kamu mengizinkan. Izin dapat dicabut kapan saja.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            when (val h = hc) {
                HcUi.Idle -> Unit
                HcUi.Syncing -> LoadingState(label = "Menyinkronkan…")
                is HcUi.Message -> {
                    InfoNote(h.text, color = if (h.needsPermission || h.needsInstall) RiskOrangeText else PrimaryDark, bg = if (h.needsPermission || h.needsInstall) RiskOrangeBg else PrimaryLight)
                    if (h.needsPermission) SecondaryButton("Kelola izin", { hcLauncher.launch(vm.hcPermissions) }, tag = "manage_hc_permission_button")
                    if (h.needsInstall) SecondaryButton("Buka Play Store", { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }, tag = "install_hc_button")
                }
            }
            PrimaryButton("Sinkronkan Health Connect", { vm.syncHealthConnect() }, icon = Icons.Rounded.Sync, loading = hc == HcUi.Syncing, tag = "sync_health_connect_button")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TimelineRow(a: ActivitySessionEntity, modifier: Modifier = Modifier) {
    val k = MovementKind.parse(a.kind)
    val active = k.isActive
    SehatiCard(modifier, contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TimeUtils.time(a.startAt), style = MaterialTheme.typography.titleSmall, color = TextMuted, modifier = Modifier.width(52.dp))
            IconBadge(if (active) Icons.Rounded.DirectionsWalk else Icons.Rounded.TwoWheeler, if (active) PrimaryDark else TextSecondary, if (active) PrimaryLight else SurfaceMuted, 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(k.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("${TimeUtils.durationLabel(((a.endAt - a.startAt) / 60000).toInt())}" + if (a.distanceMeters > 0) " · %.2f km".format(a.distanceMeters / 1000f) else "" + " · ${DataSource.parse(a.source).label}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            StatusPill(if (active) "GERAK AKTIF" else "TRANSPORTASI PASIF", if (active) PrimaryDark else TextSecondary, if (active) PrimaryLight else SurfaceMuted)
        }
    }
}

@Composable
private fun TextButton3(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick, Modifier.heightIn(min = 48.dp)) { Text(text) }
}
