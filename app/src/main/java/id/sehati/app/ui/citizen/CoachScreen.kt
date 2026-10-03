package id.sehati.app.ui.citizen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.remote.ChatContext
import id.sehati.app.data.remote.ChatEvent
import id.sehati.app.data.remote.ChatRequest
import id.sehati.app.data.remote.ChatTurn
import id.sehati.app.data.remote.ServerClient
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.repository.PosyanduRepository
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.content.HeartKnowledge
import id.sehati.app.domain.rules.AgeCalc
import id.sehati.app.domain.rules.CoachContext
import id.sehati.app.domain.rules.FactorStatus
import id.sehati.app.domain.rules.HealthCoach
import id.sehati.app.domain.rules.RedFlag
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AnswerSource { AI, OFFLINE }

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val emergency: Boolean = false,
    val moduleId: String? = null,
    val related: List<String> = emptyList(),
    val source: AnswerSource = AnswerSource.OFFLINE,
    val streaming: Boolean = false,
    val note: String? = null,
)

data class AskUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val aiReady: Boolean = false,
    val needsConsent: Boolean = false,
    val shareContext: Boolean = false,
)

/**
 * Tanya SEHATI: jawaban AI lewat server SEHATI (kunci AI hanya di server), dengan pemeriksaan gejala darurat
 * di perangkat lebih dulu, dan jawaban offline dari basis pengetahuan bila server/AI tidak tersedia.
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val current: CurrentUser, private val daily: DailyRepository, private val health: HealthRepository,
    private val posyandu: PosyanduRepository, private val settings: SettingsStore, private val server: ServerClient,
    private val clock: Clock,
) : ViewModel() {
    private var seq = 0L
    private val welcome = ChatMessage(seq++, "Halo! Aku **SEHATI**. Tanyakan apa saja seputar jantung koroner, tekanan darah, garam, rokok, olahraga, atau kebiasaan sehat.\n\n_${HeartKnowledge.DISCLAIMER}_", false)
    private val _s = MutableStateFlow(AskUiState(messages = listOf(welcome)))
    val state: StateFlow<AskUiState> = _s.asStateFlow()
    private var job: Job? = null
    private var pendingQuestion: String? = null

    init { viewModelScope.launch { refreshAvailability() } }

    private suspend fun refreshAvailability() {
        val s = settings.current()
        _s.update { it.copy(aiReady = server.isConfigured() && server.token() != null && s.aiConsent, shareContext = s.aiShareContext) }
    }

    fun send(text: String) {
        val q = text.trim().take(1000)
        if (q.isEmpty() || _s.value.busy) return
        viewModelScope.launch {
            val st = settings.current()
            val aiPossible = server.isConfigured() && server.token() != null
            if (aiPossible && !st.aiConsent && pendingQuestion == null) {
                pendingQuestion = q
                _s.update { it.copy(needsConsent = true) }
                return@launch
            }
            ask(q)
        }
    }

    fun answerConsent(useAi: Boolean, shareContext: Boolean) {
        val q = pendingQuestion
        pendingQuestion = null
        _s.update { it.copy(needsConsent = false) }
        viewModelScope.launch {
            settings.setAiConsent(useAi, shareContext && useAi)
            refreshAvailability()
            if (q != null) ask(q, forceOffline = !useAi)
        }
    }

    fun setShareContext(v: Boolean) { viewModelScope.launch { settings.setAiConsent(settings.current().aiConsent, v); refreshAvailability() } }

    private fun ask(q: String, forceOffline: Boolean = false) {
        _s.update { it.copy(messages = it.messages + ChatMessage(seq++, q, true), busy = true) }
        val faq = HeartKnowledge.answer(q)
        val general = faq.entry != null && HeartKnowledge.isGeneralQuestion(q)
        // Darurat diperiksa di perangkat dulu: jangan menunggu jaringan.
        if (!general && (RedFlag.detect(q) || HeartKnowledge.isEmergencyText(q))) {
            _s.update { it.copy(messages = it.messages + ChatMessage(seq++, RedFlag.EMERGENCY_MESSAGE, false, emergency = true), busy = false) }
            return
        }
        job = viewModelScope.launch {
            val st = settings.current()
            val useAi = !forceOffline && st.aiConsent && server.isConfigured() && server.token() != null
            if (useAi) streamAi(q, st.aiShareContext) else answerOffline(q, note = null)
        }
    }

    private suspend fun streamAi(q: String, share: Boolean) {
        val id = seq++
        _s.update { it.copy(messages = it.messages + ChatMessage(id, "", false, source = AnswerSource.AI, streaming = true)) }
        val history = _s.value.messages.filter { !it.emergency && it.id != welcome.id && it.id != id && it.text.isNotBlank() }
            .takeLast(12).map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text.take(1000)) }
        val req = ChatRequest(history, if (share) context() else null)
        var failure: ChatEvent.Failure? = null
        server.chat(req).collect { e ->
            when (e) {
                is ChatEvent.Meta -> if (e.emergency) update(id) { it.copy(emergency = true) }
                is ChatEvent.Delta -> update(id) { it.copy(text = it.text + e.text) }
                is ChatEvent.Done -> update(id) { it.copy(streaming = false) }
                is ChatEvent.Failure -> failure = e
            }
        }
        val f = failure
        val msg = _s.value.messages.firstOrNull { it.id == id }
        if (f != null && (msg == null || msg.text.isBlank())) {
            _s.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
            answerOffline(q, note = offlineNote(f))
        } else {
            update(id) { it.copy(streaming = false, note = f?.let { "Jawaban terputus: ${it.message}" }) }
            _s.update { it.copy(busy = false) }
        }
        if (f?.code == "NO_TOKEN" || f?.code == "HTTP_401") refreshAvailability()
    }

    private fun offlineNote(f: ChatEvent.Failure) = when (f.code) {
        "AI_DISABLED" -> "AI belum diaktifkan di server. Jawaban dari pustaka offline SEHATI."
        "AI_DAILY_LIMIT" -> "Batas pertanyaan AI hari ini tercapai. Jawaban dari pustaka offline."
        "OFFLINE" -> "Sedang offline. Jawaban dari pustaka offline SEHATI."
        else -> "AI tidak tersedia (${f.message.ifBlank { f.code }}). Jawaban dari pustaka offline."
    }

    private suspend fun answerOffline(q: String, note: String?) {
        val faq = HeartKnowledge.answer(q)
        delay(250)
        val msg = if (faq.entry != null) {
            ChatMessage(seq++, faq.entry.answer, false, moduleId = faq.entry.moduleId, related = faq.related.map { it.question }, note = note)
        } else {
            val r = HealthCoach.reply(q, coachContext())
            ChatMessage(seq++, r.text, false, emergency = r.emergency, related = HeartKnowledge.suggestedQuestions.take(3), note = note)
        }
        _s.update { it.copy(messages = it.messages + msg, busy = false) }
    }

    fun stop() {
        job?.cancel()
        _s.update { s -> s.copy(busy = false, messages = s.messages.map { if (it.streaming) it.copy(streaming = false, note = "Dihentikan.") else it }) }
    }

    fun reset() { stop(); _s.update { it.copy(messages = listOf(welcome)) } }

    private fun update(id: Long, f: (ChatMessage) -> ChatMessage) = _s.update { s -> s.copy(messages = s.messages.map { if (it.id == id) f(it) else it }) }

    /** Konteks tanpa identitas: kelompok usia, jenis kelamin, faktor risiko yang ada, langkah hari ini. */
    private suspend fun context(): ChatContext? {
        val u = current.user.first() ?: return null
        val age = AgeCalc.age(u.birthDate)
        val band = if (age <= 0) null else "${age / 10 * 10}-${age / 10 * 10 + 9}"
        val factors = runCatching { health.heartRisk(u.sehatiId).factors.filter { it.status == FactorStatus.PRESENT }.map { it.id } }.getOrDefault(emptyList())
        val steps = daily.observeHabit(u.sehatiId, TimeUtils.dateIso(clock.now())).first()?.steps
        return ChatContext(band, u.sex, factors, steps)
    }

    private suspend fun coachContext(): CoachContext {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(onBack: () -> Unit, onOpenAcademy: (String) -> Unit = {}, onEmergency: () -> Unit = {}, vm: CoachViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val last = s.messages.lastOrNull()
    LaunchedEffect(s.messages.size, last?.text?.length) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1) }
    fun submit() { vm.send(input); input = "" }

    Column(Modifier.fillMaxSize().imePadding().testTag("coach_screen")) {
        Box(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader("Tanya SEHATI", if (s.aiReady) "Asisten AI · jawaban umum, bukan diagnosis" else "Pustaka offline · bukan diagnosis", onBack = onBack) {
                StatusPill(if (s.aiReady) "AI" else "Offline", TextSecondary, SurfaceMuted,
                    if (s.aiReady) Icons.Outlined.AutoAwesome else Icons.Outlined.CloudOff, Modifier.testTag("coach_mode_pill"))
                IconButton(vm::reset, Modifier.size(48.dp)) { Icon(Icons.Outlined.RestartAlt, "Mulai percakapan baru", tint = TextMuted) }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(s.messages, key = { it.id }) { m -> Box(Modifier.animateItem().staggerIn(0)) { MessageBubble(m, onOpenAcademy, onEmergency, onAsk = vm::send) } }
        }
        if (!s.busy) {
            LazyRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HeartKnowledge.suggestedQuestions) { c ->
                    AssistChip({ vm.send(c) }, { Text(c, color = TextSecondary) }, Modifier.heightIn(min = 48.dp).testTag("coach_chip_$c"),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Hairline), colors = AssistChipDefaults.assistChipColors(containerColor = CardWhite))
                }
            }
        }
        HorizontalDivider(color = Hairline, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it.take(1000) }, Modifier.weight(1f).testTag("coach_input"), placeholder = { Text("Tanyakan tentang jantung…", color = TextMuted) }, shape = RoundedCornerShape(14.dp),
                maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TextSecondary, unfocusedBorderColor = Hairline, cursorColor = Primary, focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite))
            if (s.busy) FilledTonalIconButton(vm::stop, Modifier.size(52.dp).testTag("coach_stop_button"), shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceMuted, contentColor = TextPrimary)) { Icon(Icons.Outlined.Stop, "Hentikan jawaban") }
            else FilledIconButton(::submit, Modifier.size(52.dp).testTag("coach_send_button"), enabled = input.isNotBlank(), shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary, contentColor = Color.White, disabledContainerColor = SurfaceMuted, disabledContentColor = TextMuted)) { Icon(Icons.AutoMirrored.Outlined.Send, "Kirim") }
        }
        Text("Jangan menulis nama, NIK, atau nomor HP. Untuk keadaan darurat hubungi 119/112.", style = MaterialTheme.typography.labelSmall, color = TextMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
    }

    if (s.needsConsent) AiConsentDialog(onAnswer = vm::answerConsent)
}

@Composable
private fun AiConsentDialog(onAnswer: (Boolean, Boolean) -> Unit) {
    var share by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(false, false) }, containerColor = CardWhite, modifier = Modifier.testTag("ai_consent_dialog"),
        icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = Primary) },
        title = { Text("Gunakan asisten AI?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pertanyaanmu dikirim lewat server SEHATI ke layanan AI untuk dijawab. Server tidak menyimpan isi percakapan. Jangan menulis identitas (nama, NIK, nomor HP).", style = MaterialTheme.typography.bodyMedium)
                Text("Jawaban AI adalah edukasi umum, bukan diagnosis. Kamu bisa berhenti kapan saja dan tetap memakai pustaka offline.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(share, { share = it }, Modifier.testTag("ai_share_context"))
                    Text("Sertakan ringkasan faktor risiko & kelompok usia (tanpa identitas) agar saran lebih sesuai.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton({ onAnswer(true, share) }, Modifier.heightIn(min = 48.dp).testTag("ai_consent_accept")) { Text("Gunakan AI") } },
        dismissButton = { TextButton({ onAnswer(false, false) }, Modifier.heightIn(min = 48.dp).testTag("ai_consent_decline")) { Text("Tetap offline") } },
    )
}

@Composable
private fun MessageBubble(m: ChatMessage, onOpenAcademy: (String) -> Unit, onEmergency: () -> Unit, onAsk: (String) -> Unit) {
    val ctx = LocalContext.current
    if (m.emergency) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmergencyBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Telepon 119", { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119"))) }, Modifier.weight(1f), icon = Icons.Outlined.Call, tag = "coach_call_119")
                SecondaryButton("Panduan darurat", onEmergency, Modifier.weight(1f), icon = Icons.Outlined.MedicalServices, tag = "coach_open_emergency")
            }
            if (m.text.isNotBlank() && m.text != RedFlag.EMERGENCY_MESSAGE) BubbleSurface(m)
        }
        return
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!m.fromUser) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (m.source == AnswerSource.AI) Icons.Outlined.AutoAwesome else Icons.Outlined.MenuBook, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (m.source == AnswerSource.AI) "SEHATI AI" else "SEHATI", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        BubbleSurface(m)
        m.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
        m.moduleId?.let { id -> Academy.byId(id)?.let { mod ->
            AssistChip({ onOpenAcademy(id) }, { Text("Pelajari: ${mod.title}", color = TextPrimary) }, Modifier.heightIn(min = 48.dp).testTag("coach_module_$id"), leadingIcon = { Icon(Icons.Outlined.School, null, Modifier.size(18.dp), tint = TextMuted) },
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Hairline), colors = AssistChipDefaults.assistChipColors(containerColor = CardWhite))
        } }
        m.related.forEach { q -> SuggestionChip({ onAsk(q) }, { Text(q, color = TextSecondary) }, Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Hairline)) }
    }
}

@Composable
private fun BubbleSurface(m: ChatMessage) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (m.fromUser) Primary else CardWhite,
        border = if (m.fromUser) null else BorderStroke(1.dp, Hairline), modifier = Modifier.widthIn(max = 330.dp).animateContentSize()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            if (m.text.isEmpty() && m.streaming) TypingDots()
            else Text(
                if (m.fromUser) AnnotatedString(m.text) else markdownLite(m.text + if (m.streaming) " ▍" else ""),
                style = MaterialTheme.typography.bodyMedium, color = if (m.fromUser) Color.White else TextPrimary,
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).testTag("coach_typing")) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Surface(Modifier.size(8.dp).alpha(a), shape = RoundedCornerShape(50), color = TextMuted) {}
        }
    }
}

/** Markdown ringan: **tebal**, _miring_, daftar "- " / "* " / "1. ". Cukup untuk jawaban singkat tanpa pustaka tambahan. */
fun markdownLite(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.replace("\r", "").split('\n')
    lines.forEachIndexed { i, raw ->
        var line = raw
        val bullet = Regex("^\\s*[-*•]\\s+").find(line)
        val numbered = Regex("^\\s*(\\d+)[.)]\\s+").find(line)
        when {
            bullet != null -> { append("•  "); line = line.substring(bullet.range.last + 1) }
            numbered != null -> { append("${numbered.groupValues[1]}.  "); line = line.substring(numbered.range.last + 1) }
            line.startsWith("#") -> { line = line.trimStart('#', ' '); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(line) }; if (i < lines.lastIndex) append('\n'); return@forEachIndexed }
        }
        appendInline(line)
        if (i < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    val re = Regex("\\*\\*(.+?)\\*\\*|_(.+?)_")
    var last = 0
    for (m in re.findAll(text)) {
        append(text.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        else withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(m.groups[2]?.value.orEmpty()) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
