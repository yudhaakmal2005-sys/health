package id.sehati.app.ui.kader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        Text("FOLLOW-UP", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        Text("Needs Monitoring · Needs Recheck · Needs Health Worker Review", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        error?.let { InfoNote(it, icon = Icons.Rounded.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.followUps.isEmpty()) EmptyState(Icons.Rounded.EventAvailable, "Tidak ada tindak lanjut terbuka", "Hasil skrining yang perlu dipantau akan muncul di sini.")
        s.followUps.forEachIndexed { i, r ->
            var verified by remember(r.follow.id) { mutableStateOf(false) }
            val f = r.follow
            val pr = when (f.priority) { 3 -> Triple("Prioritas tinggi", RiskRedText, RiskRedBg); 2 -> Triple("Perlu ditinjau", RiskOrangeText, RiskOrangeBg); else -> Triple("Pantau", RiskYellowText, RiskYellowBg) }
            SehatiCard(Modifier.staggerIn(i).testTag("followup_${f.userId}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${f.userId} · ${r.name}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("RW ${r.rw}" + (r.lastCheck?.let { " · cek terakhir ${TimeUtils.shortDate(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    StatusPill(pr.first, pr.second, pr.third)
                }
                Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("${FollowUpType.parse(f.type).label} · ${FollowUpStatus.parse(f.status).label} · target ${TimeUtils.date(f.dueAt)}", style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Ukur ulang selesai", { vm.setFollowStatus(f.id, FollowUpStatus.DONE) }, Modifier.weight(1f), tag = "followup_done_${f.userId}")
                    SecondaryButton("Kunjungan rumah", { vm.scheduleHomeVisit(f.id, 2) }, Modifier.weight(1f), tag = "followup_home_${f.userId}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(verified, { verified = it }, Modifier.testTag("verify_${f.userId}"))
                    Text("Hasil telah diverifikasi kader/tenaga kesehatan", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                SecondaryButton("Ajukan evaluasi Puskesmas", { vm.requestReferral(f.id, verified, f.reason) }, icon = Icons.Rounded.LocalHospital, tag = "referral_${f.userId}")
            }
        }
        InfoNote("SEHATI hanya membantu administrasi dan komunikasi tindak lanjut. Tidak ada surat diagnosis yang diterbitkan otomatis.", icon = Icons.Rounded.Shield)
    }
}

@Composable
fun HomeVisitTab(vm: KaderViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_homevisit_screen")) {
        Text("ANTREAN KUNJUNGAN RUMAH", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        Text("Tiba → Verifikasi → Asesmen → Edukasi → Catat → Tindakan → Tutup", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        error?.let { InfoNote(it, icon = Icons.Rounded.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.queue.isEmpty()) EmptyState(Icons.Rounded.Home, "Antrean kosong", "Jadwalkan kunjungan rumah dari tab Follow-Up.")
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
        Text("${h.userId} · ${q.name}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        KeyValueRow("Alasan", q.follow?.reason ?: "-")
        KeyValueRow("Prioritas", when (q.follow?.priority) { 3 -> "Tinggi"; 2 -> "Sedang"; else -> "Pantau" })
        KeyValueRow("Kunjungan terakhir", q.lastVisit?.let { TimeUtils.date(it) } ?: "-")
        KeyValueRow("Jadwal", TimeUtils.date(h.scheduledAt))
        StatusPill(status.label, PrimaryDark, PrimaryLight)
        when (status) {
            HomeVisitStatus.PLANNED -> PrimaryButton("Tiba di lokasi", { vm.arrive(h.id) }, icon = Icons.Rounded.Place, tag = "arrive_${h.userId}")
            HomeVisitStatus.ARRIVED -> {
                Text("Verifikasi identitas warga sebelum melanjutkan.", style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Identitas terverifikasi", { vm.verify(h.id, true) }, icon = Icons.Rounded.VerifiedUser, tag = "verify_home_${h.userId}")
            }
            HomeVisitStatus.IN_PROGRESS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SehatiTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, "Sistolik", Modifier.weight(1f), KeyboardType.Number, tag = "home_sys_${h.userId}")
                    SehatiTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, "Diastolik", Modifier.weight(1f), KeyboardType.Number, tag = "home_dia_${h.userId}")
                }
                SecondaryButton(if (measured) "✓ Pengukuran tercatat" else "Catat pengukuran", {
                    vm.homeMeasure(h.id, MeasurementInput(sys.toIntOrNull(), dia.toIntOrNull())) { measured = true }
                }, tag = "home_measure_${h.userId}")
                SehatiTextField(assessment, { assessment = it }, "Asesmen/temuan", singleLine = false, tag = "home_assess_${h.userId}")
                SehatiTextField(education, { education = it }, "Edukasi yang diberikan", singleLine = false, tag = "home_edu_${h.userId}")
                SehatiTextField(action, { action = it }, "Tindakan/rencana", singleLine = false, tag = "home_action_${h.userId}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(resolved, { resolved = it }, Modifier.testTag("home_resolved_${h.userId}"))
                    Text("Tindak lanjut dianggap selesai", style = MaterialTheme.typography.bodyMedium)
                }
                PrimaryButton("Tutup kunjungan", { vm.closeHome(h.id, assessment, education, action, resolved) }, icon = Icons.Rounded.TaskAlt, tag = "close_home_${h.userId}")
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
        Text("SINKRONISASI", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        SehatiCard {
            Text("Tujuan: ${s.destination}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            KeyValueRow("Menunggu kirim", s.sync.pending.toString(), if (s.sync.pending > 0) RiskYellowText else TextPrimary)
            KeyValueRow("Gagal (akan dicoba lagi)", s.sync.failed.toString(), if (s.sync.failed > 0) RiskRedText else TextPrimary)
            KeyValueRow("Terkirim", s.sync.done.toString())
            KeyValueRow("Terakhir", s.sync.lastSyncAt?.let { TimeUtils.dateTime(it) } ?: "-")
            msg?.let { InfoNote(it, icon = Icons.Rounded.Sync) }
            PrimaryButton("Sinkronkan sekarang", vm::syncNow, loading = busy, icon = Icons.Rounded.Sync, tag = "kader_sync_button")
        }
        InfoNote("Aplikasi tetap bekerja tanpa internet. Semua data tersimpan di perangkat lebih dulu, lalu dikirim saat tersedia; kiriman ulang tidak membuat data ganda.", icon = Icons.Rounded.CloudOff)
        SectionTitle("Aktivitas terbaru")
        recent.forEach { q ->
            SehatiCard(contentPadding = 12) {
                Text("${q.entityType} · ${q.operation}", style = MaterialTheme.typography.titleSmall)
                Text("Status ${q.status}" + (q.lastError?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
