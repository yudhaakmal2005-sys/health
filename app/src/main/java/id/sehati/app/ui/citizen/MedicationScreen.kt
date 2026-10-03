package id.sehati.app.ui.citizen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.local.MedicationEntity
import id.sehati.app.data.reminders.ReminderScheduler
import id.sehati.app.data.repository.Dose
import id.sehati.app.data.repository.MedicationRepository
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MedUiState(
    val loading: Boolean = true,
    val meds: List<MedicationEntity> = emptyList(),
    val today: List<Dose> = emptyList(),
    val adherence: Int? = null,
    val week: List<Pair<LocalDate, Float?>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val current: CurrentUser,
    private val repo: MedicationRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {
    val state: StateFlow<MedUiState> = current.user.filterNotNull().flatMapLatest { u ->
        val today = LocalDate.now()
        combine(repo.observe(u.sehatiId), repo.observeLogs(u.sehatiId, today.minusDays(6))) { meds, logs ->
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val week = (6 downTo 0).map { d ->
                val date = today.minusDays(d.toLong())
                val doses = MedicationRepository.doses(meds, logs, date).filter { date != today || it.time <= now }
                date to if (doses.isEmpty()) null else doses.count { it.taken }.toFloat() / doses.size
            }
            MedUiState(false, meds, MedicationRepository.doses(meds, logs, today), MedicationRepository.adherence(meds, logs, today, now), week)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MedUiState())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggle(d: Dose) { viewModelScope.launch { repo.setTaken(d.medication.id, LocalDate.now(), d.time, !d.taken) } }
    fun save(id: String?, name: String, instructions: String, times: List<String>, onDone: () -> Unit) {
        val uid = current.id ?: return
        viewModelScope.launch {
            runCatching { repo.save(uid, name, instructions, times, id) }
                .onSuccess { _error.value = null; reminders.rescheduleAll(); onDone() }
                .onFailure { _error.value = it.message }
        }
    }
    fun setActive(id: String, active: Boolean) { viewModelScope.launch { repo.setActive(id, active); reminders.rescheduleAll() } }
    fun clearError() { _error.value = null }
}

@Composable
fun MedicationScreen(onBack: () -> Unit, onOpenReminders: () -> Unit, vm: MedicationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MedicationEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    ScreenColumn(Modifier.testTag("medication_screen")) {
        ScreenHeader("Obat saya", "Pengingat minum obat sesuai resep", onBack = onBack)
        if (s.loading) { SkeletonBlock(120.dp); return@ScreenColumn }
        if (s.meds.none { it.active }) {
            EmptyState(Icons.Rounded.Medication, "Belum ada obat", "Tambahkan obat yang diresepkan dokter (misalnya obat tekanan darah) agar SEHATI mengingatkan jam minumnya.",
                action = "Tambah obat", onAction = { adding = true })
        } else {
            SehatiCard(Modifier.staggerIn(0).testTag("med_today_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val done = s.today.count { it.taken }
                    ProgressRing(if (s.today.isEmpty()) 0f else done.toFloat() / s.today.size, size = 72.dp, stroke = 8.dp, color = Wellness, track = WellnessLight) {
                        Text("$done/${s.today.size}", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Jadwal hari ini", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(s.adherence?.let { "Kepatuhan 7 hari: $it%" } ?: "Kepatuhan akan tampil setelah jadwal pertama", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                s.today.forEach { d ->
                    val bg by animateColorAsState(if (d.taken) WellnessLight else SurfaceMuted, label = "dose")
                    Surface(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggle(d) }, color = bg, shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("dose_${d.medication.id}_${d.time}")) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.time, style = MaterialTheme.typography.titleMedium, color = PrimaryDark, modifier = Modifier.width(58.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.medication.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Medium,
                                    textDecoration = if (d.taken) TextDecoration.LineThrough else null)
                                if (d.medication.instructions.isNotBlank()) Text(d.medication.instructions, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Icon(if (d.taken) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (d.taken) "Sudah diminum" else "Belum diminum",
                                tint = if (d.taken) Wellness else TextMuted)
                        }
                    }
                }
            }
            SehatiCard(Modifier.staggerIn(1)) {
                Text("7 hari terakhir", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    s.week.forEach { (date, frac) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProgressRing(frac ?: 0f, size = 34.dp, stroke = 5.dp, color = if ((frac ?: 0f) >= 1f) Wellness else RiskYellow, track = BorderColor)
                            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("id")), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
            SectionTitle("Daftar obat", action = "Tambah", onAction = { adding = true })
            s.meds.forEach { m ->
                SehatiCard(onClick = { editing = m }, modifier = Modifier.testTag("med_${m.id}"), container = if (m.active) CardWhite else SurfaceMuted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Medication, PrimaryDark, PrimaryLight, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(MedicationRepository.times(m).joinToString(" · ") + if (!m.active) " · dihentikan" else "", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Switch(m.active, { vm.setActive(m.id, it) }, Modifier.testTag("med_active_${m.id}"))
                    }
                }
            }
        }
        SecondaryButton("Atur pengingat", onOpenReminders, icon = Icons.Rounded.NotificationsActive, tag = "open_reminders")
        InfoNote("SEHATI hanya mengingatkan. Jangan menambah, mengurangi, atau menghentikan obat tanpa arahan dokter atau apoteker.", icon = Icons.Rounded.Info)
    }

    if (adding || editing != null) MedicationDialog(editing, err, onDismiss = { adding = false; editing = null; vm.clearError() }) { name, ins, times ->
        vm.save(editing?.id, name, ins, times) { adding = false; editing = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MedicationDialog(m: MedicationEntity?, error: String?, onDismiss: () -> Unit, onSave: (String, String, List<String>) -> Unit) {
    var name by remember { mutableStateOf(m?.name.orEmpty()) }
    var ins by remember { mutableStateOf(m?.instructions.orEmpty()) }
    var times by remember { mutableStateOf(m?.let(MedicationRepository::times) ?: listOf("07:00")) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardWhite, modifier = Modifier.testTag("med_dialog"),
        title = { Text(if (m == null) "Tambah obat" else "Ubah obat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SehatiTextField(name, { name = it.take(60) }, "Nama obat (sesuai resep)", tag = "med_name")
                SehatiTextField(ins, { ins = it.take(120) }, "Aturan pakai", tag = "med_instructions", supporting = "Contoh: 1 tablet sesudah makan pagi")
                FieldLabel("Jam minum")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEach { t -> InputChip(true, { times = times - t }, { Text(t) }, trailingIcon = { Icon(Icons.Rounded.Close, "Hapus $t", Modifier.size(16.dp)) }) }
                    AssistChip({ picking = true }, { Text("Tambah jam") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, modifier = Modifier.testTag("med_add_time"))
                }
                error?.let { InfoNote(it, color = RiskRedText, bg = RiskRedBg, icon = Icons.Rounded.ErrorOutline) }
            }
        },
        confirmButton = { TextButton({ onSave(name, ins, times) }, Modifier.heightIn(min = 48.dp).testTag("med_save")) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Batal") } },
    )
    if (picking) TimePickDialog("08:00", onDismiss = { picking = false }) { t -> times = (times + t).distinct().sorted(); picking = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(':')
    val state = rememberTimePickerState(parts.getOrNull(0)?.toIntOrNull() ?: 8, parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardWhite,
        text = { TimePicker(state) },
        confirmButton = { TextButton({ onPick("%02d:%02d".format(state.hour, state.minute)) }, Modifier.heightIn(min = 48.dp).testTag("time_pick_ok")) { Text("Pilih") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Batal") } },
    )
}
