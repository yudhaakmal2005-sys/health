package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.local.EducationProgressEntity
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.content.EducationModule
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AcademyViewModel @Inject constructor(private val current: CurrentUser, private val daily: DailyRepository) : ViewModel() {
    val progress: StateFlow<Map<String, EducationProgressEntity>> = current.user.filterNotNull()
        .flatMapLatest { daily.observeEducation(it.sehatiId) }.map { l -> l.associateBy { it.moduleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun markRead(moduleId: String) { val id = current.id ?: return; viewModelScope.launch { daily.markRead(id, moduleId) } }
    fun saveQuiz(moduleId: String, score: Int, total: Int) { val id = current.id ?: return; viewModelScope.launch { daily.saveQuiz(id, moduleId, score, total) } }
}

@Composable
fun AcademyScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: AcademyViewModel = hiltViewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<String?>(null) }
    val modules = remember(category) { if (category == null) Academy.modules else Academy.byCategory(category!!) }
    ScreenColumn(Modifier.testTag("academy_screen")) {
        ScreenHeader("Health Academy", "Belajar 2 menit · kuis · aksi kecil", onBack = onBack)
        val done = progress.values.count { it.completedAt != null }
        SehatiCard(container = PrimaryLight, border = Primary.copy(alpha = 0.2f)) {
            Text("$done dari ${Academy.modules.size} materi tuntas · ${progress.values.count { it.readAt != null } * 2 + done * 10} poin belajar", style = MaterialTheme.typography.titleSmall, color = PrimaryDark)
        }
        ChoiceChips(listOf<String?>(null) + Academy.categories, category, { category = it }, { it ?: "Semua" }, tagPrefix = "acadcat")
        modules.forEachIndexed { i, m ->
            val p = progress[m.id]
            SehatiCard(Modifier.staggerIn(i).testTag("module_${m.id}"), onClick = { onOpen(m.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (p?.completedAt != null) Icons.Rounded.CheckCircle else Icons.Rounded.School, if (p?.completedAt != null) RiskGreenText else PrimaryDark, if (p?.completedAt != null) RiskGreenBg else PrimaryLight, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(m.summary, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Text("${m.category} · ${m.minutes} menit" + (p?.quizScore?.let { " · skor $it/${p.quizTotal}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = PrimaryDark)
                    }
                }
            }
        }
    }
}

@Composable
fun AcademyDetailScreen(moduleId: String, onBack: () -> Unit, vm: AcademyViewModel = hiltViewModel()) {
    val m = Academy.byId(moduleId)
    if (m == null) { ScreenColumn { ScreenHeader("Materi", onBack = onBack); ErrorState("Materi tidak ditemukan.") }; return }
    LaunchedEffect(moduleId) { vm.markRead(moduleId) }
    var quiz by remember { mutableStateOf(false) }
    if (quiz) QuizScreen(m, onClose = { quiz = false }, onFinish = { score -> vm.saveQuiz(m.id, score, m.quiz.size) })
    else ScreenColumn(Modifier.testTag("academy_detail_screen")) {
        ScreenHeader(m.title, "${m.category} · ${m.minutes} menit baca", onBack = onBack)
        m.paragraphs.forEachIndexed { i, p -> Text(p, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.staggerIn(i)) }
        SehatiCard(container = WellnessLight, border = Wellness.copy(alpha = 0.3f)) {
            Text("Aksi kecil", style = MaterialTheme.typography.labelLarge, color = WellnessDark)
            Text(m.challenge, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        InfoNote("Materi edukasi umum, bukan pengganti saran tenaga kesehatan.", icon = Icons.Rounded.Shield)
        PrimaryButton("Mulai kuis (${m.quiz.size} soal)", { quiz = true }, icon = Icons.Rounded.Quiz, tag = "start_quiz_button")
    }
}

@Composable
private fun QuizScreen(m: EducationModule, onClose: () -> Unit, onFinish: (Int) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var finished by remember { mutableStateOf(false) }
    ScreenColumn(Modifier.testTag("quiz_screen")) {
        ScreenHeader("Kuis: ${m.title}", onBack = onClose)
        if (finished) {
            AnimatedCheck(Modifier.align(Alignment.CenterHorizontally))
            Text("Skor $score dari ${m.quiz.size}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("quiz_score"))
            Text(if (score * 100 >= m.quiz.size * 60) "Materi tuntas. Selamat!" else "Baca lagi materinya, lalu coba lagi.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            InfoNote("Aksi hari ini: ${m.challenge}", icon = Icons.Rounded.Flag)
            PrimaryButton("Selesai", onClose, tag = "quiz_done_button")
        } else {
            val q = m.quiz[index]
            StepIndicator(index, m.quiz.size)
            Text(q.question, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            q.options.forEachIndexed { i, o ->
                val chosen = picked
                val correct = chosen != null && i == q.correctIndex
                val wrong = chosen == i && i != q.correctIndex
                SehatiCard(Modifier.testTag("quiz_option_$i"), onClick = if (chosen == null) ({ picked = i; if (i == q.correctIndex) score++ }) else null,
                    container = if (correct) RiskGreenBg else if (wrong) RiskRedBg else CardWhite, border = if (correct) RiskGreen else if (wrong) RiskRed else BorderColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                        if (correct) Icon(Icons.Rounded.CheckCircle, "Benar", tint = RiskGreenText)
                        if (wrong) Icon(Icons.Rounded.Cancel, "Salah", tint = RiskRedText)
                    }
                }
            }
            if (picked != null) {
                InfoNote(q.explanation, icon = Icons.Rounded.Lightbulb)
                PrimaryButton(if (index == m.quiz.lastIndex) "Lihat skor" else "Soal berikutnya", {
                    if (index == m.quiz.lastIndex) { finished = true; onFinish(score) } else { index++; picked = null }
                }, tag = "quiz_next_button")
            }
        }
    }
}
