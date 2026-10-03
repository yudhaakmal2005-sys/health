package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.NumberFmt
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.rules.BloodPressureRules
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*

@Composable
fun HomeScreen(
    onOpenHealth: () -> Unit, onOpenMove: () -> Unit, onOpenFood: () -> Unit,
    onOpenAcademy: (String?) -> Unit, onOpenCoach: () -> Unit,
    onOpenRisk: () -> Unit = {}, onOpenChallenges: () -> Unit = {},
    onEmergency: () -> Unit = {}, onOpenMeds: () -> Unit = {}, onBreath: () -> Unit = {}, onReminders: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading || s.user == null) {
        ScreenColumn(Modifier.testTag("home_screen")) { SkeletonBlock(110.dp); SkeletonBlock(160.dp); SkeletonBlock(120.dp) }
        return
    }
    ScreenColumn(Modifier.testTag("home_screen")) {
        // 1) "Bagaimana kondisi saya hari ini?" — ringkasan dulu
        HomeHero(s.greeting, s.user!!.fullName.substringBefore(' '), onEmergency, onOpenCoach, onOpenMeds, onBreath, onReminders, Modifier.staggerIn(0))
        if (s.openFollowUps > 0) {
            InfoNote("Ada ${s.openFollowUps} tindak lanjut kesehatan. Hubungi kader Posyandu atau datang ke Puskesmas.", Modifier.staggerIn(1).testTag("home_followup_note"), icon = Icons.Outlined.EventAvailable, color = RiskOrangeText, bg = RiskOrangeBg)
        }
        HeartHomeSection(onOpenRisk, onOpenChallenges)

        // 2) Angka penting — kisi 2 kolom
        SectionTitle("Hari ini", action = "Kesehatan", onAction = onOpenHealth)
        val c = s.latestCheck
        Row(Modifier.fillMaxWidth().staggerIn(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val bp = c?.bloodPressure
            if (bp != null) {
                val r = BloodPressureRules.interpret(bp.first, bp.second)
                val st = r.severity.style()
                SehatiCard(Modifier.weight(1f).testTag("home_bp_tile"), onClick = onOpenHealth, contentPadding = 14) {
                    Text("Tekanan darah", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${bp.first}/${bp.second}", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(" mmHg", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    StatusPill(r.category, st.text, st.bg)
                }
            } else {
                MetricTile(Icons.Outlined.MonitorHeart, "Tekanan darah", "–", "belum ada data", Primary, PrimaryLight, null, Modifier.weight(1f), onClick = onOpenHealth, tag = "home_bp_tile")
            }
            SehatiCard(Modifier.weight(1f).testTag("home_steps_tile"), onClick = onOpenMove, contentPadding = 14) {
                Text("Langkah", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                AnimatedNumber(s.steps, MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), format = { NumberFmt.thousands(it) })
                Text("dari ${NumberFmt.thousands(s.targets.steps)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                LinearBar(s.steps.toFloat() / s.targets.steps, Primary)
            }
        }
        Row(Modifier.fillMaxWidth().staggerIn(4), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(Icons.Outlined.WaterDrop, "Air putih", "${s.water} / ${s.targets.waterGlasses}", "gelas", TextPrimary, SurfaceMuted, s.water.toFloat() / s.targets.waterGlasses, Modifier.weight(1f), tag = "home_water_tile")
            MetricTile(Icons.Outlined.Bedtime, "Tidur", if (s.sleepMinutes > 0) TimeUtils.durationLabel(s.sleepMinutes) else "–", "target ${s.targets.sleepHours.toInt()} jam", TextPrimary, SurfaceMuted, (s.sleepMinutes / 60f) / s.targets.sleepHours, Modifier.weight(1f), tag = "home_sleep_tile")
        }
        Row(Modifier.fillMaxWidth().staggerIn(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(Icons.Outlined.Restaurant, "Makanan", if (s.foodCount == 0) "–" else "${s.foodQuality}/100", if (s.foodCount == 0) "belum ada catatan" else "${NumberFmt.thousands(s.kcal)} kkal", WellnessDark, WellnessLight, if (s.foodCount == 0) null else s.foodQuality / 100f, Modifier.weight(1f), onClick = onOpenFood, tag = "home_food_tile")
            MetricTile(Icons.Outlined.SmokeFree, "Rokok", "${s.cigarettes}", "batang hari ini", RiskOrange, RiskOrangeBg, null, Modifier.weight(1f), tag = "home_smoking_tile")
        }
        Row(Modifier.fillMaxWidth().staggerIn(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("+ Air", { vm.addWater(1) }, Modifier.weight(1f), tag = "add_water_button")
            SecondaryButton("− Air", { vm.addWater(-1) }, Modifier.weight(1f), tag = "remove_water_button")
            SecondaryButton("+ Rokok", { vm.setCigarettes(1) }, Modifier.weight(1f), tag = "add_cigarette_button")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Aktif ${TimeUtils.durationLabel(s.activeMinutes)} · sumber ${DataSource.parse(s.stepsSource).label}" + if (s.passiveMinutes > 0) " · transportasi tidak dihitung" else "", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
            TextButton2("Mulai bergerak", onOpenMove, "start_activity_button")
        }

        // 3) Agenda & rencana
        UpcomingCards(onOpenMeds)
        if (s.plan.isNotEmpty()) {
            SectionTitle("Rencana hari ini")
            SehatiCard(Modifier.staggerIn(6), contentPadding = 6) {
                s.plan.forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p.id in s.donePlan, { vm.togglePlan(p.id) }, Modifier.testTag("plan_${p.id}"), colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = TextMuted))
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, color = if (p.id in s.donePlan) TextMuted else TextPrimary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        RiskCard(s.level, Modifier.staggerIn(7), onClick = onOpenHealth, compact = true)

        if (c != null) {
            SectionTitle("Pemeriksaan terakhir", action = "Riwayat", onAction = onOpenHealth)
            SehatiCard(Modifier.staggerIn(7), onClick = onOpenHealth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(TimeUtils.dateTime(c.measuredAt), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(listOfNotNull(c.bloodPressure?.let { "${it.first}/${it.second} mmHg" }, c.weightKg?.let { "$it kg" }, c.glucose?.let { "GDS ${it.toInt()} mg/dL" }).joinToString(" · ").ifBlank { "Pengukuran tercatat" },
                            style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.source.label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(c.verification.label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        } else {
            SehatiCard(Modifier.staggerIn(7), onClick = onOpenHealth) {
                Text("Belum ada hasil pemeriksaan", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("Catat sendiri atau datang ke Posyandu dengan QR SEHATI-mu.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        // 4) Tanya SEHATI — baris masukan yang tenang
        SehatiCard(Modifier.staggerIn(8).testTag("home_coach_card"), onClick = onOpenCoach) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tanya SEHATI", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(s.tip, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.testTag("home_coach_tip"))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).background(TextPrimary, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (s.modules.isNotEmpty()) {
            SectionTitle("Bacaan untukmu", action = "Semua", onAction = { onOpenAcademy(null) })
            SehatiCard(Modifier.staggerIn(9), contentPadding = 0) {
                Column {
                    s.modules.forEachIndexed { i, m ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                        AgendaRow(Icons.Outlined.MenuBook, m.title, "${m.category} · ${m.minutes} menit baca", null, onClick = { onOpenAcademy(m.id) })
                    }
                }
            }
        }

        // Darurat tetap terlihat tanpa berteriak
        Surface(onClick = onEmergency, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = CardWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth().testTag("home_sos_row")) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, tint = PrimaryDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nyeri dada atau sesak berat?", style = MaterialTheme.typography.labelLarge, color = PrimaryDark, modifier = Modifier.weight(1f))
                Text("Telepon 119", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LinearBar(progress: Float, color: Color) {
    val p by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), motionTween(Motion.Long), label = "bar")
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(SurfaceMuted, androidx.compose.foundation.shape.CircleShape)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(color, androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable
private fun TextButton2(text: String, onClick: () -> Unit, tag: String) {
    androidx.compose.material3.TextButton(onClick, Modifier.heightIn(min = 48.dp).testTag(tag), contentPadding = PaddingValues(0.dp)) {
        Text(text, color = PrimaryDark, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Outlined.ChevronRight, null, tint = PrimaryDark)
    }
}
