package id.sehati.app.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
        SehatiCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(" dari ${Academy.modules.size} materi tuntas", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = TextMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text("${progress.values.count { it.readAt != null } * 2 + done * 10} poin belajar", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextSecondary)
        }
        ChoiceChips(listOf<String?>(null) + Academy.categories, category, { category = it }, { it ?: "Semua" }, tagPrefix = "acadcat")
        if (modules.isNotEmpty()) SehatiCard(Modifier.staggerIn(1), contentPadding = 0) {
            Column {
                modules.forEachIndexed { i, m ->
                    val p = progress[m.id]
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().testTag("module_${m.id}").clickable { onOpen(m.id) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (p?.completedAt != null) Icons.Rounded.CheckCircle else Icons.Outlined.School, null, tint = if (p?.completedAt != null) Wellness else TextMuted, modifier = Modifier.size(20.dp).padding(top = 1.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(m.summary, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("${m.category} · ${m.minutes} menit" + (p?.quizScore?.let { " · skor $it/${p.quizTotal}" } ?: ""), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
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
        SehatiCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(width = 3.dp, height = 40.dp).background(Wellness, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AKSI KECIL", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.2.sp)
                    Text(m.challenge, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
        InfoNote("Materi edukasi umum, bukan pengganti saran tenaga kesehatan.", icon = Icons.Outlined.Shield, color = TextSecondary, bg = SurfaceMuted)
        PrimaryButton("Mulai kuis (${m.quiz.size} soal)", { quiz = true }, icon = Icons.Outlined.Quiz, tag = "start_quiz_button")
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
            Icon(Icons.Outlined.TaskAlt, null, tint = Wellness, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
            Text("Skor $score dari ${m.quiz.size}", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("quiz_score"))
            Text(if (score * 100 >= m.quiz.size * 60) "Materi tuntas. Selamat!" else "Baca lagi materinya, lalu coba lagi.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            InfoNote("Aksi hari ini: ${m.challenge}", icon = Icons.Outlined.Flag, color = TextSecondary, bg = SurfaceMuted)
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
                    border = if (correct) RiskGreen else if (wrong) RiskRed else BorderColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                        if (correct) Icon(Icons.Rounded.CheckCircle, "Benar", tint = RiskGreenText)
                        if (wrong) Icon(Icons.Outlined.Cancel, "Salah", tint = RiskRedText)
                    }
                }
            }
            if (picked != null) {
                InfoNote(q.explanation, icon = Icons.Outlined.Lightbulb, color = TextSecondary, bg = SurfaceMuted)
                PrimaryButton(if (index == m.quiz.lastIndex) "Lihat skor" else "Soal berikutnya", {
                    if (index == m.quiz.lastIndex) { finished = true; onFinish(score) } else { index++; picked = null }
                }, tag = "quiz_next_button")
            }
        }
    }
}
