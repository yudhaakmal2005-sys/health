package id.sehati.app.ui.citizen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.prefs.ReminderPrefs
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.reminders.Notifier
import id.sehati.app.data.reminders.ReminderScheduler
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val prefs: StateFlow<ReminderPrefs?> = settings.settings.map { it.reminders }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun update(f: (ReminderPrefs) -> ReminderPrefs) {
        val cur = prefs.value ?: return
        viewModelScope.launch { settings.setReminders(f(cur)); scheduler.rescheduleAll() }
    }
}

private enum class PickTarget { WALK, BP, SLEEP, FACT, CHALLENGE, WATER_ADD }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit, onOpenMedications: () -> Unit, vm: ReminderSettingsViewModel = hiltViewModel()) {
    val p = vm.prefs.collectAsStateWithLifecycle().value
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(Notifier.canPost(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var pick by remember { mutableStateOf<PickTarget?>(null) }

    ScreenColumn(Modifier.testTag("reminder_settings_screen")) {
        ScreenHeader("Pengingat", "Atur jam pengingat kebiasaan sehat", onBack = onBack)
        if (!allowed && Build.VERSION.SDK_INT >= 33) {
            SehatiCard(container = RiskYellowBg, border = RiskYellow.copy(alpha = 0.4f)) {
                Text("Notifikasi belum diizinkan", style = MaterialTheme.typography.titleSmall, color = RiskYellowText)
                Text("Izinkan notifikasi agar SEHATI bisa mengingatkan minum obat, minum air, dan jadwal Posyandu.", style = MaterialTheme.typography.bodySmall, color = RiskYellowText)
                PrimaryButton("Izinkan notifikasi", { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, icon = Icons.Outlined.NotificationsActive, tag = "allow_notifications")
            }
        }
        if (p == null) { SkeletonBlock(200.dp); return@ScreenColumn }

        ReminderRow(Icons.Outlined.Medication, "Minum obat", "Sesuai jam di daftar Obat saya", p.medication, { v -> vm.update { it.copy(medication = v) } }, "rem_med") {
            TextButton(onOpenMedications, Modifier.heightIn(min = 48.dp)) { Text("Atur obat") }
        }
        ReminderRow(Icons.Outlined.WaterDrop, "Minum air", "Tidak muncul bila target air tercapai", p.water, { v -> vm.update { it.copy(water = v) } }, "rem_water") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.waterTimes.forEach { t ->
                    InputChip(true, { vm.update { it.copy(waterTimes = it.waterTimes - t) } }, { Text(t) }, trailingIcon = { Icon(Icons.Outlined.Close, "Hapus $t", Modifier.size(16.dp)) })
                }
                if (p.waterTimes.size < 8) AssistChip({ pick = PickTarget.WATER_ADD }, { Text("Tambah jam") }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) })
            }
        }
        ReminderRow(Icons.Outlined.DirectionsWalk, "Jalan sehat", "Tidak muncul bila target langkah tercapai", p.walk, { v -> vm.update { it.copy(walk = v) } }, "rem_walk") {
            TimeButton(p.walkTime) { pick = PickTarget.WALK }
        }
        ReminderRow(Icons.Outlined.MonitorHeart, "Cek tekanan darah", "Seminggu sekali", p.bpCheck, { v -> vm.update { it.copy(bpCheck = v) } }, "rem_bp") {
            ChoiceChips((1..7).toList(), p.bpDay, { d -> vm.update { it.copy(bpDay = d) } }, { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale("id")) }, tagPrefix = "bpday")
            TimeButton(p.bpTime) { pick = PickTarget.BP }
        }
        ReminderRow(Icons.Outlined.EventAvailable, "Jadwal Posyandu", "Sehari sebelum dan pagi hari Posyandu di RW-mu", p.posyandu, { v -> vm.update { it.copy(posyandu = v) } }, "rem_posyandu")
        ReminderRow(Icons.Outlined.EmojiEvents, "Tantangan harian", "Hanya bila ada tantangan yang belum dicatat", p.challenge, { v -> vm.update { it.copy(challenge = v) } }, "rem_challenge") {
            TimeButton(p.challengeTime) { pick = PickTarget.CHALLENGE }
        }
        ReminderRow(Icons.Outlined.Lightbulb, "Fakta atau mitos harian", null, p.fact, { v -> vm.update { it.copy(fact = v) } }, "rem_fact") {
            TimeButton(p.factTime) { pick = PickTarget.FACT }
        }
        ReminderRow(Icons.Outlined.Bedtime, "Bersiap tidur", null, p.sleep, { v -> vm.update { it.copy(sleep = v) } }, "rem_sleep") {
            TimeButton(p.sleepTime) { pick = PickTarget.SLEEP }
        }
        InfoNote("Pengingat dijadwalkan di perangkat ini dan tetap berjalan tanpa internet. Isi notifikasi tidak menampilkan hasil pemeriksaanmu.", icon = Icons.Outlined.Lock)
    }

    pick?.let { target ->
        val initial = when (target) {
            PickTarget.WALK -> p?.walkTime; PickTarget.BP -> p?.bpTime; PickTarget.SLEEP -> p?.sleepTime
            PickTarget.FACT -> p?.factTime; PickTarget.CHALLENGE -> p?.challengeTime; PickTarget.WATER_ADD -> "12:00"
        } ?: "08:00"
        TimePickDialog(initial, onDismiss = { pick = null }) { t ->
            vm.update {
                when (target) {
                    PickTarget.WALK -> it.copy(walkTime = t); PickTarget.BP -> it.copy(bpTime = t); PickTarget.SLEEP -> it.copy(sleepTime = t)
                    PickTarget.FACT -> it.copy(factTime = t); PickTarget.CHALLENGE -> it.copy(challengeTime = t)
                    PickTarget.WATER_ADD -> it.copy(waterTimes = (it.waterTimes + t).distinct().sorted())
                }
            }
            pick = null
        }
    }
}

@Composable
private fun TimeButton(time: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pukul $time") }
}

@Composable
private fun ReminderRow(
    icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, tag: String,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SehatiCard(Modifier.testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (checked) PrimaryDark else TextMuted, if (checked) PrimaryLight else SurfaceMuted, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
            Switch(checked, onChange, Modifier.testTag("${tag}_switch"))
        }
        if (checked && extra != null) extra()
    }
}
