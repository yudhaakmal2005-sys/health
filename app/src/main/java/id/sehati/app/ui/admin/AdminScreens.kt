package id.sehati.app.ui.admin

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        OverlineLabel("OVERVIEW")
        if (st == null) { LoadingState(); return@ScreenColumn }
        InfoNote(CommunityAnalytics.TERMINOLOGY, icon = Icons.Outlined.Info, color = TextSecondary, bg = SurfaceMuted)

        SectionTitle("Masalah kesehatan apa yang terlihat?")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Warga terdaftar", st.totalRegistered.toString(), Primary, Modifier.weight(1f), "admin_kpi_registered")
            Kpi("Cakupan skrining", "${st.screeningCoverage.percent}%", Wellness, Modifier.weight(1f), "admin_kpi_screening", st.screeningCoverage.label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Tindak lanjut terbuka", st.openFollowUps.toString(), RiskOrange, Modifier.weight(1f), "admin_kpi_followup")
            Kpi("Cakupan tindak lanjut", "${st.followUpCoverage.percent}%", PrimaryDark, Modifier.weight(1f), "admin_kpi_followup_cov", st.followUpCoverage.label)
        }
        SehatiCard(Modifier.staggerIn(1)) {
            Text("Distribusi hasil skrining peserta", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            BarRow("TD rentang tinggi", st.elevatedBp.numerator, maxOf(st.elevatedBp.denominator, 1), TextSecondary, valueLabel = "${st.elevatedBp.percent}%")
            BarRow("Gula di atas normal", st.elevatedGlucose.numerator, maxOf(st.elevatedGlucose.denominator, 1), TextSecondary, valueLabel = "${st.elevatedGlucose.percent}%")
            BarRow("Perokok aktif", st.smoking.numerator, maxOf(st.smoking.denominator, 1), TextSecondary, valueLabel = "${st.smoking.percent}%")
            BarRow("Indikator obesitas", st.obesityIndicator.numerator, maxOf(st.obesityIndicator.denominator, 1), TextSecondary, valueLabel = "${st.obesityIndicator.percent}%")
            BarRow("Aktivitas kurang", st.physicalInactivity.numerator, maxOf(st.physicalInactivity.denominator, 1), TextSecondary, valueLabel = "${st.physicalInactivity.percent}%")
            Text("Penyebut: TD ${st.elevatedBp.denominator}, gula ${st.elevatedGlucose.denominator}, asesmen ${st.smoking.denominator} peserta. Bukan prevalensi populasi.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        SehatiCard(Modifier.staggerIn(2)) {
            Text("Profil SEHATI warga", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            val max = maxOf(st.riskDistribution.values.maxOrNull() ?: 1, 1)
            RiskLevel.entries.forEach { lvl -> val c = lvl.style(); BarRow(lvl.label, st.riskDistribution[lvl] ?: 0, max, c.color) }
        }
        SectionTitle("Di mana masalahnya?")
        SecondaryButton("Lihat Heart Map per RW", onOpenCommunity, icon = Icons.Outlined.Map, tag = "admin_open_map")
        SectionTitle("Siapa yang perlu ditindaklanjuti?")
        s.registry.take(3).forEach { r -> RegistryCard(r, null, {}, {}, Modifier.testTag("overview_followup_${r.follow.userId}"), compact = true) }
        if (s.registry.isEmpty()) InfoNote("Tidak ada tindak lanjut terbuka.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg)
        SecondaryButton("Buka registri tindak lanjut", onOpenFollowUp, tag = "admin_open_followup")
    }
}

@Composable
private fun Kpi(label: String, value: String, color: Color, modifier: Modifier, tag: String, sub: String? = null) {
    // Warna tidak lagi mewarnai angka; angka netral & tabular agar mudah dibandingkan.
    KpiTile(label, value, modifier, sub = sub, tag = tag)
}

/** Heart Map: hanya agregat per RW. Tidak ada nama, NIK, HP, alamat, atau hasil individu. */
@Composable
fun CommunityTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val st = s.stats
    ScreenColumn(Modifier.testTag("admin_community_screen")) {
        OverlineLabel("COMMUNITY · HEART MAP")
        if (st == null) { LoadingState(); return@ScreenColumn }
        st.map.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, c ->
                    val (bg, fg, icon) = when (c.state) {
                        MapState.INSUFFICIENT -> Triple(SurfaceMuted, TextSecondary, Icons.Outlined.HelpOutline)
                        MapState.LOWER -> Triple(RiskGreenBg, RiskGreenText, Icons.Rounded.CheckCircle)
                        MapState.HIGHER -> Triple(RiskOrangeBg, RiskOrangeText, Icons.Outlined.MonitorHeart)
                        MapState.FOLLOW_UP_CONCENTRATION -> Triple(RiskRedBg, RiskRedText, Icons.Outlined.EventAvailable)
                    }
                    SehatiCard(Modifier.weight(1f).staggerIn(row * 2 + col).testTag("map_rw_${c.rw}"), contentPadding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 3.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(fg))
                            Spacer(Modifier.width(8.dp))
                            Text("RW ${c.rw}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        StatusPill(c.state.label, fg, bg, icon)
                        Text("Diskrining ${c.screened}/${c.registered}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextSecondary)
                        Text("Pantau lebih tinggi ${c.higherNeed} · tindak lanjut ${c.openFollowUps}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text("Legenda: abu = data belum cukup · hijau = kebutuhan pemantauan lebih rendah · oranye = lebih tinggi · merah = konsentrasi tindak lanjut.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        InfoNote("Heart Map hanya menampilkan data agregat. Sel dengan kurang dari ${CommunityAnalytics.MIN_CELL} peserta diskrining ditandai \"Data belum cukup\" untuk melindungi privasi dan mencegah salah tafsir.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    }
}

@Composable
fun FollowUpAdminTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val activeCadres = s.cadres.filter { it.cadre.active }
    ScreenColumn(Modifier.testTag("admin_followup_screen")) {
        OverlineLabel("FOLLOW-UP REQUIRED")
        error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        if (s.registry.isEmpty()) EmptyState(Icons.Rounded.CheckCircle, "Tidak ada tindak lanjut terbuka", "Semua tindak lanjut sudah selesai.")
        s.registry.forEachIndexed { i, r -> RegistryCard(r, activeCadres, { cid -> vm.assign(r.follow.id, cid) }, { vm.scheduleRecheck(r.follow.id, 7) }, Modifier.staggerIn(i).testTag("registry_${r.follow.userId}")) }
        InfoNote("Admin tidak mengakses detail kesehatan individu. SEHATI tidak menerbitkan diagnosis; tindak lanjut bersifat administratif dan memerlukan verifikasi tenaga kesehatan.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    }
}

@Composable
private fun RegistryCard(r: RegistryRow, cadres: List<CadreRow>?, onAssign: (String?) -> Unit, onSchedule: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    val f = r.follow
    var menu by remember { mutableStateOf(false) }
    val pr = when (f.priority) { 3 -> Triple("Prioritas tinggi", RiskRedText, RiskRedBg); 2 -> Triple("Perlu ditinjau", RiskOrangeText, RiskOrangeBg); else -> Triple("Pantau", RiskYellowText, RiskYellowBg) }
    SehatiCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.userId, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(pr.first, pr.second, pr.third)
        }
        Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text("RW ${r.rw} · ${FollowUpType.parse(f.type).label} · ${FollowUpStatus.parse(f.status).label}" + (r.lastCheck?.let { " · cek terakhir ${TimeUtils.shortDate(it)}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = TextMuted)
        r.cadreName?.let { Text("Ditugaskan: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
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
        OverlineLabel("REPORTS")
        val st = s.stats
        if (st == null) { LoadingState(); return@ScreenColumn }
        SehatiCard {
            Text("Ringkasan laporan", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(vm.reportText().orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.testTag("report_preview"))
        }
        PrimaryButton("Bagikan laporan (teks)", { share(vm.reportText(), "Laporan SEHATI") }, icon = Icons.Outlined.Share, tag = "share_report_button")
        SecondaryButton("Bagikan data per RW (CSV)", { share(vm.reportCsv(), "SEHATI per RW (CSV)") }, icon = Icons.Outlined.TableChart, tag = "share_csv_button")
        InfoNote("Laporan hanya berisi angka agregat dan terminologi \"distribusi hasil skrining\". Tidak ada nama, NIK, nomor HP, alamat, atau hasil individu.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
    }
}

@Composable
fun CadresTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<String?>(null) }
    ScreenColumn(Modifier.testTag("admin_cadres_screen")) {
        OverlineLabel("CADRES")
        error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        created?.let { InfoNote("Akun kader $it dibuat. Berikan ID dan kata sandi sementara kepada kader.", icon = Icons.Rounded.CheckCircle, color = RiskGreenText, bg = RiskGreenBg, modifier = Modifier.testTag("cadre_created_note")) }
        PrimaryButton("Tambah kader", { adding = true; vm.clearError() }, icon = Icons.Outlined.PersonAdd, tag = "add_cadre_button")
        if (s.cadres.isEmpty()) EmptyState(Icons.Outlined.Groups, "Belum ada kader", "Kader akan muncul setelah akun diprovisikan.")
        if (s.cadres.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                s.cadres.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().testTag("cadre_${c.cadre.sehatiId}").heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${c.cadre.sehatiId} · RW ${c.cadre.assignedRw}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                            if (c.cadre.active) StatusPill("Aktif", RiskGreenText, RiskGreenBg) else StatusPill("Nonaktif", TextMuted, SurfaceMuted)
                        }
                        Switch(c.cadre.active, { vm.setCadreActive(c.cadre.sehatiId, it) }, Modifier.testTag("cadre_switch_${c.cadre.sehatiId}"))
                    }
                }
            }
        }
    }
    if (adding) AddCadreDialog(vm, onDismiss = { adding = false }, onCreated = { adding = false; created = it })
}

@Composable
fun LogisticsTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("admin_logistics_screen")) {
        OverlineLabel("LOGISTICS")
        if (s.logistics.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                s.logistics.forEachIndexed { i, l ->
                    val low = l.stock < l.minStock
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().testTag("logistic_${l.id}").padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(l.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Stok ${l.stock} ${l.unit} · minimum ${l.minStock}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                            if (low) StatusPill("Stok menipis", RiskOrangeText, RiskOrangeBg, Icons.Outlined.Warning)
                        }
                        IconButton({ vm.adjustStock(l.id, -1) }, Modifier.size(48.dp).testTag("stock_minus_${l.id}")) { Icon(Icons.Outlined.Remove, "Kurangi stok ${l.name}", tint = TextSecondary) }
                        IconButton({ vm.adjustStock(l.id, 5) }, Modifier.size(48.dp).testTag("stock_plus_${l.id}")) { Icon(Icons.Outlined.Add, "Tambah stok ${l.name}", tint = TextSecondary) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(vm: AdminViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val msg by vm.syncMessage.collectAsStateWithLifecycle()
    val t = ClinicalConfig.current
    val rules = listOf(
        BloodPressureRules.interpret(120, 80).rule, GlucoseRules.interpret(100f).rule, LipidRules.interpret(150f).rule,
        AnthropometryRules.classify(170f, 65f).rule,
    )
    ScreenColumn(Modifier.testTag("admin_settings_screen")) {
        OverlineLabel("SETTINGS · ATURAN & AUDIT")
        SehatiCard {
            Text("Sinkronisasi", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Tujuan: ${s.destination}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            KeyValueRow("Menunggu", s.sync.pending.toString()); KeyValueRow("Gagal", s.sync.failed.toString())
            msg?.let { InfoNote(it, color = TextSecondary, bg = SurfaceMuted) }
            SecondaryButton("Sinkronkan sekarang", vm::syncNow, icon = Icons.Outlined.Sync, tag = "admin_sync_button")
        }
        SehatiCard {
            Text("Versi aturan klinis: ${ClinicalThresholds.VERSION}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Ambang adalah konfigurasi internal aplikasi (bukan klaim standar resmi WHO/Kemenkes). Setiap aturan berikut mencatat sumber, penjelasan, keterbatasan, dan tindakan.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            KeyValueRow("TD tinggi", "≥ ${t.bpHighSys}/${t.bpHighDia} mmHg"); KeyValueRow("TD sangat tinggi", "≥ ${t.bpUrgentSys}/${t.bpUrgentDia} mmHg")
            KeyValueRow("GDS di atas normal", "≥ ${t.gdsElevated.toInt()} mg/dL"); KeyValueRow("GDS tinggi", "≥ ${t.gdsHigh.toInt()} mg/dL")
            KeyValueRow("Kolesterol total tinggi", "≥ ${t.cholHigh.toInt()} mg/dL"); KeyValueRow("IMT obesitas I", "≥ ${t.bmiObese1}")
        }
        ThresholdEditor(vm)
        SehatiCard(contentPadding = 0) {
            Column {
                rules.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.rule, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Sumber: ${r.source}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Keterbatasan: ${r.limitations}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Text("Tindakan: ${r.action}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
        SectionTitle("Audit log")
        if (s.audit.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                s.audit.take(40).forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Text("${TimeUtils.dateTime(a.at)} · ${a.actorRole} ${a.actorId} · ${a.action}" + (a.subjectId?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
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

/** Penyesuaian ambang aturan (mis. mengikuti pedoman Puskesmas). Divalidasi, dicatat di audit, lalu semua profil dihitung ulang. */
@Composable
private fun ThresholdEditor(vm: AdminViewModel) {
    val version by vm.thresholdVersion.collectAsStateWithLifecycle()
    val msg by vm.thresholdMessage.collectAsStateWithLifecycle()
    val cur = ClinicalConfig.current
    var sys by remember(version) { mutableStateOf(cur.bpHighSys.toString()) }
    var dia by remember(version) { mutableStateOf(cur.bpHighDia.toString()) }
    var gds by remember(version) { mutableStateOf(cur.gdsElevated.toInt().toString()) }
    var chol by remember(version) { mutableStateOf(cur.cholBorderline.toInt().toString()) }
    var bmi by remember(version) { mutableStateOf(cur.bmiObese1.toString()) }
    var active by remember(version) { mutableStateOf(cur.activeMinutesPerWeekGoal.toString()) }
    var confirmReset by remember { mutableStateOf(false) }
    val num = androidx.compose.ui.text.input.KeyboardType.Number
    SehatiCard(Modifier.testTag("threshold_editor")) {
        Text("Sesuaikan ambang aturan", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(if (ClinicalConfig.isCustom) "Saat ini memakai ambang kustom." else "Saat ini memakai ambang bawaan aplikasi.", style = MaterialTheme.typography.bodySmall, color = if (ClinicalConfig.isCustom) RiskOrangeText else TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, "TD tinggi sistolik", Modifier.weight(1f), keyboardType = num, tag = "th_sys")
            SehatiTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, "TD tinggi diastolik", Modifier.weight(1f), keyboardType = num, tag = "th_dia")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(gds, { gds = it.filter(Char::isDigit).take(3) }, "GDS di atas normal", Modifier.weight(1f), keyboardType = num, tag = "th_gds")
            SehatiTextField(chol, { chol = it.filter(Char::isDigit).take(3) }, "Kolesterol batas atas", Modifier.weight(1f), keyboardType = num, tag = "th_chol")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SehatiTextField(bmi, { bmi = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, "IMT obesitas I", Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, tag = "th_bmi")
            SehatiTextField(active, { active = it.filter(Char::isDigit).take(3) }, "Menit aktif/minggu", Modifier.weight(1f), keyboardType = num, tag = "th_active")
        }
        msg?.let { InfoNote(it, Modifier.testTag("threshold_message"), color = TextSecondary, bg = SurfaceMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Simpan", {
                vm.saveThresholds(cur.copy(
                    bpHighSys = sys.toIntOrNull() ?: 0, bpHighDia = dia.toIntOrNull() ?: 0, gdsElevated = gds.toFloatOrNull() ?: 0f,
                    cholBorderline = chol.toFloatOrNull() ?: 0f, bmiObese1 = bmi.toFloatOrNull() ?: 0f, activeMinutesPerWeekGoal = active.toIntOrNull() ?: 0,
                ))
            }, Modifier.weight(1f), tag = "th_save")
            SecondaryButton("Kembalikan bawaan", { confirmReset = true }, Modifier.weight(1f), tag = "th_reset")
        }
        Text("Perubahan memengaruhi penilaian semua warga dan dicatat di audit log. Hanya sesuaikan bila ada pedoman resmi dari Puskesmas atau Dinas Kesehatan.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
    if (confirmReset) ConfirmDialog("Kembalikan ambang bawaan?", "Semua profil akan dihitung ulang dengan ambang bawaan aplikasi.", "Kembalikan", { confirmReset = false; vm.resetThresholds() }, { confirmReset = false }, tag = "th_reset_dialog")
}
