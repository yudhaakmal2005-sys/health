package id.sehati.app.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.repository.PosyanduRepository
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.content.HeartKnowledge
import id.sehati.app.domain.rules.RedFlag
import id.sehati.app.domain.rules.CoachContext
import id.sehati.app.domain.rules.HealthCoach
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean, val emergency: Boolean = false, val moduleId: String? = null, val related: List<String> = emptyList())

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val current: CurrentUser, private val daily: DailyRepository, private val health: HealthRepository,
    private val posyandu: PosyanduRepository, private val settings: SettingsStore, private val clock: Clock,
) : ViewModel() {
    private val _messages = MutableStateFlow(listOf(ChatMessage("Halo! Aku SEHATI. Tanyakan tentang jantung koroner, tanda bahaya, garam, rokok, olahraga, atau kebiasaan sehat.\n\n${HeartKnowledge.DISCLAIMER}", false)))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /** Konteks terbatas: profil, pengukuran terbaru, aktivitas, tidur, kebiasaan, target hari ini. Diproses di perangkat. */
    private suspend fun context(): CoachContext {
        val u = current.user.first()!!
        val iso = TimeUtils.dateIso(clock.now())
        val s = settings.current()
        val h = daily.observeHabit(u.sehatiId, iso).first()
        val sm = daily.observeSmoking(u.sehatiId, iso).first()
        val sleep = daily.observeSleep(u.sehatiId, iso).first()
        val checks = health.checks(u.sehatiId)
        val level = health.levelOf(health.observeProfile(u.sehatiId).first())
        val fus = posyandu.observeFollowUpsOf(u.sehatiId).first()
        return CoachContext(u.fullName.substringBefore(' '), h?.steps ?: 0, s.targets.steps, h?.waterGlasses ?: 0, s.targets.waterGlasses,
            sleep.maxOfOrNull { it.minutes }?.div(60f), sm?.cigarettes ?: 0, checks.firstOrNull()?.bloodPressure, level.label, fus.any { it.status == "OPEN" })
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || _thinking.value) return
        _messages.update { it + ChatMessage(q, true) }
        _thinking.value = true
        viewModelScope.launch {
            val faq = HeartKnowledge.answer(q)
            val emergency = RedFlag.detect(q) || HeartKnowledge.isEmergencyText(q)
            val msg = when {
                emergency -> ChatMessage(RedFlag.EMERGENCY_MESSAGE, false, emergency = true)
                faq.entry != null -> ChatMessage("${faq.entry.answer}\n\n${HeartKnowledge.DISCLAIMER}", false, faq.entry.emergency, faq.entry.moduleId, faq.related.map { it.question })
                else -> HealthCoach.reply(q, context()).let { ChatMessage(it.text, false, it.emergency) }
            }
            if (!msg.emergency) delay(350)
            _messages.update { it + msg }
            _thinking.value = false
        }
    }
}

@Composable
fun CoachScreen(onBack: () -> Unit, onOpenAcademy: (String) -> Unit = {}, vm: CoachViewModel = hiltViewModel()) {
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val thinking by vm.thinking.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size, thinking) { listState.animateScrollToItem((msgs.size).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize().imePadding().testTag("coach_screen")) {
        Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader("Tanya SEHATI", "Jawaban singkat seputar jantung · bukan diagnosis", onBack = onBack) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(msgs) { m ->
                if (m.emergency) EmergencyBanner()
                else Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = if (m.fromUser) Primary else CardWhite, border = if (m.fromUser) null else androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.widthIn(max = 320.dp)) {
                        Text(m.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = if (m.fromUser) androidx.compose.ui.graphics.Color.White else TextPrimary)
                    }
                    m.moduleId?.let { id -> Academy.byId(id)?.let { mod ->
                        AssistChip({ onOpenAcademy(id) }, { Text("Pelajari: ${mod.title}") }, Modifier.heightIn(min = 48.dp).testTag("coach_module_$id"))
                    } }
                    m.related.forEach { q -> SuggestionChip({ vm.send(q) }, { Text(q) }, Modifier.heightIn(min = 48.dp)) }
                }
            }
            if (thinking) item { Text("Pelatih sedang mengetik…", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.shimmer()) }
        }
        androidx.compose.foundation.lazy.LazyRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HeartKnowledge.suggestedQuestions) { c ->
                AssistChip({ vm.send(c) }, { Text(c) }, Modifier.heightIn(min = 48.dp).testTag("coach_chip_$c"))
            }
        }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f).testTag("coach_input"), placeholder = { Text("Tanyakan tentang jantung…") }, shape = RoundedCornerShape(16.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }))
            FilledIconButton({ vm.send(input); input = "" }, Modifier.size(52.dp).testTag("coach_send_button")) { Icon(Icons.Rounded.Send, "Kirim") }
        }
    }
}
