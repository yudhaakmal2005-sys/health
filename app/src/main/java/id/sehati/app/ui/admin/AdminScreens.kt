package id.sehati.app.ui.admin

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.FollowUpType
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

@Composable
fun OverviewTab(vm: AdminViewModel, onOpenFollowUp: () -> Unit, onOpenCommunity: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s.stats
    ScreenColumn(Modifier.testTag("admin_overview_screen")) {
        Text("OVERVIEW", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        if (st == null) { LoadingState(); return@ScreenColumn }
        InfoNote(CommunityAnalytics.TERMINOLOGY, icon = Icons.Rounded.Info)

        Text("Masalah kesehatan apa yang terlihat?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Warga terdaftar", st.totalRegistered.toString(), Primary, Modifier.weight(1f), "admin_kpi_registered")
            Kpi("Cakupan skrining", "${st.screeningCoverage.percent}%", Wellness, Modifier.weight(1f), "admin_kpi_screening", st.screeningCoverage.label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Tindak lanjut terbuka", st.openFollowUps.toString(), RiskOrange, Modifier.weight(1f), "admin_kpi_followup")
            Kpi("Cakupan tindak lanjut", "${st.followUpCoverage.percent}%", PrimaryDark, Modifier.weight(1f), "admin_kpi_followup_cov", st.followUpCoverage.label)
        }
        SehatiCard(Modifier.staggerIn(1)) {
            Text("Distribusi hasil skrining peserta", style = MaterialTheme.typography.titleSmall)
            BarRow("TD rentang tinggi", st.elevatedBp.numerator, maxOf(st.elevatedBp.denominator, 1), RiskOrange, valueLabel = "${st.elevatedBp.percent}%")
            BarRow("Gula di atas normal", st.elevatedGlucose.numerator, maxOf(st.elevatedGlucose.denominator, 1), RiskYellow, valueLabel = "${st.elevatedGlucose.percent}%")
            BarRow("Perokok aktif", st.smoking.numerator, maxOf(st.smoking.denominator, 1), RiskRed, valueLabel = "${st.smoking.percent}%")
            BarRow("Indikator obesitas", st.obesityIndicator.numerator, maxOf(st.obesityIndicator.denominator, 1), RiskOrange, valueLabel = "${st.obesityIndicator.percent}%")
            BarRow("Aktivitas kurang", st.physicalInactivity.numerator, maxOf(st.physicalInactivity.denominator, 1), Primary, valueLabel = "${st.physicalInactivity.percent}%")
            Text("Penyebut: TD ${st.elevatedBp.denominator}, gula ${st.elevatedGlucose.denominator}, asesmen ${st.smoking.denominator} peserta. Bukan prevalensi populasi.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        SehatiCard(Modifier.staggerIn(2)) {
            Text("Profil SEHATI warga", style = MaterialTheme.typography.titleSmall)
            val max = maxOf(st.riskDistribution.values.maxOrNull() ?: 1, 1)
            RiskLevel.entries.forEach { lvl -> val c = lvl.style(); BarRow(lvl.label, st.riskDistribution[lvl] ?: 0, max, c.color) }
        }
        Text("Di mana masalahnya?", style = MaterialTheme.typography.titleMedium)
        SecondaryButton("Lihat Heart Map per RW", onOpenCommunity, icon = Icons.Rounded.Map, tag = "admin_open_map")
        Text("Siapa yang perlu ditindaklanjuti?", style = MaterialTheme.typography.titleMedium)
        s.registry.take(3).forEach { r -> RegistryCard(r, null, {}, {}, Modifier.testTag("overview_followup_${r.follow.userId}"), compact = true) }
        if (s.registry.isEmpty()) InfoNote("Tidak ada tindak lanjut terbuka.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        SecondaryButton("Buka registri tindak lanjut", onOpenFollowUp, tag = "admin_open_followup")
    }
}

@Composable
private fun Kpi(label: String, value: String, color: Color, modifier: Modifier, tag: String, sub: String? = null) {
    SehatiCard(modifier.testTag(tag), contentPadding = 14) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** Heart Map: hanya agregat per RW. Tidak ada nama, NIK, HP, alamat, atau hasil individu. */
@Composable
fun CommunityTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s.stats
    ScreenColumn(Modifier.testTag("admin_community_screen")) {
        Text("COMMUNITY · HEART MAP", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        if (st == null) { LoadingState(); return@ScreenColumn }
        st.map.forEachIndexed { i, c ->
            val (bg, fg, icon) = when (c.state) {
                MapState.INSUFFICIENT -> Triple(SurfaceMuted, TextSecondary, Icons.Rounded.HelpOutline)
                MapState.LOWER -> Triple(RiskGreenBg, RiskGreenText, Icons.Rounded.CheckCircle)
                MapState.HIGHER -> Triple(RiskOrangeBg, RiskOrangeText, Icons.Rounded.MonitorHeart)
                MapState.FOLLOW_UP_CONCENTRATION -> Triple(RiskRedBg, RiskRedText, Icons.Rounded.EventAvailable)
            }
            SehatiCard(Modifier.staggerIn(i).testTag("map_rw_${c.rw}"), container = bg, border = fg.copy(alpha = 0.3f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon, fg, Color.White, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RW ${c.rw}", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
                        Text(c.state.label, style = MaterialTheme.typography.labelLarge, color = fg)
                    }
                }
                Text("Terdaftar ${c.registered} · diskrining ${c.screened} · pemantauan lebih tinggi ${c.higherNeed} · tindak lanjut terbuka ${c.openFollowUps}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        InfoNote("Heart Map hanya menampilkan data agregat. Sel dengan kurang dari ${CommunityAnalytics.MIN_CELL} peserta diskrining ditandai \"Data belum cukup\" untuk melindungi privasi dan mencegah salah tafsir.", icon = Icons.Rounded.Shield)
    }
}

@Composable
fun FollowUpAdminTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val activeCadres = s.cadres.filter { it.cadre.active }
    ScreenColumn(Modifier.testTag("admin_followup_screen")) {
        Text("FOLLOW-UP REQUIRED", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        error?.let { InfoNote(it, icon = Icons.Rounded.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.registry.isEmpty()) EmptyState(Icons.Rounded.CheckCircle, "Tidak ada tindak lanjut terbuka", "Semua tindak lanjut sudah selesai.")
        s.registry.forEachIndexed { i, r -> RegistryCard(r, activeCadres, { cid -> vm.assign(r.follow.id, cid) }, { vm.scheduleRecheck(r.follow.id, 7) }, Modifier.staggerIn(i).testTag("registry_${r.follow.userId}")) }
        InfoNote("Admin tidak mengakses detail kesehatan individu. SEHATI tidak menerbitkan diagnosis; tindak lanjut bersifat administratif dan memerlukan verifikasi tenaga kesehatan.", icon = Icons.Rounded.Shield)
    }
}

@Composable
private fun RegistryCard(r: RegistryRow, cadres: List<CadreRow>?, onAssign: (String?) -> Unit, onSchedule: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    val f = r.follow
    var menu by remember { mutableStateOf(false) }
    val pr = when (f.priority) { 3 -> Triple("Prioritas tinggi", RiskRedText, RiskRedBg); 2 -> Triple("Perlu ditinjau", RiskOrangeText, RiskOrangeBg); else -> Triple("Pantau", RiskYellowText, RiskYellowBg) }
    SehatiCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.userId, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
            StatusPill(pr.first, pr.second, pr.third)
        }
        Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text("RW ${r.rw} · ${FollowUpType.parse(f.type).label} · ${FollowUpStatus.parse(f.status).label}" + (r.lastCheck?.let { " · cek terakhir ${TimeUtils.shortDate(it)}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = TextMuted)
        r.cadreName?.let { Text("Ditugaskan: $it", style = MaterialTheme.typography.bodySmall, color = PrimaryDark) }
        if (!compact && cadres != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    SecondaryButton("Tugaskan kader", { menu = true }, tag = "assign_${f.userId}")
                    DropdownMenu(menu, { menu = false }) {
                        cadres.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { menu = false; onAssign(c.cadre.sehatiId) }, modifier = Modifier.testTag("assign_to_${c.cadre.sehatiId}")) }
                        if (cadres.isEmpty()) DropdownMenuItem(text = { Text("Tidak ada kader aktif") }, onClick = { menu = false })
                    }
                }
                SecondaryButton("Jadwalkan ukur ulang", onSchedule, Modifier.weight(1f), tag = "schedule_${f.userId}")
            }
        }
    }
}

@Composable
fun ReportsTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    fun share(text: String?, subject: String) {
        if (text == null) return
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, text) }, "Bagikan laporan"))
    }
    ScreenColumn(Modifier.testTag("admin_reports_screen")) {
        Text("REPORTS", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        val st = s.stats
        if (st == null) { LoadingState(); return@ScreenColumn }
        SehatiCard {
            Text("Ringkasan laporan", style = MaterialTheme.typography.titleSmall)
            Text(vm.reportText().orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.testTag("report_preview"))
        }
        PrimaryButton("Bagikan laporan (teks)", { share(vm.reportText(), "Laporan SEHATI") }, icon = Icons.Rounded.Share, tag = "share_report_button")
        SecondaryButton("Bagikan data per RW (CSV)", { share(vm.reportCsv(), "SEHATI per RW (CSV)") }, icon = Icons.Rounded.TableChart, tag = "share_csv_button")
        InfoNote("Laporan hanya berisi angka agregat dan terminologi \"distribusi hasil skrining\". Tidak ada nama, NIK, nomor HP, alamat, atau hasil individu.", icon = Icons.Rounded.Shield)
    }
}

@Composable
fun CadresTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<String?>(null) }
    ScreenColumn(Modifier.testTag("admin_cadres_screen")) {
        Text("CADRES", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        error?.let { InfoNote(it, icon = Icons.Rounded.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        created?.let { InfoNote("Akun kader $it dibuat. Berikan ID dan kata sandi sementara kepada kader.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg, modifier = Modifier.testTag("cadre_created_note")) }
        PrimaryButton("Tambah kader", { adding = true; vm.clearError() }, icon = Icons.Rounded.PersonAdd, tag = "add_cadre_button")
        if (s.cadres.isEmpty()) EmptyState(Icons.Rounded.Groups, "Belum ada kader", "Kader akan muncul setelah akun diprovisikan.")
        s.cadres.forEachIndexed { i, c ->
            SehatiCard(Modifier.staggerIn(i).testTag("cadre_${c.cadre.sehatiId}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Person, WellnessDark, WellnessLight, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text("${c.cadre.sehatiId} · RW ${c.cadre.assignedRw}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(c.cadre.active, { vm.setCadreActive(c.cadre.sehatiId, it) }, Modifier.testTag("cadre_switch_${c.cadre.sehatiId}"))
                }
                Text(if (c.cadre.active) "Aktif" else "Nonaktif", style = MaterialTheme.typography.labelMedium, color = if (c.cadre.active) RiskGreenText else TextMuted)
            }
        }
    }
    if (adding) AddCadreDialog(vm, onDismiss = { adding = false }, onCreated = { adding = false; created = it })
}

@Composable
fun LogisticsTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("admin_logistics_screen")) {
        Text("LOGISTICS", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        s.logistics.forEachIndexed { i, l ->
            val low = l.stock < l.minStock
            SehatiCard(Modifier.staggerIn(i).testTag("logistic_${l.id}"), container = if (low) RiskOrangeBg else CardWhite, border = if (low) RiskOrange.copy(alpha = 0.4f) else BorderColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.titleSmall)
                        Text("Stok ${l.stock} ${l.unit} · minimum ${l.minStock}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (low) StatusPill("Stok menipis", RiskOrangeText, Color.White, Icons.Rounded.Warning)
                    }
                    IconButton({ vm.adjustStock(l.id, -1) }, Modifier.size(48.dp).testTag("stock_minus_${l.id}")) { Icon(Icons.Rounded.Remove, "Kurangi stok ${l.name}") }
                    IconButton({ vm.adjustStock(l.id, 5) }, Modifier.size(48.dp).testTag("stock_plus_${l.id}")) { Icon(Icons.Rounded.Add, "Tambah stok ${l.name}") }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val msg by vm.syncMessage.collectAsStateWithLifecycle()
    val t = ClinicalThresholds()
    val rules = listOf(
        BloodPressureRules.interpret(120, 80).rule, GlucoseRules.interpret(100f).rule, LipidRules.interpret(150f).rule,
        AnthropometryRules.classify(170f, 65f).rule,
    )
    ScreenColumn(Modifier.testTag("admin_settings_screen")) {
        Text("SETTINGS · ATURAN & AUDIT", style = MaterialTheme.typography.labelLarge, color = PrimaryDark)
        SehatiCard {
            Text("Sinkronisasi", style = MaterialTheme.typography.titleSmall)
            Text("Tujuan: ${s.destination}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            KeyValueRow("Menunggu", s.sync.pending.toString()); KeyValueRow("Gagal", s.sync.failed.toString())
            msg?.let { InfoNote(it) }
            SecondaryButton("Sinkronkan sekarang", vm::syncNow, icon = Icons.Rounded.Sync, tag = "admin_sync_button")
        }
        SehatiCard {
            Text("Versi aturan klinis: ${ClinicalThresholds.VERSION}", style = MaterialTheme.typography.titleSmall)
            Text("Ambang adalah konfigurasi internal aplikasi (bukan klaim standar resmi WHO/Kemenkes). Setiap aturan berikut mencatat sumber, penjelasan, keterbatasan, dan tindakan.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            KeyValueRow("TD tinggi", "≥ ${t.bpHighSys}/${t.bpHighDia} mmHg"); KeyValueRow("TD sangat tinggi", "≥ ${t.bpUrgentSys}/${t.bpUrgentDia} mmHg")
            KeyValueRow("GDS di atas normal", "≥ ${t.gdsElevated.toInt()} mg/dL"); KeyValueRow("GDS tinggi", "≥ ${t.gdsHigh.toInt()} mg/dL")
            KeyValueRow("Kolesterol total tinggi", "≥ ${t.cholHigh.toInt()} mg/dL"); KeyValueRow("IMT obesitas I", "≥ ${t.bmiObese1}")
        }
        rules.forEach { r ->
            SehatiCard(contentPadding = 14) {
                Text(r.rule, style = MaterialTheme.typography.titleSmall)
                Text("Sumber: ${r.source}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("Keterbatasan: ${r.limitations}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text("Tindakan: ${r.action}", style = MaterialTheme.typography.bodySmall, color = PrimaryDark)
            }
        }
        SectionTitle("Audit log")
        s.audit.take(40).forEach { a ->
            Text("${TimeUtils.dateTime(a.at)} · ${a.actorRole} ${a.actorId} · ${a.action}" + (a.subjectId?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(vertical = 2.dp))
        }
        if (s.audit.isEmpty()) Text("Belum ada catatan audit.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun AddCadreDialog(vm: AdminViewModel, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }; var rw by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.testTag("add_cadre_dialog"), containerColor = CardWhite,
        title = { Text("Tambah kader") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SehatiTextField(name, { name = it }, "Nama lengkap", tag = "cadre_name_field")
                SehatiTextField(rw, { rw = it.filter(Char::isDigit).take(2) }, "RW tugas", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, tag = "cadre_rw_field")
                SehatiTextField(pw, { pw = it }, "Kata sandi sementara", password = true, tag = "cadre_password_field", supporting = "Minimal 6 karakter")
            }
        },
        confirmButton = { TextButton({ vm.addCadre(name, rw.ifBlank { "01" }, pw) { onCreated(it) } }, Modifier.heightIn(min = 48.dp).testTag("cadre_save_button")) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Batal") } },
    )
}
