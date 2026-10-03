package id.sehati.app.ui.kader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.repository.CitizenSummary
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.model.SyncStatus
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

/** Hari Posyandu: register layanan hari ini dengan filter. */
@Composable
fun TodayTab(vm: KaderViewModel, onOpenVisit: (String) -> Unit, onStartRegistration: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenColumn(Modifier.testTag("kader_today_screen")) {
        OverlineLabel("POSYANDU HARI INI")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Dilayani", s.allRows.count { it.completed }.toString(), Wellness, Modifier.weight(1f))
            StatBox("Berlangsung", s.allRows.count { !it.completed }.toString(), TextMuted, Modifier.weight(1f))
            StatBox("Perhatian", s.allRows.count { it.needsAttention }.toString(), RiskOrange, Modifier.weight(1f))
        }
        PrimaryButton("Daftarkan warga (QR / SEHATI ID)", onStartRegistration, icon = Icons.Outlined.QrCodeScanner, tag = "scan_qr_button")
        ChoiceChips(RegisterFilter.entries, s.filter, vm::setFilter, { it.label }, tagPrefix = "filter")
        if (s.rows.isEmpty()) EmptyState(Icons.Outlined.Groups, "Belum ada warga pada filter ini", "Daftarkan warga dengan QR atau SEHATI ID.")
        if (s.rows.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                s.rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenVisit(r.visit.id) }.testTag("register_row_${r.visit.userId}")
                            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(TimeUtils.time(r.visit.registeredAt), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = TextMuted, modifier = Modifier.width(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.visit.userId, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary)
                            Text(r.name, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                r.hasFollowUp -> StatusPill("Follow-up", RiskOrangeText, RiskOrangeBg)
                                r.completed -> StatusPill("Selesai", RiskGreenText, RiskGreenBg)
                                else -> StatusPill("Berlangsung", TextSecondary, SurfaceMuted)
                            }
                            if (r.needsAttention) StatusPill("Perlu perhatian", RiskRedText, RiskRedBg, Icons.Outlined.Warning)
                            SyncChip(r.sync)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    SehatiCard(modifier, contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape)); Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
        }
        AnimatedNumber(value.toInt(), MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), TextPrimary)
    }
}

/** Langkah 1 — Pendaftaran: scan QR atau cari SEHATI ID; hanya ringkasan minimal yang ditampilkan. */
@Composable
fun CitizensTab(vm: KaderViewModel, onVisit: (String) -> Unit, startWithScanner: Boolean) {
    val results by vm.results.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val scanMsg by vm.scanMessage.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(startWithScanner) }
    var registering by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<id.sehati.app.data.local.UserEntity?>(null) }

    ScreenColumn(Modifier.testTag("kader_citizens_screen")) {
        OverlineLabel("LANGKAH 1 · PENDAFTARAN")
        if (scanning) {
            QrScannerPanel(onResult = { vm.onScanned(it); scanning = false })
            SecondaryButton("Tutup pemindai", { scanning = false })
        } else {
            SecondaryButton("Pindai QR warga", { vm.clearScan(); scanning = true }, icon = Icons.Outlined.QrCodeScanner, tag = "open_scanner_button")
        }
        SehatiTextField(query, { query = it; vm.onQuery(it) }, "Cari SEHATI ID atau nama", tag = "citizen_search_field")
        scanMsg?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg) }
        error?.let { InfoNote(it, icon = Icons.Outlined.ErrorOutline, color = RiskRedText, bg = RiskRedBg, modifier = Modifier.testTag("kader_error")) }
        scanned?.let { CitizenCard(it, vm, onVisit, Modifier.testTag("scanned_citizen")) }
        results.filter { it.sehatiId != scanned?.sehatiId }.forEachIndexed { i, c -> CitizenCard(c, vm, onVisit, Modifier.staggerIn(i)) }
        if (query.isNotBlank() && results.isEmpty()) EmptyState(Icons.Outlined.PersonSearch, "Warga tidak ditemukan", "Periksa SEHATI ID, atau daftarkan warga baru.")
        TextButton({ registering = true }, Modifier.heightIn(min = 48.dp).testTag("register_new_citizen_button")) { Icon(Icons.Outlined.PersonAdd, null, tint = PrimaryDark); Spacer(Modifier.width(8.dp)); Text("Daftarkan warga baru", color = PrimaryDark) }
    }
    if (registering) NewCitizenDialog(vm, onDismiss = { registering = false; vm.resetNew() }, onCreated = { u -> registering = false; query = u.sehatiId; vm.onQuery(u.sehatiId); created = u })
    created?.let { u -> CitizenIdCardDialog(u) { created = null } }
}

/** Kartu SEHATI untuk warga baru: ID, QR, dan cara mengaktifkan akun di HP warga sendiri. */
@Composable
private fun CitizenIdCardDialog(u: id.sehati.app.data.local.UserEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardWhite, modifier = Modifier.testTag("citizen_card_dialog"),
        title = { Text("Kartu SEHATI warga") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(u.fullName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(u.sehatiId, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("citizen_card_id"))
                id.sehati.app.ui.components.QrImage(id.sehati.app.domain.rules.QrPayload.build(u.sehatiId, u.qrToken), size = 180.dp)
                InfoNote(
                    "Sampaikan ke warga: unduh SEHATI → Masuk → \"Sudah didaftarkan kader?\" → isi SEHATI ID dan tanggal lahir → buat kata sandi. " +
                        "Hasil pemeriksaan di Posyandu akan muncul di HP warga." + if (!u.consentServerSync) " (Perlu persetujuan sinkronisasi server.)" else "",
                    icon = Icons.Outlined.PhoneAndroid, color = TextSecondary, bg = SurfaceMuted,
                )
                Text("Foto layar ini atau tulis SEHATI ID di buku KMS/kartu warga.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        },
        confirmButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp).testTag("citizen_card_close")) { Text("Selesai") } },
    )
}

@Composable
private fun CitizenCard(c: CitizenSummary, vm: KaderViewModel, onVisit: (String) -> Unit, modifier: Modifier = Modifier) {
    SehatiCard(modifier.testTag("citizen_${c.sehatiId}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${c.sehatiId} · ${c.age} th · RW ${c.rw}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
            }
            if (c.followUpOpen) StatusPill("Tindak lanjut", RiskOrangeText, RiskOrangeBg)
        }
        HorizontalDivider(color = SurfaceMuted)
        KeyValueRow("Pemeriksaan terakhir", c.lastCheckAt?.let { TimeUtils.date(it) } ?: "Belum ada")
        KeyValueRow("Status tindak lanjut", if (c.followUpOpen) "Perlu tindak lanjut" else "Tidak ada", valueColor = if (c.followUpOpen) RiskOrangeText else TextPrimary)
        PrimaryButton("Daftarkan kunjungan hari ini", { vm.registerVisit(c.sehatiId, onVisit) }, icon = Icons.Outlined.HowToReg, tag = "register_visit_${c.sehatiId}")
        Text("Riwayat kesehatan hanya terbuka selama kunjungan aktif dan dicatat dalam audit.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun NewCitizenDialog(vm: KaderViewModel, onDismiss: () -> Unit, onCreated: (id.sehati.app.data.local.UserEntity) -> Unit) {
    val f by vm.newForm.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.testTag("new_citizen_dialog"), containerColor = CardWhite,
        title = { Text("Warga baru") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                run {
                    SehatiTextField(f.name, { v -> vm.updateNew { copy(name = v) } }, "Nama lengkap", tag = "new_name")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SehatiTextField(f.day, { v -> vm.updateNew { copy(day = v.filter(Char::isDigit).take(2)) } }, "Tgl", Modifier.weight(1f), KeyboardType.Number, tag = "new_day")
                        SehatiTextField(f.month, { v -> vm.updateNew { copy(month = v.filter(Char::isDigit).take(2)) } }, "Bln", Modifier.weight(1f), KeyboardType.Number, tag = "new_month")
                        SehatiTextField(f.year, { v -> vm.updateNew { copy(year = v.filter(Char::isDigit).take(4)) } }, "Tahun", Modifier.weight(1.4f), KeyboardType.Number, tag = "new_year")
                    }
                    ChoiceChips(Sex.entries, f.sex, { v -> vm.updateNew { copy(sex = v) } }, { it.label }, tagPrefix = "newsex")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SehatiTextField(f.rw, { v -> vm.updateNew { copy(rw = v.filter(Char::isDigit).take(2)) } }, "RW", Modifier.weight(1f), KeyboardType.Number, tag = "new_rw")
                        SehatiTextField(f.rt, { v -> vm.updateNew { copy(rt = v.filter(Char::isDigit).take(2)) } }, "RT", Modifier.weight(1f), KeyboardType.Number, tag = "new_rt")
                    }
                    SwitchRow("Warga menyetujui pencatatan data", "Persetujuan lisan/tertulis sudah diperoleh.", f.consent, { v -> vm.updateNew { copy(consent = v) } }, "new_consent")
                    SwitchRow("Izinkan sinkronisasi ke server", null, f.consentServer, { v -> vm.updateNew { copy(consentServer = v) } }, "new_consent_server")
                    f.error?.let { Text(it, color = RiskRedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("new_error")) }
                }
            }
        },
        confirmButton = { TextButton({ vm.registerCitizen { onCreated(it) } }, enabled = !f.saving, modifier = Modifier.heightIn(min = 48.dp).testTag("new_save_button")) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Batal") } },
    )
}
