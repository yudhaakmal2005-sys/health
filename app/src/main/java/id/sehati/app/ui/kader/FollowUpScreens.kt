package id.sehati.app.ui.kader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.MeasurementInput
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

@Composable
fun FollowUpTab(vm: KaderViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_followup_screen")) {
        OverlineLabel("FOLLOW-UP")
        Text("Needs Monitoring · Needs Recheck · Needs Health Worker Review", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.followUps.isEmpty()) EmptyState(Icons.Outlined.EventAvailable, "Tidak ada tindak lanjut terbuka", "Hasil skrining yang perlu dipantau akan muncul di sini.")
        s.followUps.forEachIndexed { i, r ->
            var verified by remember(r.follow.id) { mutableStateOf(false) }
            val f = r.follow
            val pr = when (f.priority) { 3 -> Triple("Prioritas tinggi", RiskRedText, RiskRedBg); 2 -> Triple("Perlu ditinjau", RiskOrangeText, RiskOrangeBg); else -> Triple("Pantau", RiskYellowText, RiskYellowBg) }
            SehatiCard(Modifier.staggerIn(i).testTag("followup_${f.userId}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${f.userId} · ${r.name}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("RW ${r.rw}" + (r.lastCheck?.let { " · cek terakhir ${TimeUtils.shortDate(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    StatusPill(pr.first, pr.second, pr.third)
                }
                Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("${FollowUpType.parse(f.type).label} · ${FollowUpStatus.parse(f.status).label} · target ${TimeUtils.date(f.dueAt)}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Ukur ulang selesai", { vm.setFollowStatus(f.id, FollowUpStatus.DONE) }, Modifier.weight(1f), tag = "followup_done_${f.userId}")
                    SecondaryButton("Kunjungan rumah", { vm.scheduleHomeVisit(f.id, 2) }, Modifier.weight(1f), tag = "followup_home_${f.userId}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(verified, { verified = it }, Modifier.testTag("verify_${f.userId}"), colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = TextMuted))
                    Text("Hasil telah diverifikasi kader/tenaga kesehatan", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                }
                SecondaryButton("Ajukan evaluasi Puskesmas", { vm.requestReferral(f.id, verified, f.reason) }, icon = Icons.Outlined.LocalHospital, tag = "referral_${f.userId}")
            }
        }
        InfoNote("SEHATI hanya membantu administrasi dan komunikasi tindak lanjut. Tidak ada surat diagnosis yang diterbitkan otomatis.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    }
}

@Composable
fun HomeVisitTab(vm: KaderViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_homevisit_screen")) {
        OverlineLabel("ANTREAN KUNJUNGAN RUMAH")
        Text("Tiba → Verifikasi → Asesmen → Edukasi → Catat → Tindakan → Tutup", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.queue.isEmpty()) EmptyState(Icons.Outlined.Home, "Antrean kosong", "Jadwalkan kunjungan rumah dari tab Follow-Up.")
        s.queue.forEachIndexed { i, q -> HomeVisitCard(q, vm, Modifier.staggerIn(i)) }
    }
}

@Composable
private fun HomeVisitCard(q: QueueItem, vm: KaderViewModel, modifier: Modifier) {
    val h = q.home
    var sys by remember { mutableStateOf("") }; var dia by remember { mutableStateOf("") }
    var assessment by remember { mutableStateOf("") }; var education by remember { mutableStateOf("") }; var action by remember { mutableStateOf("") }
    var resolved by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf(h.measurementId != null) }
    val status = HomeVisitStatus.parse(h.status)
    SehatiCard(modifier.testTag("homevisit_${h.userId}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${h.userId} · ${q.name}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(status.label, TextSecondary, SurfaceMuted)
        }
        KeyValueRow("Alasan", q.follow?.reason ?: "-")
        KeyValueRow("Prioritas", when (q.follow?.priority) { 3 -> "Tinggi"; 2 -> "Sedang"; else -> "Pantau" })
        KeyValueRow("Kunjungan terakhir", q.lastVisit?.let { TimeUtils.date(it) } ?: "-")
        KeyValueRow("Jadwal", TimeUtils.date(h.scheduledAt))
        when (status) {
            HomeVisitStatus.PLANNED -> PrimaryButton("Tiba di lokasi", { vm.arrive(h.id) }, icon = Icons.Outlined.Place, tag = "arrive_${h.userId}")
            HomeVisitStatus.ARRIVED -> {
                Text("Verifikasi identitas warga sebelum melanjutkan.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                PrimaryButton("Identitas terverifikasi", { vm.verify(h.id, true) }, icon = Icons.Outlined.VerifiedUser, tag = "verify_home_${h.userId}")
            }
            HomeVisitStatus.IN_PROGRESS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SehatiTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, "Sistolik", Modifier.weight(1f), KeyboardType.Number, tag = "home_sys_${h.userId}")
                    SehatiTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, "Diastolik", Modifier.weight(1f), KeyboardType.Number, tag = "home_dia_${h.userId}")
                }
                SecondaryButton(if (measured) "Pengukuran tercatat" else "Catat pengukuran", {
                    vm.homeMeasure(h.id, MeasurementInput(sys.toIntOrNull(), dia.toIntOrNull())) { measured = true }
                }, icon = if (measured) Icons.Outlined.Check else null, tag = "home_measure_${h.userId}")
                SehatiTextField(assessment, { assessment = it }, "Asesmen/temuan", singleLine = false, tag = "home_assess_${h.userId}")
                SehatiTextField(education, { education = it }, "Edukasi yang diberikan", singleLine = false, tag = "home_edu_${h.userId}")
                SehatiTextField(action, { action = it }, "Tindakan/rencana", singleLine = false, tag = "home_action_${h.userId}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(resolved, { resolved = it }, Modifier.testTag("home_resolved_${h.userId}"), colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = TextMuted))
                    Text("Tindak lanjut dianggap selesai", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                PrimaryButton("Tutup kunjungan", { vm.closeHome(h.id, assessment, education, action, resolved) }, icon = Icons.Outlined.TaskAlt, tag = "close_home_${h.userId}")
            }
            HomeVisitStatus.CLOSED -> Unit
        }
    }
}

@Composable
fun SyncTab(vm: KaderViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val msg by vm.syncMessage.collectAsStateWithLifecycle()
    val busy by vm.syncing.collectAsStateWithLifecycle()
    val recent by vm.recentQueue.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_sync_screen")) {
        OverlineLabel("SINKRONISASI")
        SehatiCard {
            Text("Tujuan: ${s.destination}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            HorizontalDivider(color = SurfaceMuted)
            KeyValueRow("Menunggu kirim", s.sync.pending.toString(), valueColor = if (s.sync.pending > 0) RiskYellowText else TextPrimary)
            KeyValueRow("Gagal (akan dicoba lagi)", s.sync.failed.toString(), valueColor = if (s.sync.failed > 0) RiskRedText else TextPrimary)
            KeyValueRow("Terkirim", s.sync.done.toString())
            KeyValueRow("Terakhir", s.sync.lastSyncAt?.let { TimeUtils.dateTime(it) } ?: "-")
            msg?.let { InfoNote(it, icon = Icons.Outlined.Sync, color = TextSecondary, bg = SurfaceMuted) }
            PrimaryButton("Sinkronkan sekarang", vm::syncNow, loading = busy, icon = Icons.Outlined.Sync, tag = "kader_sync_button")
        }
        InfoNote("Aplikasi tetap bekerja tanpa internet. Semua data tersimpan di perangkat lebih dulu, lalu dikirim saat tersedia; kiriman ulang tidak membuat data ganda.", icon = Icons.Outlined.CloudOff, color = TextSecondary, bg = SurfaceMuted)
        SectionTitle("Aktivitas terbaru")
        if (recent.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                recent.forEachIndexed { i, q ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("${q.entityType} · ${q.operation}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Status ${q.status}" + (q.lastError?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}
