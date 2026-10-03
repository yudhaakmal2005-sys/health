package id.sehati.app.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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

/** Kepala beranda: tanggal, sapaan, pengingat, tombol darurat yang tenang, dan aksi cepat bergaris tipis. */
@Composable
fun HomeHero(
    greeting: String, name: String, onEmergency: () -> Unit, onAsk: () -> Unit, onMeds: () -> Unit, onBreath: () -> Unit, onReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id"))) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row {
                    Text("$greeting, ", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("home_greeting_name"))
                }
            }
            Surface(onClick = onReminders, shape = CircleShape, color = CardWhite, border = androidx.compose.foundation.BorderStroke(1.dp, Hairline), modifier = Modifier.size(44.dp).testTag("home_reminders_button")) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Notifications, "Pengingat", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = onEmergency, shape = RoundedCornerShape(22.dp), color = CardWhite, border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.35f)), modifier = Modifier.height(44.dp).testTag("home_sos_button")) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Call, null, tint = Primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Darurat", color = Primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Outlined.ChatBubbleOutline, "Tanya", onAsk, "qa_ask", Modifier.weight(1f))
            QuickAction(Icons.Outlined.Medication, "Obat", onMeds, "qa_meds", Modifier.weight(1f))
            QuickAction(Icons.Outlined.Air, "Napas", onBreath, "qa_breath", Modifier.weight(1f))
            QuickAction(Icons.Outlined.MedicalServices, "P3K", onEmergency, "qa_emergency", Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(14.dp)).pressScale(src, 0.97f)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(color = TextMuted), role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
    }
}

/** Satu baris agenda: ikon garis dalam kotak netral, judul + keterangan, dan nilai ringkas di kanan. */
@Composable
fun AgendaRow(icon: ImageVector, title: String, sub: String?, trailing: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailingColor: Color = TextSecondary) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceMuted), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = trailingColor, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Agenda: Posyandu berikutnya di RW warga dan obat yang belum diminum hari ini, dalam satu kartu berdaftar. */
@Composable
fun UpcomingCards(onOpenMeds: () -> Unit, vm: HomeExtrasViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.nextPosyandu == null && !s.hasMeds) return
    SectionTitle("Agenda")
    SehatiCard(Modifier.staggerIn(2), contentPadding = 0) {
        Column {
            s.nextPosyandu?.let { p ->
                val date = runCatching { LocalDate.parse(p.date) }.getOrNull()
                val days = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
                AgendaRow(
                    Icons.Outlined.CalendarMonth,
                    when (days) { 0 -> "Posyandu hari ini"; 1 -> "Posyandu besok"; null -> "Posyandu"; else -> "Posyandu $days hari lagi" },
                    listOf(p.location, listOf(p.startTime, p.endTime).filter { it.isNotBlank() }.joinToString("–")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Cek tensi, gula darah & berat badan" },
                    date?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("id"))),
                    Modifier.testTag("home_posyandu_card"),
                )
            }
            if (s.nextPosyandu != null && s.hasMeds) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
            if (s.hasMeds) {
                AgendaRow(
                    Icons.Outlined.Medication,
                    if (s.dosesLeft == 0) "Obat hari ini sudah diminum" else "${s.dosesLeft} jadwal obat belum diminum",
                    s.nextDose?.let { "Berikutnya: $it" },
                    if (s.dosesLeft == 0) "Selesai" else null,
                    Modifier.testTag("home_meds_card"), onClick = onOpenMeds,
                    trailingColor = WellnessDark,
                )
            }
        }
    }
}
