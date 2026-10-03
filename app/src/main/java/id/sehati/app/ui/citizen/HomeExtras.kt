package id.sehati.app.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.remote.PosyanduSlot
import id.sehati.app.data.remote.RemoteAccount
import id.sehati.app.data.repository.MedicationRepository
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class HomeExtrasState(val nextPosyandu: PosyanduSlot? = null, val dosesLeft: Int = 0, val nextDose: String? = null, val hasMeds: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeExtrasViewModel @Inject constructor(current: CurrentUser, remote: RemoteAccount, meds: MedicationRepository) : ViewModel() {
    val state: StateFlow<HomeExtrasState> = current.user.filterNotNull().flatMapLatest { u ->
        val today = LocalDate.now()
        combine(remote.config, meds.observe(u.sehatiId), meds.observeLogs(u.sehatiId, today)) { cfg, list, logs ->
            val next = cfg?.posyandu.orEmpty().filter { it.rw == u.rw || u.rw.isBlank() }
                .filter { runCatching { !LocalDate.parse(it.date).isBefore(today) }.getOrDefault(false) }.minByOrNull { it.date }
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val doses = MedicationRepository.doses(list, logs, today).filter { !it.taken }
            HomeExtrasState(next, doses.size, doses.firstOrNull { it.time >= now }?.let { "${it.time} · ${it.medication.name}" } ?: doses.firstOrNull()?.let { "${it.time} · ${it.medication.name}" }, list.any { it.active })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeExtrasState())
}

/** Kepala beranda: sapaan, tanggal, tombol darurat, dan aksi cepat. */
@Composable
fun HomeHero(
    greeting: String, name: String, onEmergency: () -> Unit, onAsk: () -> Unit, onMeds: () -> Unit, onBreath: () -> Unit, onReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id"))) }
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.background(heroBrush()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("$greeting,", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("home_greeting_name"))
                    Text(date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Surface(onClick = onReminders, shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(44.dp).testTag("home_reminders_button")) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.NotificationsActive, "Pengingat", tint = Color.White) }
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = onEmergency, shape = RoundedCornerShape(22.dp), color = RiskRed, modifier = Modifier.height(44.dp).testTag("home_sos_button")) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sos, null, tint = Color.White); Spacer(Modifier.width(4.dp))
                        Text("Darurat", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Rounded.AutoAwesome, "Tanya", onAsk, "qa_ask")
                QuickAction(Icons.Rounded.Medication, "Obat", onMeds, "qa_meds")
                QuickAction(Icons.Rounded.SelfImprovement, "Napas", onBreath, "qa_breath")
                QuickAction(Icons.Rounded.MedicalServices, "P3K jantung", onEmergency, "qa_emergency")
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, tag: String) {
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).pressScale(src)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick).padding(vertical = 4.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = PrimaryDark) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/** Kartu agenda: Posyandu berikutnya di RW warga dan obat yang belum diminum hari ini. */
@Composable
fun UpcomingCards(onOpenMeds: () -> Unit, vm: HomeExtrasViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    s.nextPosyandu?.let { p ->
        val date = runCatching { LocalDate.parse(p.date) }.getOrNull()
        val days = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
        SehatiCard(Modifier.staggerIn(2).testTag("home_posyandu_card"), container = WellnessLight, border = Wellness.copy(alpha = 0.3f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.size(56.dp).background(Color.White, RoundedCornerShape(14.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(date?.format(DateTimeFormatter.ofPattern("MMM", Locale("id")))?.uppercase().orEmpty(), style = MaterialTheme.typography.labelSmall, color = WellnessDark)
                    Text(date?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (days) { 0 -> "Posyandu hari ini"; 1 -> "Posyandu besok"; null -> "Posyandu"; else -> "Posyandu $days hari lagi" }, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(listOf(p.location, listOf(p.startTime, p.endTime).filter { it.isNotBlank() }.joinToString("–")).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Cek tensi, gula darah & berat badan. Bawa QR SEHATI.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
    }
    if (s.hasMeds) {
        SehatiCard(Modifier.staggerIn(2).testTag("home_meds_card"), onClick = onOpenMeds) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Medication, if (s.dosesLeft == 0) WellnessDark else PrimaryDark, if (s.dosesLeft == 0) WellnessLight else PrimaryLight, 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.dosesLeft == 0) "Semua obat hari ini sudah diminum" else "${s.dosesLeft} jadwal obat belum diminum", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    s.nextDose?.let { Text("Berikutnya: $it", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
            }
        }
    }
}
