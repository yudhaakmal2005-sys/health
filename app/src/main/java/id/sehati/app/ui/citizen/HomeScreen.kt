package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
        // Pertanyaan 1: "Bagaimana kondisi saya hari ini?"
        HomeHero(s.greeting, s.user!!.fullName.substringBefore(' '), onEmergency, onOpenCoach, onOpenMeds, onBreath, onReminders, Modifier.staggerIn(0))
        UpcomingCards(onOpenMeds)
        RiskCard(s.level, Modifier.staggerIn(1), onClick = onOpenHealth, compact = true)
        if (s.openFollowUps > 0) {
            InfoNote("Ada ${s.openFollowUps} tindak lanjut kesehatan. Hubungi kader Posyandu atau datang ke Puskesmas.", Modifier.staggerIn(2).testTag("home_followup_note"), icon = Icons.Rounded.EventAvailable, color = RiskOrangeText, bg = RiskOrangeBg)
        }

        HeartHomeSection(onOpenRisk, onOpenChallenges)

        // Pertanyaan 2: "Apa yang perlu saya lakukan?"
        SectionTitle("Target aktivitas hari ini")
        SehatiCard(Modifier.staggerIn(3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(s.steps.toFloat() / s.targets.steps, size = 120.dp, modifier = Modifier.semantics { contentDescription = "Langkah ${s.steps} dari ${s.targets.steps}" }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedNumber(s.steps, MaterialTheme.typography.headlineSmall, format = { NumberFmt.thousands(it) })
                        Text("langkah", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${NumberFmt.thousands(s.steps)} / ${NumberFmt.thousands(s.targets.steps)} langkah", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Sumber: ${DataSource.parse(s.stepsSource).label}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("Aktif ${TimeUtils.durationLabel(s.activeMinutes)}" + if (s.passiveMinutes > 0) " · transportasi ${TimeUtils.durationLabel(s.passiveMinutes)} (tidak dihitung)" else "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    TextButton2("Mulai bergerak", onOpenMove, "start_activity_button")
                }
            }
            Text("Target dapat kamu ubah di Profil. Angka ini bukan patokan universal.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        Row(Modifier.fillMaxWidth().staggerIn(4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.WaterDrop, "Air", "${s.water} / ${s.targets.waterGlasses}", "gelas", Primary, PrimaryLight, s.water.toFloat() / s.targets.waterGlasses, Modifier.weight(1f), tag = "home_water_tile")
            MetricTile(Icons.Rounded.Bedtime, "Tidur", if (s.sleepMinutes > 0) TimeUtils.durationLabel(s.sleepMinutes) else "–", "target ${s.targets.sleepHours.toInt()} jam", Color(0xFF6366F1), Color(0xFFE0E7FF), (s.sleepMinutes / 60f) / s.targets.sleepHours, Modifier.weight(1f), tag = "home_sleep_tile")
        }
        Row(Modifier.fillMaxWidth().staggerIn(5), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.Restaurant, "Makanan", if (s.foodCount == 0) "–" else "${s.foodQuality}/100", if (s.foodCount == 0) "belum ada catatan" else "${NumberFmt.thousands(s.kcal)} kkal", Wellness, WellnessLight, if (s.foodCount == 0) null else s.foodQuality / 100f, Modifier.weight(1f), onClick = onOpenFood, tag = "home_food_tile")
            MetricTile(Icons.Rounded.SmokeFree, "Rokok hari ini", "${s.cigarettes}", "batang", RiskOrange, RiskOrangeBg, null, Modifier.weight(1f), tag = "home_smoking_tile")
        }
        Row(Modifier.fillMaxWidth().staggerIn(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("+ Air", { vm.addWater(1) }, Modifier.weight(1f), tag = "add_water_button")
            SecondaryButton("− Air", { vm.addWater(-1) }, Modifier.weight(1f), tag = "remove_water_button")
            SecondaryButton("+ Rokok", { vm.setCigarettes(1) }, Modifier.weight(1f), tag = "add_cigarette_button")
        }

        if (s.plan.isNotEmpty()) {
            SectionTitle("Rencana hari ini")
            SehatiCard(Modifier.staggerIn(6)) {
                s.plan.forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p.id in s.donePlan, { vm.togglePlan(p.id) }, Modifier.testTag("plan_${p.id}"), colors = CheckboxDefaults.colors(checkedColor = Wellness))
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Pertanyaan 3: "Apa yang perlu saya perhatikan?"
        SectionTitle("Pemeriksaan terakhir", action = "Riwayat", onAction = onOpenHealth)
        val c = s.latestCheck
        if (c == null) {
            EmptyState(Icons.Rounded.MonitorHeart, "Belum ada hasil pemeriksaan", "Catat sendiri atau datang ke Posyandu dengan QR SEHATI-mu.", Modifier.staggerIn(7))
        } else {
            SehatiCard(Modifier.staggerIn(7), onClick = onOpenHealth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(TimeUtils.dateTime(c.measuredAt), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        c.bloodPressure?.let { (sy, di) ->
                            val r = BloodPressureRules.interpret(sy, di)
                            Text("$sy/$di mmHg", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            val st = r.severity.style()
                            StatusPill(r.category, st.text, st.bg, st.icon)
                        } ?: Text(listOfNotNull(c.weightKg?.let { "$it kg" }, c.glucose?.let { "GDS ${it.toInt()} mg/dL" }).joinToString(" · ").ifBlank { "Pengukuran tercatat" }, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.source.label, style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                        Text(c.verification.label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }

        SectionTitle("Tanya SEHATI")
        SehatiCard(Modifier.staggerIn(8).testTag("home_coach_card"), onClick = onOpenCoach, container = PrimaryLight, border = Primary.copy(alpha = 0.25f)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Rounded.AutoAwesome, Color.White, Primary, 40)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.tip, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.testTag("home_coach_tip"))
                    Text("Ketuk untuk bertanya soal jantung · bukan diagnosis", style = MaterialTheme.typography.bodySmall, color = PrimaryDark)
                }
            }
        }

        if (s.modules.isNotEmpty()) {
            SectionTitle("Belajar untukmu", action = "Semua", onAction = { onOpenAcademy(null) })
            s.modules.forEachIndexed { i, m ->
                SehatiCard(Modifier.staggerIn(9 + i), onClick = { onOpenAcademy(m.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.School, PrimaryDark, PrimaryLight, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${m.category} · ${m.minutes} menit baca", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TextButton2(text: String, onClick: () -> Unit, tag: String) {
    androidx.compose.material3.TextButton(onClick, Modifier.heightIn(min = 48.dp).testTag(tag), contentPadding = PaddingValues(0.dp)) {
        Text(text, color = PrimaryDark, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Rounded.ChevronRight, null, tint = PrimaryDark)
    }
}
